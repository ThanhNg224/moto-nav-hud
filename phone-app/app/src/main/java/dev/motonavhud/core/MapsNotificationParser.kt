package dev.motonavhud.core

/**
 * Parser cho notification dẫn đường của Google Maps: heuristic văn bản Việt + Anh + Android 16/17 Live Updates.
 * Không bao giờ ném exception.
 *
 * Quy tắc chính:
 * - Maneuver lấy từ chỉ dẫn TỨC THỜI (trước "then"/"rồi"/"sau đó") của trường chính (title/text);
 *   bigText chỉ là dự phòng khi trường chính không nhận ra.
 * - Dòng tóm tắt chuyến đi (ETA / thời lượng / tổng quãng đường) được tách riêng khỏi tên đường và `d`.
 * - Chỉ dẫn mơ hồ (vừa trái vừa phải trong cùng mệnh đề) -> m=0 và `ambiguous=true` (chặn cả bigText lẫn icon fallback).
 * - "Đã đến"/"have arrived" mới là k=3; "arrive at ..." (sắp đến) vẫn là k=1.
 * - Khoảng cách không hợp lệ/âm/tràn -> d=-1, không bao giờ là 0.
 * - Không nhận ra gì -> [NavState.IDLE] (k=0).
 */
object MapsNotificationParser {

    /** Kết quả chi tiết giữ nguyên cờ [ambiguous] qua ranh giới parser để listener không dùng icon ghi đè chỉ dẫn mơ hồ. */
    data class ParseResult(val state: NavState, val ambiguous: Boolean = false)

    private class ManeuverRule(
        val pattern: Regex,
        val maneuver: Int,
        val exitGroup: Boolean = false,
        val directional: Boolean = false,
        val explicit: Boolean = true,
    )

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
        ManeuverRule(rx("rẽ$GAP$L|turn$GAP$L"), NavState.M_LEFT, directional = true),
        ManeuverRule(rx("rẽ$GAP$R|turn$GAP$R"), NavState.M_RIGHT, directional = true),
        ManeuverRule(rx("(?<![\\p{L}])$L(?![\\p{L}])"), NavState.M_LEFT, directional = true, explicit = false),
        ManeuverRule(rx("(?<![\\p{L}])$R(?![\\p{L}])"), NavState.M_RIGHT, directional = true, explicit = false),
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
    // Giới từ khoảng cách ngay trước dấu "-" (vd "Turn left in - 30 m"): bắt buộc coi "-" là dấu âm, không phải dấu nối câu.
    private val trailingPrepRx = rx("(?:^|[\\s\u00a0\u202f])(?:in|at|after|for|about|approx|by|to|from|of|within|trong|sau|còn|khoảng)$")

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
    private val pureEtaLabeledRx = rx("^$SP*(?:(?:đến lúc|đến nơi lúc|eta|arrive(?:$SP+(?:at|by))?|arrival|đến)$SP*:?$SP*$TIME|$TIME$SP*(?:eta|arrival))$SP*$")
    private val bareTimeRx = rx("(?<![\\d:])$TIME(?![\\d:])")
    private val pureBareTimeRx = rx("^$SP*$TIME$SP*$")
    private val pureDurationRx = rx("^$SP*\\d+(?:[.,]\\d+)?$SP*(?:hours?|hrs?|hr|h|giờ|minutes?|mins?|min|phút|p)(?:$SP+\\d+$SP*(?:minutes?|mins?|min|phút|p))?$SP*$")
    private val parenDistanceRx = rx("\\($SP*$NUM$SP*$UNIT$SP*\\)")
    private val trailingEtaClauseRx = rx("(?:$SP*[·•|.,;\\-–]$SP*|$SP+)(?:(?:đến lúc|đến nơi lúc|eta|arrive(?:$SP+(?:at|by))?|arrival)$SP*:?$SP*$TIME|$TIME$SP*(?:eta|arrival))$SP*$")

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

    /** Mơ hồ trái/phải: khác hẳn "không nhận ra" (M_UNKNOWN) để không bị bigText hay icon khôi phục mũi tên. */
    private const val AMBIGUOUS = -1

    /**
     * Chỉ lấy `progressMax - progress` làm quãng đường còn lại (mét) khi có đủ bằng chứng của định dạng
     * ProgressStyle theo mét của Google Maps (category=navigation, có progressSegments, và thang > 100 để
     * loại thang phần trăm 0..100 mặc định của Android). Ngược lại trả -1.
     */
    fun extractProgressRemainingM(
        category: String?,
        template: String?,
        indeterminate: Boolean,
        hasSegments: Boolean,
        progress: Int,
        progressMax: Int,
    ): Int {
        if (category != "navigation") return -1
        if (template.isNullOrEmpty() || !template.contains("ProgressStyle")) return -1
        if (indeterminate || !hasSegments || progressMax <= 100 || progress !in 0..progressMax) return -1
        val rem = progressMax - progress
        return if (rem in 0..MAX_METERS.toInt()) rem else -1
    }

    fun parse(
        title: String?,
        text: String?,
        subText: String?,
        bigText: String?,
        shortCriticalText: String? = null,
        progressRemainingM: Int = -1,
    ): NavState = parseDetailed(title, text, subText, bigText, shortCriticalText, progressRemainingM).state

    fun parseDetailed(
        title: String?,
        text: String?,
        subText: String?,
        bigText: String?,
        shortCriticalText: String? = null,
        progressRemainingM: Int = -1,
    ): ParseResult = try {
        parseUnsafe(
            title.orEmpty(),
            text.orEmpty(),
            subText.orEmpty(),
            bigText.orEmpty(),
            shortCriticalText.orEmpty(),
            progressRemainingM,
        )
    } catch (_: Exception) {
        ParseResult(NavState.IDLE, ambiguous = false)
    }

    private class Analysis(
        val d: Int, val m: Int, val x: Int, val street: String, val instruction: String, val arrived: Boolean,
    )

    /** Có dấu +/-/−/– ngay trước vị trí [start] (chỉ miễn dấu gạch nối câu ngay sau một maneuver hoàn chỉnh, không đứng sau giới từ kiểu "in - 30 m"). */
    private fun signedBefore(text: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0 || text[i] !in "-−–+") return false
        if (text[i] in "-–" && i < start - 1 && i > 0 && text[i - 1].isWhitespace()) {
            val before = text.substring(0, i).trimEnd()
            if (before.isNotEmpty() && before.last().isLetterOrDigit() && !trailingPrepRx.containsMatchIn(before)) {
                if (classify(before, before).first > NavState.M_UNKNOWN) return false
            }
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

    /**
     * Dòng chỉ chứa tóm tắt chuyến đi (ETA / thời lượng / tổng quãng đường, vd "Arrive 14:30", "15 min · 4.2 km · 14:30",
     * "12 phút (5,3 km) · 12:45") phải được tách riêng khỏi tên đường và khoảng cách rẽ `d`.
     * Một dòng chỉ có duy nhất khoảng cách ("200 m") KHÔNG phải dòng tóm tắt để vẫn làm khoảng cách rẽ `d`.
     */
    private fun isTripSummaryLine(line: String): Boolean {
        val raw = line.trim()
        if (raw.isEmpty()) return false
        val segs = raw.split('·', '•', '|').map { it.trim() }.filter { it.isNotEmpty() }
        if (segs.isEmpty()) return false
        var hasEtaOrDuration = false
        for (seg in segs) {
            val withoutParenDist = parenDistanceRx.replace(seg, "").trim()
            if (pureEtaLabeledRx.matches(seg) || pureBareTimeRx.matches(seg) || pureDurationRx.matches(seg)) {
                hasEtaOrDuration = true
            } else if (withoutParenDist != seg && pureDurationRx.matches(withoutParenDist)) {
                hasEtaOrDuration = true
            } else if (pureDistanceRx.matches(seg)) {
                // Hợp lệ chỉ khi đi cùng ETA hoặc thời lượng trong cùng dòng
            } else {
                return false
            }
        }
        return hasEtaOrDuration
    }

    private fun stripTrailingEta(s: String): String = trailingEtaClauseRx.replace(s, "").trim(' ', '.', ',', '·', '•', '|', '-', '–')

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
        var requireExplicit = false
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
                // Cụm tên đường đứng đầu ("On Dr. Left Fork Road ...", "On Main St, turn right onto B"):
                // chỉ tách sang mệnh đề maneuver phía sau khi mệnh đề đó có chỉ dẫn TƯỜNG MINH (không để "Dr." làm lộ "Left" từ tên đường).
                street = clause.substring(first.range.last + 1)
                head = ""
                var pos = first.range.last + 1
                while (pos < clause.length) {
                    val end = clause.indexOfAny(charArrayOf(',', '.', ';'), pos)
                    if (end < 0) break
                    val trailing = clause.substring(end + 1).trim()
                    if (trailing.isNotEmpty()) {
                        var candHead = trailing
                        var candStreet = clause.substring(first.range.last + 1, end)
                        val tSeps = separatorRx.findAll(trailing).toList()
                        val tFirst = tSeps.firstOrNull()
                        if (tFirst != null) {
                            if (tFirst.range.first > 0) {
                                candHead = trailing.substring(0, tFirst.range.first)
                                candStreet = trailing.substring(tFirst.range.last + 1)
                            } else if (enterRoundaboutRx.containsMatchIn(trailing)) {
                                val next = tSeps.getOrNull(1)
                                candHead = if (next != null) trailing.substring(0, next.range.first) else trailing
                                candStreet = if (next != null) trailing.substring(next.range.last + 1) else candStreet
                            } else {
                                candHead = ""
                            }
                        }
                        if (candHead.isNotEmpty() && classify(candHead, candHead, requireExplicit = true).first != NavState.M_UNKNOWN) {
                            head = candHead
                            street = candStreet
                            requireExplicit = true
                            break
                        }
                    }
                    pos = end + 1
                }
                // Nếu vẫn còn dấu phẩy ngăn cách với cụm "toward ..." không phải maneuver (vd "On Main St, toward Left Fork Road"),
                // giữ tên đường gọn trước dấu phẩy.
                if (head.isEmpty()) {
                    val comma = street.indexOfAny(charArrayOf(',', ';'))
                    if (comma > 0) street = street.substring(0, comma)
                }
                pre = head
            }
            street = stripTrailingEta(street)
        }
        val (m, x) = classify(head, head, requireExplicit = requireExplicit)
        val arrived = arrivedRx.containsMatchIn(pre) && !arrivedNegRx.containsMatchIn(pre)
        val s = if (m <= NavState.M_UNKNOWN && street.isEmpty()) stripTrailingEta(clause) else street
        return Analysis(d, if (arrived) NavState.M_ARRIVE else m, x, s, clause, arrived)
    }

    /** Trả (maneuver, exit). Mơ hồ trái/phải -> ([AMBIGUOUS], 0). */
    private fun classify(head: String, exitSource: String, requireExplicit: Boolean = false): Pair<Int, Int> {
        for (rule in maneuverRules) {
            if (requireExplicit && !rule.explicit) continue
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
    ): ParseResult {
        val rawPrimary = listOf(title, text).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val (primarySummaries, primary) = rawPrimary.partition { isTripSummaryLine(it) }
        val rawExpanded = listOf(bigText).map { it.trim() }.filter { it.isNotEmpty() && it !in rawPrimary }
        val (expandedSummaries, expanded) = rawExpanded.partition { isTripSummaryLine(it) }
        val sub = subText.trim()
        val shortCrit = shortCriticalText.trim()
        if (rawPrimary.isEmpty() && rawExpanded.isEmpty() && sub.isEmpty() && shortCrit.isEmpty()) {
            return ParseResult(NavState.IDLE, ambiguous = false)
        }

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
        val summaryInputs = (listOf(sub) + primarySummaries + expandedSummaries).filter { it.isNotEmpty() }
        val (eta, parsedRemaining) = parseSubText(summaryInputs, primary + expanded)
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
            else -> return ParseResult(NavState.IDLE, ambiguous = false)
        }
        val d = if (k == NavState.K_ARRIVED) 0 else a.d
        return ParseResult(
            state = NavState(k = k, m = a.m.coerceAtLeast(NavState.M_UNKNOWN), x = a.x, d = d, s = a.street, e = eta, r = remaining),
            ambiguous = a.m == AMBIGUOUS,
        )
    }

    private fun parseSubText(summaries: List<String>, lines: List<String>): Pair<String, Int> {
        var eta = ""
        var remaining = -1
        val haystack = (summaries + lines).joinToString(" | ")
        for (r in etaLabeledRx) {
            r.find(haystack)?.let { eta = formatTime(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            if (eta.isNotEmpty()) break
        }
        for (sum in summaries) {
            for (seg in sum.split('·', '•', '|')) {
                val t = seg.trim()
                if (eta.isEmpty()) {
                    bareTimeRx.find(t)?.let { eta = formatTime(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
                }
                if (remaining < 0) {
                    pureDistanceRx.find(t)?.let { remaining = toMeters(it.groupValues[1], it.groupValues[2]) }
                        ?: parenDistanceRx.find(t)?.let { remaining = toMeters(it.groupValues[1], it.groupValues[2]) }
                }
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
