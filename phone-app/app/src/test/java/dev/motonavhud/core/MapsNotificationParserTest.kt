package dev.motonavhud.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TẤT CẢ fixture dưới đây là SYNTHETIC (tự soạn, không phải chuỗi thật của Google Maps).
 * Khi có mẫu thật từ Capture, thêm dòng vào bảng tương ứng.
 */
class MapsNotificationParserTest {
    private class Case(
        val name: String,
        val title: String?, val text: String?, val sub: String?, val big: String? = null,
        val k: Int, val m: Int, val d: Int, val x: Int = 0, val s: String? = null, val e: String? = null, val r: Int? = null,
    )

    private val vi = listOf(
        Case("vi rẽ trái", "200 m", "Rẽ trái vào Nguyễn Văn Linh", "12 phút · 5,3 km · 12:45", null, 1, 3, 200, s = "Nguyễn Văn Linh", e = "12:45", r = 5300),
        Case("vi rẽ phải km thập phân", "1,2 km", "Rẽ phải vào Lê Văn Lương", "Đến lúc 09:05", null, 1, 6, 1200, s = "Lê Văn Lương", e = "09:05"),
        Case("vi title là chỉ dẫn", "Rẽ trái vào Hoàng Diệu", "350 m", "8 phút · 2 km · 18:20", null, 1, 3, 350, s = "Hoàng Diệu", e = "18:20", r = 2000),
        Case("vi quay đầu", "50 m", "Quay đầu", null, null, 1, 8, 50),
        Case("vi vòng xuyến lối ra thứ 2", "150 m", "Vào vòng xuyến, đi theo lối ra thứ 2 vào Phạm Văn Đồng", null, null, 1, 9, 150, x = 2, s = "Phạm Văn Đồng"),
        Case("vi vòng xoay lối ra thứ ba", "80 m", "Tại vòng xoay, lối ra thứ ba", null, null, 1, 9, 80, x = 3),
        Case("vi giữ bên trái", "400 m", "Giữ bên trái để vào Võ Văn Kiệt", null, null, 1, 10, 400, s = "Võ Văn Kiệt"),
        Case("vi giữ bên phải", "300 m", "Giữ bên phải", null, null, 1, 11, 300),
        Case("vi đi thẳng", "2 km", "Đi thẳng trên Đại lộ Thăng Long", null, null, 1, 1, 2000, s = "Đại lộ Thăng Long"),
        Case("vi đã đến", "", "Bạn đã đến nơi", null, null, 3, 12, 0),
        Case("vi rẽ nhẹ trái", "120 m", "Rẽ nhẹ sang trái vào Trần Hưng Đạo", null, null, 1, 2, 120, s = "Trần Hưng Đạo"),
        Case("vi rẽ gắt phải", "90 m", "Rẽ gắt sang phải", null, null, 1, 7, 90),
        Case("vi mét thường", "500 mét", "Rẽ phải", null, null, 1, 6, 500),
        Case("vi tính lại tuyến", "", "Đang tính lại tuyến đường", null, null, 2, 0, -1),
        Case("vi tên đường chứa Phải không gây nhầm", "200 m", "Rẽ trái vào Phải Lê", null, null, 1, 3, 200, s = "Phải Lê"),
    )

    private val en = listOf(
        Case("en turn left", "200 m", "Turn left onto High St", "13 min · 4.6 km · 11:55 ETA", null, 1, 3, 200, s = "High St", e = "11:55", r = 4600),
        Case("en turn right miles", "0.5 mi", "Turn right onto Main Street", "20 min · 8 mi · 3:40 PM", null, 1, 6, 805, s = "Main Street", e = "15:40", r = 12875),
        Case("en roundabout 3rd exit", "100 m", "At the roundabout, take the 3rd exit onto Ring Rd", null, null, 1, 9, 100, x = 3, s = "Ring Rd"),
        Case("en roundabout exit word", "100 m", "Roundabout, take the second exit", null, null, 1, 9, 100, x = 2),
        Case("en u-turn", "30 m", "Make a U-turn", null, null, 1, 8, 30),
        Case("en keep left", "600 m", "Keep left at the fork", null, null, 1, 10, 600),
        Case("en keep right", "600 m", "Keep right onto A1", null, null, 1, 11, 600, s = "A1"),
        Case("en continue straight feet", "300 ft", "Continue straight on Oak Ave", null, null, 1, 1, 91, s = "Oak Ave"),
        Case("en arrive prospective stays navigating", "", "Arrive at your destination", null, null, 1, 12, -1),
        Case("en slight right", "250 yd", "Slight right onto Elm St", null, null, 1, 5, 229, s = "Elm St"),
        Case("en sharp left", "70 m", "Sharp left", null, null, 1, 4, 70),
        Case("en head depart", "10 m", "Head north on Park Rd", null, null, 1, 13, 10, s = "Park Rd"),
        Case("en recalculating", "", "Recalculating route", null, null, 2, 0, -1),
        Case("en ETA arrive at", "100 m", "Turn left", "Arrive at 18:30 · 5 km", null, 1, 3, 100, e = "18:30", r = 5000),
        Case("en bigText only", "", "", null, "In 300 m turn right onto Bay Rd", 1, 6, 300, s = "Bay Rd"),
    )

    private val edge = listOf(
        Case("rác không nhận ra -> k=0", "Chào bạn", "Hôm nay trời đẹp", null, null, 0, 0, -1),
        Case("toàn rỗng -> k=0", "", "", "", "", 0, 0, -1),
        Case("null hết -> k=0", null, null, null, null, 0, 0, -1),
        Case("chỉ khoảng cách -> k=1 m=0", "200 m", "", null, null, 1, 0, 200),
        Case("hàng nghìn 1.200 m", "1.200 m", "Đi thẳng", null, null, 1, 1, 1200),
    )

    private val review = listOf(
        Case("then clause must not win (en)", "100 m", "Turn right, then turn left", null, null, 1, 6, 100),
        Case("then clause must not win (vi)", "100 m", "Rẽ phải rồi rẽ trái vào Hai Bà Trưng", null, null, 1, 6, 100),
        Case("then clause street ignored", "100 m", "Turn right onto A then turn left onto B", null, null, 1, 6, 100, s = "A"),
        Case("ambiguous left/right -> m=0", "200 m", "Turn left or right", null, null, 1, 0, 200),
        Case("prospective arrival is navigating", "", "In 150 m, arrive at your destination", null, null, 1, 12, 150),
        Case("completed arrival en", "", "You have arrived", null, null, 3, 12, 0),
        Case("roundabout survives street split", "150 m", "Vào vòng xuyến", null, null, 1, 9, 150),
        Case("expanded text is fallback", "300 m", "Navigating", null, "Turn right onto Bay Rd", 1, 6, 300, s = "Bay Rd"),
        Case("primary wins over expanded", "Turn left onto A", "100 m", null, "Turn right onto B", 1, 3, 100, s = "A"),
        Case("comma thousands + dot decimal", "1,234.5 m", "Turn left", null, null, 1, 3, 1235),
        Case("dot thousands meters", "1.234 m", "Turn left", null, null, 1, 3, 1234),
        Case("comma decimal km", "1,2 km", "Turn left", null, null, 1, 3, 1200),
        Case("garbage grouping -> -1 not 0", "1,234.5.6 m", "Turn left", null, null, 1, 3, -1),
        Case("negative distance -> -1", "Turn left", "-50 m", null, null, 1, 3, -1),
        Case("overflow distance -> -1", "99999999999 m", "Turn left", null, null, 1, 3, -1),
        Case("huge digits -> -1", "9".repeat(400) + " km", "Turn left", null, null, 1, 3, -1),
    )

    @Test fun reviewRegressions() = review.forEach(::check)

    private val verification = listOf(
        // a: khoảng cách chỉ lấy trong chỉ dẫn tức thời
        Case("then-clause distance not paired", "Turn right, then in 30 m turn left", null, null, null, 1, 6, -1),
        Case("standalone distance field still works", "Turn right, then in 30 m turn left", "200 m", null, null, 1, 6, 200),
        Case("vi then-clause distance not paired", "Rẽ phải, rồi sau 30 m rẽ trái", null, null, null, 1, 6, -1),
        // b: mơ hồ không bị bigText khôi phục
        Case("ambiguous blocks expanded fallback", "200 m", "Turn left or right", null, "Turn right onto B", 1, 0, 200),
        Case("unrecognized primary still falls back", "200 m", "Navigating", null, "Turn right onto B", 1, 6, 200, s = "B"),
        // c: token số phải đầy đủ
        Case("spaced negative -> -1", "Turn left", "- 30 m", null, null, 1, 3, -1),
        Case("space grouping 1 050 m", "1 050 m", "Turn left", null, null, 1, 3, 1050),
        Case("nbsp grouping", "1\u00a0050 m", "Turn left", null, null, 1, 3, 1050),
        Case("narrow nbsp grouping", "1\u202f050 m", "Turn left", null, null, 1, 3, 1050),
        Case("grouping inside text", "Turn left", "In 1 050 m", null, null, 1, 3, 1050),
        Case("digit-prefixed suffix not a distance", "Turn left", "2 1 050 m", null, null, 1, 3, -1),
        // d: arrived phải khẳng định
        Case("haven't arrived yet is not arrival", "", "You haven't arrived yet", null, null, 0, 0, -1),
        Case("not arrived", "", "You have not arrived", null, null, 0, 0, -1),
        Case("if you have arrived", "", "If you have arrived, stop", null, null, 0, 0, -1),
        Case("vi chưa đến", "", "Bạn chưa đến nơi", null, null, 0, 0, -1),
        Case("you've arrived", "", "You've arrived", null, null, 3, 12, 0),
        // e: reroute theo trường chính
        Case("primary maneuver beats expanded reroute", "Turn left onto A", "100 m", null, "Recalculating route", 1, 3, 100, s = "A"),
        Case("primary reroute wins", "", "Recalculating", null, "Turn left", 2, 3, -1),
    )

    private val round3 = listOf(
        Case("merge onto Left Fork Road is not a left turn", "100 m", "Merge onto Left Fork Road", null, null, 1, 0, 100, s = "Left Fork Road"),
        Case("turn left onto Right Fork Road on the Hill", "100 m", "Turn left onto Right Fork Road on the Hill", null, null, 1, 3, 100, s = "Right Fork Road on the Hill"),
        Case("vi hòa vào Đường Rẽ Trái", "100 m", "Nhập vào Đường Rẽ Trái", null, null, 1, 0, 100, s = "Đường Rẽ Trái"),
        Case("roundabout with exit and street", "150 m", "Vào vòng xuyến, đi theo lối ra thứ 2 vào Phạm Văn Đồng", null, null, 1, 9, 150, x = 2, s = "Phạm Văn Đồng"),
        Case("sign then 4 spaces -> -1", "-    30 m", "Turn left", null, null, 1, 3, -1),
        Case("sign then 10 spaces -> -1", "Turn left", "-          30 m", null, null, 1, 3, -1),
        Case("plus sign -> -1", "Turn left", "+  30 m", null, null, 1, 3, -1),
        Case("unsigned still ok", "30 m", "Turn left", null, null, 1, 3, 30),
    )

    private val round4 = listOf(
        Case("leading road phrase is not a maneuver", "100 m", "On Left Fork Road toward downtown", null, null, 1, 0, 100, s = "Left Fork Road toward downtown"),
        Case("leading road phrase then explicit maneuver", "100 m", "On Main St, turn right onto B", null, null, 1, 6, 100, s = "B"),
        Case("roundabout exit from maneuver only", "100 m", "At the roundabout, take the second exit onto Exit 7 Ramp", null, null, 1, 9, 100, x = 2, s = "Exit 7 Ramp"),
        Case("enter roundabout exit from maneuver only", "100 m", "Vào vòng xuyến, đi theo lối ra thứ 2 vào Lối ra 7", null, null, 1, 9, 100, x = 2, s = "Lối ra 7"),
        Case("signed distance split by double space -> -1", "-1  030 m", "Turn left", null, null, 1, 3, -1),
        Case("vi leading road phrase then explicit maneuver", "200 m", "Trên đường Nguyễn Trãi, rẽ trái", null, null, 1, 3, 200, s = "đường Nguyễn Trãi"),
        Case("leading road phrase then toward road name is not maneuver", "100 m", "On Main St, toward Left Fork Road", null, null, 1, 0, 100, s = "Main St"),
        Case("vi leading road phrase then enter roundabout with exit", "150 m", "Trên đường A, vào vòng xuyến, lối ra thứ 2 vào Lối ra 7", null, null, 1, 9, 150, x = 2, s = "Lối ra 7"),
        Case("dash separator before distance is not negative", "", "Turn right – 100 m", null, null, 1, 6, 100),
        Case("hyphen separator before distance is not negative", "", "Rẽ phải - 150 m", null, null, 1, 6, 150),
        Case("attached minus after text is still negative", "", "Turn right -100 m", null, null, 1, 6, -1),
    )

    @Test fun round4Regressions() = round4.forEach(::check)

    @Test fun realOppoAndroid17Maps26ProgressStyle() {
        // Mẫu thật thu từ OPPO CPH2873 Android 17 + Google Maps 26.47.00 (mode=l xe máy, ProgressStyle Live Updates).
        val s1 = MapsNotificationParser.parse(
            title = "80\u00a0m · Turn right onto P. Tố Hữu",
            text = null,
            subText = "Arrive 14:19",
            bigText = null,
            shortCriticalText = "80\u00a0m",
            progressRemainingM = 9959,
        )
        assertEquals(NavState.K_NAVIGATING, s1.k)
        assertEquals(NavState.M_RIGHT, s1.m)
        assertEquals(80, s1.d)
        assertEquals("P. Tố Hữu", s1.s)
        assertEquals("14:19", s1.e)
        assertEquals(9959, s1.r)

        val s2 = MapsNotificationParser.parse(
            title = "Head toward P. Tố Hữu",
            text = null,
            subText = "Arrive 14:02",
            bigText = null,
            shortCriticalText = "",
            progressRemainingM = 3899,
        )
        assertEquals(NavState.K_NAVIGATING, s2.k)
        assertEquals(NavState.M_DEPART, s2.m)
        assertEquals(-1, s2.d)
        assertEquals("P. Tố Hữu", s2.s)
        assertEquals("14:02", s2.e)
        assertEquals(3899, s2.r)

        // shortCriticalText làm dự phòng khoảng cách khi title không có số mét; "Arrive 14:30" tách riêng khỏi tên đường.
        val s3 = MapsNotificationParser.parse(
            title = "Turn left onto Nguyễn Trãi",
            text = "Arrive\u00a014:30",
            subText = null,
            bigText = null,
            shortCriticalText = "120\u00a0m",
            progressRemainingM = 2500,
        )
        assertEquals(NavState.K_NAVIGATING, s3.k)
        assertEquals(NavState.M_LEFT, s3.m)
        assertEquals(120, s3.d)
        assertEquals("Nguyễn Trãi", s3.s)
        assertEquals("14:30", s3.e)
        assertEquals(2500, s3.r)

        // Notification rác có progressMax nhưng không có tín hiệu dẫn đường vẫn phải trả IDLE.
        val s4 = MapsNotificationParser.parse(
            title = "Downloading offline map",
            text = null,
            subText = null,
            bigText = null,
            shortCriticalText = null,
            progressRemainingM = 5000,
        )
        assertEquals(NavState.K_IDLE, s4.k)
    }

    @Test fun round5AuditRegressions() {
        // 1. ETA đổi 14:30 -> 14:31 không đổi tên đường và KHÔNG tạo bước mới (stepId i giữ nguyên để không rung lại)
        val a1 = MapsNotificationParser.parse("Turn left onto Nguyễn Trãi", "Arrive\u00a014:30", null, null, "120\u00a0m")
        val a2 = MapsNotificationParser.parse("Turn left onto Nguyễn Trãi", "Arrive\u00a014:31", null, null, "120\u00a0m")
        assertEquals("Nguyễn Trãi", a1.s)
        assertEquals("Nguyễn Trãi", a2.s)
        assertEquals("14:30", a1.e)
        assertEquals("14:31", a2.e)
        var now = 1000L
        val th = Throttle({ now }, z = 1)
        th.offer(a1, now)
        val e1 = th.poll()!!
        now += 1000L
        th.offer(a2, now)
        val e2 = th.poll()!!
        assertEquals(e1.i, e2.i)

        // 2. Dòng tóm tắt chuyến đi ở text không cướp `d` trước shortCriticalText và đi đúng vào `r` + `e`
        val sum = MapsNotificationParser.parse(
            title = "Head toward P. Tố Hữu",
            text = "15 min · 4.2 km · Arrive 14:30",
            subText = null,
            bigText = null,
            shortCriticalText = "80\u00a0m",
        )
        assertEquals(80, sum.d)
        assertEquals(4200, sum.r)
        assertEquals("14:30", sum.e)
        assertEquals("P. Tố Hữu", sum.s)

        // 3. Dấu chấm viết tắt ("Dr.", "P.") trong cụm tên đường đứng đầu KHÔNG làm lộ "Left"/"Trái" thành hướng rẽ
        val dr = MapsNotificationParser.parse("100 m", "On Dr. Left Fork Road", null, null)
        assertEquals(NavState.K_NAVIGATING, dr.k)
        assertEquals(NavState.M_UNKNOWN, dr.m)
        assertEquals(100, dr.d)
        assertEquals("Dr. Left Fork Road", dr.s)

        val drThenTurn = MapsNotificationParser.parse("100 m", "On Dr. Main St., turn right onto B", null, null)
        assertEquals(NavState.M_RIGHT, drThenTurn.m)
        assertEquals("B", drThenTurn.s)

        val vnAbbrev = MapsNotificationParser.parse("100 m", "Trên P. Trái Cây", null, null)
        assertEquals(NavState.M_UNKNOWN, vnAbbrev.m)

        // 4. "Turn left in - 30 m" bị từ chối như khoảng cách âm; "Turn right – 100 m" vẫn hợp lệ
        val negIn = MapsNotificationParser.parse("", "Turn left in - 30 m", null, null)
        assertEquals(NavState.M_LEFT, negIn.m)
        assertEquals(-1, negIn.d)
        val negVi = MapsNotificationParser.parse("", "Rẽ trái sau - 30 m", null, null)
        assertEquals(NavState.M_LEFT, negVi.m)
        assertEquals(-1, negVi.d)

        // 5. Giữ nguyên cờ ambiguous qua ranh giới parser để listener chặn icon fallback
        val amb = MapsNotificationParser.parseDetailed("200 m", "Turn left or right", null, null)
        assertEquals(NavState.M_UNKNOWN, amb.state.m)
        assertTrue(amb.ambiguous)
        val noDir = MapsNotificationParser.parseDetailed("200 m", "Nguyễn Văn Linh", null, null)
        assertEquals(NavState.M_UNKNOWN, noDir.state.m)
        assertEquals(false, noDir.ambiguous)

        // 6. ProgressStyle chỉ trừ progressMax - progress khi có segments và thang > 100 (loại thang % 0..100 mặc định)
        assertEquals(
            -1,
            MapsNotificationParser.extractProgressRemainingM("navigation", "android.app.Notification\$ProgressStyle", false, false, 35, 100),
        )
        assertEquals(
            -1,
            MapsNotificationParser.extractProgressRemainingM("navigation", "android.app.Notification\$ProgressStyle", false, true, 35, 100),
        )
        assertEquals(
            -1,
            MapsNotificationParser.extractProgressRemainingM("progress", "android.app.Notification\$ProgressStyle", false, true, 0, 5000),
        )
        assertEquals(
            9959,
            MapsNotificationParser.extractProgressRemainingM("navigation", "android.app.Notification\$ProgressStyle", false, true, 0, 9959),
        )
    }

    @Test fun round3Regressions() = round3.forEach(::check)

    @Test fun verificationRegressions() = verification.forEach(::check)

    private fun check(c: Case) {
        val st = MapsNotificationParser.parse(c.title, c.text, c.sub, c.big)
        val msg = c.name + " -> " + st
        assertEquals(msg, c.k, st.k)
        assertEquals(msg, c.m, st.m)
        assertEquals(msg, c.d, st.d)
        assertEquals(msg, c.x, st.x)
        c.s?.let { assertEquals(msg, it, st.s) }
        c.e?.let { assertEquals(msg, it, st.e) }
        c.r?.let { assertEquals(msg, it, st.r) }
    }

    @Test fun vietnamese() = vi.forEach(::check)
    @Test fun english() = en.forEach(::check)
    @Test fun edgeCases() = edge.forEach(::check)

    @Test fun atLeast25Cases() = assertTrue(vi.size + en.size + edge.size >= 25)

    @Test fun neverThrowsOnGarbage() {
        val junk = listOf("\u0000", "((((", "9".repeat(500) + " km", "1,2,3,4 m", "99:99", "Rẽ", "left", "vào", "on ")
        for (a in junk) for (b in junk) MapsNotificationParser.parse(a, b, a, b)
    }
}
