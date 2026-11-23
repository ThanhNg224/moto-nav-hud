package dev.motonavhud.core

/**
 * Parser v0 cho notification dẫn đường của Google Maps: heuristic văn bản Việt + Anh.
 * Chuỗi thật của Maps ở Việt Nam CHƯA được kiểm chứng; các bảng dưới đây là nơi thêm luật sau khi
 * có mẫu thật (xem tính năng Capture). Không bao giờ ném exception.
 *
 * Quy tắc chính:
 * - Maneuver lấy từ chỉ dẫn TỨC THỜI (trước "then"/"rồi"/"sau đó") của trường chính (title/text);
 *   bigText chỉ là dự phòng khi trường chính không nhận ra.
 * - Chỉ dẫn mơ hồ (vừa trái vừa phải trong cùng mệnh đề) -> m=0.
 * - "Đã đến"/"have arrived" mới là k=3; "arrive at ..." (sắp đến) vẫn là k=1.
 * - Khoảng cách không hợp lệ/âm/tràn -> d=-1, không bao giờ là 0.
 * - Không nhận ra gì -> [NavState.IDLE] (k=0).
 */
object MapsNotificationParser {

    private class ManeuverRule(val pattern: Regex, val maneuver: Int, val exitGroup: Boolean = false, val directional: Boolean = false)

    private const val L = "(?:trái|left)"
    private const val R = "(?:phải|right)"
    private const val GAP = "[^,.;]{0,24}?"
    private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    // Thứ tự quan trọng: luật đứng trước thắng.
    private val maneuverRules = listOf(
        ManeuverRule(rx("vòng xuyến|vòng xoay|bùng binh|roundabout|traffic circle|\\brotary\\b"), NavState.M_ROUNDABOUT, exitGroup = true),
        ManeuverRule(rx("quay đầu|đảo chiều|u-?turn"), NavState.M_UTURN),
        ManeuverRule(rx("giữ$GAP$L|keep$GAP$L|stay$GAP$L"), NavState.M_KEEP_LEFT, directional = true),
        ManeuverRule(rx("giữ$GAP$R|keep$GAP$R|stay$GAP$R"), NavState.M_KEEP_RIGHT, directional = true),
        ManeuverRule(rx("(?:gắt|sharp)$GAP$L"), NavState.M_SHARP_LEFT, directional = true),
        ManeuverRule(rx("(?:gắt|sharp)$GAP$R"), NavState.M_SHARP_RIGHT, directional = true),
        ManeuverRule(rx("(?:chếch|nhẹ|slight)$GAP$L"), NavState.M_SLIGHT_LEFT, directional = true),
        ManeuverRule(rx("(?:chếch|nhẹ|slight)$GAP$R"), NavState.M_SLIGHT_RIGHT, directional = true),
        ManeuverRule(rx("rẽ$GAP$L|turn$GAP$L|(?<![\\p{L}])$L(?![\\p{L}])"), NavState.M_LEFT, directional = true),
        ManeuverRule(rx("rẽ$GAP$R|turn$GAP$R|(?<![\\p{L}])$R(?![\\p{L}])"), NavState.M_RIGHT, directional = true),
        ManeuverRule(rx("xuất phát|khởi hành|\\bhead\\b|\\bdepart\\b"), NavState.M_DEPART),
        ManeuverRule(rx("đi thẳng|tiếp tục|\\bcontinue\\b|\\bstraight\\b"), NavState.M_STRAIGHT),
        // Sắp đến (chưa đến): vẫn là dẫn đường, mũi tên "đích". Không khớp "Arrive 14:19" / "Arrive\u00a014:19".
        ManeuverRule(rx("\\barrive\\b(?!$SP+(?:at$SP+|by$SP+)?\\d)|destination|điểm đến|sắp đến"), NavState.M_ARRIVE),
    )

    private val leftRx = rx("(?<![\\p{L}])$L(?![\\p{L}])")
    private val rightRx = rx("(?<![\\p{L}])$R(?![\\p{L}])")
    // Chỉ cụm khẳng định "đã đến"; phủ định/điều kiện ("haven't arrived yet", "chưa đến", "nếu đã đến") bị loại.
    private val arrivedRx = rx("(?<![\\p{L}])đã (?:đến|tới)|(?:you have|you've|have|has) arrived|^\\s*arrived(?!\\s+yet)")
    private val arrivedNegRx = rx("\\b(?:not|never|yet|if|until|before|unless|when)\\b|n't|nếu|chưa|không|trước khi|khi nào")
    private val thenRx = rx("(?<![\\p{L}])(?:then|rồi|sau đó)(?![\\p{L}])")
    private val rerouteRx = rx("tính lại|đang tìm tuyến|recalculating|rerouting|re-routing")
    // Dấu phân cách maneuver | tên đường; chỉ dấu đầu tiên có chữ đứng trước mới được xét (xem analyze).
    private val separatorRx = rx("(?:(?<![\\p{L}])vào|(?<![\\p{L}])trên|\\bonto\\b|\\bon\\b|\\btoward(?:s)?\\b|(?<![\\p{L}])hướng)\\s+(?=\\S)")
    // Cấu trúc tường minh mà dấu phân cách đứng đầu câu: "Vào vòng xuyến".
    private val enterRoundaboutRx = rx("^(?:vào|enter)\\s+(?:the\\s+)?(?:vòng xuyến|vòng xoay|bùng binh|roundabout|traffic circle|rotary)")

    // Token số đầy đủ: nhóm theo dấu cách ("1 050", cả nbsp/narrow nbsp) hoặc nhóm dấu chấm/phẩy.
    private const val NUM = "(\\d{1,3}(?:[\u00a0\u202f ]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)*)"
    // Không chấp nhận số dính vào ký tự số/dấu phía trước; dấu âm/dương (cách bao nhiêu khoảng trắng cũng được) kiểm bằng [signedBefore].
    private const val NOT_PART = "(?<![\\d.,:]|\\d[\u00a0\u202f ])"
    private const val SP = "[\\s\u00a0\u202f]"
    private const val UNIT = "(km|ki-?lô-?mét|kilomet(?:er|re)s?|mi(?:les?)?|mét|m(?:et(?:er|re)s?)?|ft|feet|foot|yd|yards?)(?![\\p{L}])"
    private val distanceRx = rx("$NOT_PART$NUM$SP*$UNIT")
    private val pureDistanceRx = rx("^$SP*$NUM$SP*$UNIT$SP*$")

    private const val TIME = "(\\d{1,2}):(\\d{2})(?:$SP*([ap]\\.?m\\.?|sa|ch))?"
    private val etaLabeledRx = listOf(
        rx("(?:đến lúc|đến nơi lúc|eta|arrive(?:$SP+(?:at|by))?|arrival|đến)$SP*:?$SP*$TIME"),
        rx("$TIME$SP*(?:eta|arrival)"),
    )
    private val bareTimeRx = rx("(?<![\\d:])$TIME(?![\\d:])")

    private val ordinalWords = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6,
        "nhất" to 1, "hai" to 2, "ba" to 3, "tư" to 4, "năm" to 5, "sáu" to 6,
    )
    private val exitRxs = listOf(
        rx("(?:lối ra|lối rẽ|ngã rẽ|exit)\\s*(?:thứ\\s*)?(\\d+)"),
        rx("(\\d+)(?:st|nd|rd|th)\\s+exit"),
        rx("(?:lối ra|lối rẽ|ngã rẽ)\\s*thứ\\s*(\\p{L}+)"),
        rx("(first|second|third|fourth|fifth|sixth)\\s+exit"),
        rx("exit\\s+(first|second|third|fourth|fifth|sixth)"),
    )

    private const val MAX_METERS = 20_000_000.0

    /** Mơ hồ trái/phải: khác hẳn "không nhận ra" (M_UNKNOWN) để không bị bigText khôi phục mũi tên. */
    private const val AMBIGUOUS = -1

    fun parse(
        title: String?,
        text: String?,
        subText: String?,
        bigText: String?,
        shortCriticalText: String? = null,
        progressRemainingM: Int = -1,
    ): NavState = try {
        parseUnsafe(
            title.orEmpty(),
            text.orEmpty(),
            subText.orEmpty(),
            bigText.orEmpty(),
            shortCriticalText.orEmpty(),
            progressRemainingM,
        )
    } catch (_: Exception) {
        NavState.IDLE
    }

    private class Analysis(
        val d: Int, val m: Int, val x: Int, val street: String, val instruction: String, val arrived: Boolean,
    )

    /** Có dấu +/-/−/– ngay trước vị trí [start] (phân biệt dấu gạch ngang phân cách kiểu "Turn right – 100 m"). */
    private fun signedBefore(text: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0 || text[i] !in "-−–+") return false
        // Dấu gạch ngang (- hoặc –) có khoảng trắng hai bên sau chữ/số là dấu phân cách câu, không phải dấu âm.
        if (text[i] in "-–" && i < start - 1 && i > 0 && text[i - 1].isWhitespace()) {
            var j = i - 1
            while (j >= 0 && text[j].isWhitespace()) j--
            if (j >= 0 && text[j].isLetterOrDigit()) return false
        }
        return true
    }

    /** Khoảng cách đầu tiên không mang dấu; token có dấu là không hợp lệ và bị bỏ qua (d=-1 nếu không còn token nào). */
    private fun findDistance(text: String): MatchResult? =
        distanceRx.findAll(text).firstOrNull { !signedBefore(text, it.range.first) && !digitBeforeAcrossGrouping(text, it.range.first) }

    /** Token bắt đầu giữa chừng một số có khoảng trắng nhóm ("-1  030 m" không được thành 30 m). */
    private fun digitBeforeAcrossGrouping(text: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && text[i] in "\u00a0\u202f ") i--
        return i < start - 1 && i >= 0 && text[i].isDigit()
    }

    private fun analyze(lines: List<String>): Analysis {
        // Trường khoảng cách đứng riêng (dòng chỉ chứa khoảng cách).
        var d = -1
        var distLine = -1
        for ((idx, l) in lines.withIndex()) {
            val pm = pureDistanceRx.find(l) ?: continue
            d = toMeters(pm.groupValues[1], pm.groupValues[2])
            distLine = idx
            break
        }
        // Không trim ở đây: dấu "-" đứng đầu phải còn nguyên để [findDistance] thấy dấu âm.
        val joined = lines.withIndex().filter { it.index != distLine }.joinToString(". ") { it.value }

        // Cô lập chỉ dẫn tức thời TRƯỚC (cắt từ "then"/"rồi"/"sau đó"), rồi mới tìm khoảng cách trong đó.
        var clause = thenRx.find(joined)?.let { joined.substring(0, it.range.first) } ?: joined
        if (distLine < 0) {
            findDistance(clause)?.let {
                d = toMeters(it.groupValues[1], it.groupValues[2])
                clause = clause.removeRange(it.range)
            }
        }
        clause = clause.trim(' ', '-', '–', '·', '•', ':', ',', '.', ';')

        // Tên đường chỉ được cô lập tại dấu phân cách ĐẦU TIÊN có chữ đứng trước; không bao giờ suy maneuver từ phần tên đường.
        var head = clause
        var street = ""
        var pre = clause // phần trước tên đường, dùng cho kiểm tra "đã đến"
        val seps = separatorRx.findAll(clause).toList()
        val first = seps.firstOrNull()
        if (first != null) {
            if (first.range.first > 0) {
                pre = clause.substring(0, first.range.first)
                head = pre
                street = clause.substring(first.range.last + 1)
            } else if (enterRoundaboutRx.containsMatchIn(clause)) {
                // "Vào vòng xuyến ...": maneuver kết thúc ở dấu phân cách kế tiếp (tên đường sau đó không được dùng để suy lối ra).
                val next = seps.getOrNull(1)
                head = if (next != null) clause.substring(0, next.range.first) else clause
                pre = head
                street = if (next != null) clause.substring(next.range.last + 1) else ""
            } else {
                // Cụm tên đường đứng đầu ("On Left Fork Road ...") chỉ là ngữ cảnh; chỉ phân loại mệnh đề maneuver tách biệt đứng sau nếu có.
                val end = clause.indexOfAny(charArrayOf(',', '.', ';'))
                street = clause.substring(first.range.last + 1, if (end < 0) clause.length else end)
                head = if (end < 0) "" else clause.substring(end + 1).trim()
                val headSeps = separatorRx.findAll(head).toList()
                val hFirst = headSeps.firstOrNull()
                if (hFirst != null) {
                    if (hFirst.range.first > 0) {
                        street = head.substring(hFirst.range.last + 1)
                        head = head.substring(0, hFirst.range.first)
                    } else if (enterRoundaboutRx.containsMatchIn(head)) {
                        val next = headSeps.getOrNull(1)
                        street = if (next != null) head.substring(next.range.last + 1) else street
                        head = if (next != null) head.substring(0, next.range.first) else head
                    } else {
                        head = ""
                    }
                }
                pre = head
            }
            street = street.trim(' ', '.', ',')
        }
        val (m, x) = classify(head, head)
        val arrived = arrivedRx.containsMatchIn(pre) && !arrivedNegRx.containsMatchIn(pre)
        val s = if (m <= NavState.M_UNKNOWN && street.isEmpty()) clause else street
        return Analysis(d, if (arrived) NavState.M_ARRIVE else m, x, s, clause, arrived)
    }

    /** Trả (maneuver, exit). Mơ hồ trái/phải -> ([AMBIGUOUS], 0). */
    private fun classify(head: String, exitSource: String): Pair<Int, Int> {
        for (rule in maneuverRules) {
            if (!rule.pattern.containsMatchIn(head)) continue
            if (rule.directional && leftRx.containsMatchIn(head) && rightRx.containsMatchIn(head)) return AMBIGUOUS to 0
            return rule.maneuver to (if (rule.exitGroup) exitNumber(exitSource) else 0)
        }
        return NavState.M_UNKNOWN to 0
    }

    private fun parseUnsafe(
        title: String,
        text: String,
        subText: String,
        bigText: String,
        shortCriticalText: String,
        progressRemainingM: Int,
    ): NavState {
        val primary = listOf(title, text).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val expanded = listOf(bigText).map { it.trim() }.filter { it.isNotEmpty() && it !in primary }
        val sub = subText.trim()
        val shortCrit = shortCriticalText.trim()
        if (primary.isEmpty() && expanded.isEmpty() && sub.isEmpty() && shortCrit.isEmpty()) return NavState.IDLE

        var a = analyze(primary)
        val primaryM = a.m
        if (a.m == NavState.M_UNKNOWN && expanded.isNotEmpty()) { // mơ hồ (AMBIGUOUS) thì KHÔNG fallback
            val b = analyze(expanded)
            if (b.m != NavState.M_UNKNOWN) {
                a = Analysis(if (a.d >= 0) a.d else b.d, b.m, b.x, b.street.ifEmpty { a.street }, b.instruction, b.arrived)
            } else if (a.d < 0) {
                a = Analysis(b.d, a.m, a.x, a.street, a.instruction, a.arrived)
            }
        }
        // Android 16/17 Live Updates (ProgressStyle): shortCriticalText có thể chứa khoảng cách tới điểm rẽ ("80 m").
        if (a.d < 0 && shortCrit.isNotEmpty()) {
            val critD = pureDistanceRx.find(shortCrit)?.let { toMeters(it.groupValues[1], it.groupValues[2]) } ?: -1
            if (critD >= 0) a = Analysis(critD, a.m, a.x, a.street, a.instruction, a.arrived)
        }

        // Reroute cũng ưu tiên trường chính: bigText chỉ được xét khi trường chính không nhận ra gì.
        val reroute = rerouteRx.containsMatchIn(primary.joinToString(" ")) ||
            (primaryM == NavState.M_UNKNOWN && rerouteRx.containsMatchIn(expanded.joinToString(" ")))
        val (eta, parsedRemaining) = parseSubText(sub, primary + expanded)
        val hasNavSignal = reroute || a.arrived || a.m != NavState.M_UNKNOWN || a.d >= 0 || eta.isNotEmpty() || parsedRemaining >= 0
        val remaining = when {
            parsedRemaining >= 0 -> parsedRemaining
            hasNavSignal && progressRemainingM in 0..MAX_METERS.toInt() -> progressRemainingM
            else -> -1
        }

        val k = when {
            reroute -> NavState.K_REROUTING
            a.arrived -> NavState.K_ARRIVED
            hasNavSignal -> NavState.K_NAVIGATING
            else -> return NavState.IDLE
        }
        val d = if (k == NavState.K_ARRIVED) 0 else a.d
        return NavState(k = k, m = a.m.coerceAtLeast(NavState.M_UNKNOWN), x = a.x, d = d, s = a.street, e = eta, r = remaining)
    }

    private fun parseSubText(sub: String, lines: List<String>): Pair<String, Int> {
        var eta = ""
        var remaining = -1
        val haystack = (listOf(sub) + lines).joinToString(" | ")
        for (r in etaLabeledRx) {
            r.find(haystack)?.let { eta = formatTime(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            if (eta.isNotEmpty()) break
        }
        for (seg in sub.split('·', '•', '|')) {
            val t = seg.trim()
            if (eta.isEmpty()) {
                bareTimeRx.find(t)?.let { eta = formatTime(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            }
            if (remaining < 0) {
                pureDistanceRx.find(t)?.let { remaining = toMeters(it.groupValues[1], it.groupValues[2]) }
            }
        }
        return eta to remaining
    }

    private fun exitNumber(text: String): Int {
        for (r in exitRxs) {
            val g = r.find(text)?.groupValues?.get(1)?.lowercase() ?: continue
            g.toIntOrNull()?.let { return it }
            ordinalWords[g]?.let { return it }
        }
        return 0
    }

    private fun formatTime(h: String, mi: String, suffix: String): String {
        var hour = h.toInt()
        val min = mi.toInt()
        val sfx = suffix.lowercase().replace(".", "")
        if (sfx == "pm" || sfx == "ch") { if (hour < 12) hour += 12 }
        if ((sfx == "am" || sfx == "sa") && hour == 12) hour = 0
        if (hour > 23 || min > 59) return ""
        return "%02d:%02d".format(hour, min)
    }

    /** Mét (làm tròn), hoặc -1 nếu số không hợp lệ/âm/quá lớn. */
    internal fun toMeters(num: String, unit: String): Int {
        val u = unit.lowercase()
        val small = !(u.startsWith("k") || u == "mi" || u.startsWith("mile"))
        val value = parseNumber(num, small) ?: return -1
        val factor = when {
            u.startsWith("k") -> 1000.0
            u == "mi" || u.startsWith("mile") -> 1609.344
            u == "ft" || u == "feet" || u == "foot" -> 0.3048
            u == "yd" || u.startsWith("yard") -> 0.9144
            else -> 1.0
        }
        val meters = value * factor
        if (!meters.isFinite() || meters < 0 || meters > MAX_METERS) return -1
        return Math.round(meters).toInt()
    }

    private fun groupsOk(intPart: String, sep: Char): Boolean {
        val parts = intPart.split(sep)
        return parts[0].length in 1..3 && parts.drop(1).all { it.length == 3 }
    }

    /**
     * "1,234.5" / "1.234,5": dấu cuối là thập phân, dấu còn lại là hàng nghìn (phải đúng nhóm 3).
     * Một dấu duy nhất: "1,2" / "0.5" là thập phân; đúng 3 chữ số phía sau với đơn vị nhỏ (m, ft, yd)
     * như "1.234 m" là hàng nghìn. Nhiều dấu cùng loại: hàng nghìn, sai nhóm thì null.
     */
    private fun parseNumber(rawNum: String, smallUnit: Boolean): Double? {
        val num = rawNum.replace(Regex("[\u00a0\u202f ]"), "")
        val hasDot = '.' in num
        val hasComma = ',' in num
        if (hasDot && hasComma) {
            val dec = if (num.lastIndexOf('.') > num.lastIndexOf(',')) '.' else ','
            val thou = if (dec == '.') ',' else '.'
            val idx = num.lastIndexOf(dec)
            val intPart = num.substring(0, idx)
            val frac = num.substring(idx + 1)
            if (dec in intPart || thou in frac || !groupsOk(intPart, thou)) return null
            return (intPart.replace(thou.toString(), "") + "." + frac).toDoubleOrNull()
        }
        val sep = if (hasDot) '.' else if (hasComma) ',' else return num.toDoubleOrNull()
        val parts = num.split(sep)
        if (parts.size > 2) return if (groupsOk(num, sep)) num.replace(sep.toString(), "").toDoubleOrNull() else null
        val (a, b) = parts
        if (smallUnit && b.length == 3 && a.length in 1..3 && a[0] != '0') return (a + b).toDoubleOrNull()
        return "$a.$b".toDoubleOrNull()
    }
}
