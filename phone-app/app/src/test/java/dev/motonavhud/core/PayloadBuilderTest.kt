package dev.motonavhud.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadBuilderTest {
    private val p = "MOTONAV1"

    private fun res(st: NavState, mtuLimit: Int = 180, z: Int = 48211, q: Int = 42, a: Int = 900) =
        PayloadBuilder.build(st, z = z, q = q, i = 7, a = a, p = p, mtuLimit = mtuLimit)

    private fun build(st: NavState, mtuLimit: Int = 180): ByteArray = (res(st, mtuLimit) as PayloadBuilder.Result.Ok).bytes
    private fun json(b: ByteArray) = JSONObject(String(b, Charsets.UTF_8))

    @Test fun matchesProtocolExample() {
        val b = build(NavState(1, 3, 0, 180, "Nguyễn Văn Linh", "12:45", 4200))
        assertEquals(
            """{"v":1,"z":48211,"q":42,"i":7,"a":900,"k":1,"m":3,"x":0,"d":180,"p":"MOTONAV1","s":"Nguyễn Văn Linh","e":"12:45","r":4200}""",
            String(b, Charsets.UTF_8),
        )
    }

    @Test fun jsonIsValidAndFieldsRoundTrip() {
        val o = json(build(NavState(2, 9, 3, -1, "A", "", -1)))
        assertEquals(1, o.getInt("v")); assertEquals(48211, o.getInt("z")); assertEquals(42, o.getInt("q"))
        assertEquals(7, o.getInt("i")); assertEquals(900, o.getInt("a")); assertEquals(2, o.getInt("k"))
        assertEquals(9, o.getInt("m")); assertEquals(3, o.getInt("x")); assertEquals(-1, o.getInt("d"))
        assertEquals("A", o.getString("s")); assertEquals(-1, o.getInt("r")); assertEquals(p, o.getString("p"))
    }

    @Test fun longMultibyteStreetFitsCapAndStaysValid() {
        val st = NavState(1, 3, 0, 100, "Đường ".repeat(60), "12:45", 1000)
        val b = build(st)
        assertTrue(b.size <= 180)
        val s = json(b).getString("s")
        assertTrue(s.isNotEmpty())
        assertTrue(("Đường ".repeat(60)).startsWith(s))
    }

    @Test fun neverSplitsSurrogatePairOrCombiningGrapheme() {
        val emoji = "🚀"
        val s = json(build(NavState(1, 3, 0, 100, emoji.repeat(100), "", -1))).getString("s")
        assertTrue(s.isNotEmpty() && s.length % 2 == 0)
        val s2 = json(build(NavState(1, 3, 0, 100, "é".repeat(100), "", -1))).getString("s")
        assertEquals(0, s2.length % 2)
    }

    @Test fun quotesAndBackslashesCountAfterEscaping() {
        val b = build(NavState(1, 3, 0, 100, "\"\\".repeat(100), "12:45", 5))
        assertTrue(b.size <= 180)
        assertTrue(json(b).getString("s").all { it == '"' || it == '\\' })
    }

    @Test fun dropsEBeforeShorteningS() {
        val base = NavState(1, 3, 0, 100, "x".repeat(60), "12:45", 5)
        val full = build(base).size
        val o = json(build(base, mtuLimit = full - 1))
        assertFalse(o.has("e"))
        assertEquals(60, o.getString("s").length)
    }

    @Test fun dropOrderIsEThenSThenR() {
        val st = NavState(1, 3, 0, 100, "Nguyễn Văn Linh", "12:45", 4200)
        val full = build(st, 500).size
        val eBytes = ""","e":"12:45"""".length
        val sBytes = (""","s":"""" + "Nguyễn Văn Linh" + """"""").toByteArray().size
        val rBytes = ""","r":4200""".length
        // Bỏ e trước.
        val noE = json((res(st, full - 1) as PayloadBuilder.Result.Ok).bytes)
        assertFalse(noE.has("e")); assertTrue(noE.has("s")); assertTrue(noE.has("r"))
        // Rồi s bị rút/bỏ, r còn.
        val noS = json((res(st, full - eBytes - sBytes) as PayloadBuilder.Result.Ok).bytes)
        assertFalse(noS.has("e")); assertFalse(noS.has("s")); assertEquals(4200, noS.getInt("r"))
        // Cuối cùng mới bỏ r; lõi còn nguyên.
        val core = json((res(st, full - eBytes - sBytes - rBytes) as PayloadBuilder.Result.Ok).bytes)
        assertFalse(core.has("r")); assertTrue(core.has("p")); assertTrue(core.has("d"))
    }

    @Test fun coreWithTenDigitSessionFitsAtMtu107() {
        // Giá trị lõi lớn nhất thực tế: z 10 chữ số, q/i/a lớn, p 8 ký tự.
        val st = NavState(2, 13, 6, 20000, "Đại lộ Võ Nguyên Giáp", "12:45", 1234567)
        val r = PayloadBuilder.build(st, z = 2147483647, q = 2147483647, i = 99999, a = 999999, p = "MOTONAV1", mtuLimit = 104)
        assertTrue(r is PayloadBuilder.Result.Ok)
        val b = (r as PayloadBuilder.Result.Ok).bytes
        assertTrue(b.size <= 104)
        val o = json(b)
        for (key in listOf("v", "z", "q", "i", "a", "k", "m", "x", "d", "p")) assertTrue(o.has(key))
    }

    @Test fun unfittableCoreIsExplicitAndNeverExceedsCap() {
        val st = NavState(1, 3, 0, 100, "abc", "12:45", 5)
        for (limit in listOf(20, 50, 60, 70)) {
            val r = res(st, limit, z = 2147483647, q = 2147483647)
            assertTrue("limit $limit", r is PayloadBuilder.Result.Unfittable)
            r as PayloadBuilder.Result.Unfittable
            assertTrue(r.coreBytes > limit)
        }
    }

    @Test fun okResultNeverExceedsCap() {
        val st = NavState(1, 13, 0, 2000, "Đại lộ Võ Nguyên Giáp ".repeat(5), "12:45", 123456)
        for (limit in 60..200) {
            val r = res(st, limit, z = 2147483647, q = 123456789)
            if (r is PayloadBuilder.Result.Ok) assertTrue(r.bytes.size <= minOf(180, limit))
        }
    }

    @Test fun capIs180EvenWithLargeMtu() {
        assertTrue(build(NavState(1, 3, 0, 100, "ê".repeat(200), "12:45", 1), 512).size <= 180)
    }

    @Test fun controlCharsAreEscaped() {
        assertEquals("a\nb\tc", json(build(NavState(1, 3, 0, 1, "a\nb\tc", "", -1))).getString("s"))
    }
}
