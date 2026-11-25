package dev.motonavhud.core

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPlaceTest {
    private fun place(lat: Double?, lng: Double?, address: String = "Ben Thanh Market") =
        QuickPlace("p1", "Chợ", address, lat, lng, PlaceKind.OTHER)

    /** Lấy phần q= của google.navigation và giải mã lại để so với địa chỉ gốc. */
    private fun decodedQ(uri: String): String {
        val q = uri.substringAfter("q=").substringBefore("&mode=l")
        return URLDecoder.decode(q, "UTF-8")
    }

    @Test
    fun navigationUriPositiveCoordinates() {
        assertEquals("google.navigation:q=10.776900,106.700900&mode=l", MapsUri.navigationUri(place(10.7769, 106.7009)))
    }

    @Test
    fun navigationUriNegativeCoordinates() {
        assertEquals("google.navigation:q=-33.868820,151.209346&mode=l", MapsUri.navigationUri(place(-33.8688197, 151.2093456)))
    }

    @Test
    fun navigationUriRoundsToSixDecimals() {
        assertEquals("google.navigation:q=10.776912,106.700988&mode=l", MapsUri.navigationUri(place(10.77691234567, 106.70098765)))
    }

    @Test
    fun navigationUriEncodesVietnameseAddress() {
        val addr = "  Cộng Cà Phê, Nguyễn Huệ  "
        val uri = MapsUri.navigationUri(place(null, null, addr))
        assertTrue(uri.startsWith("google.navigation:q="))
        assertTrue(uri.endsWith("&mode=l"))
        val q = uri.substringAfter("q=").substringBefore("&mode=l")
        assertTrue("không được còn khoảng trắng hoặc dấu +", !q.contains(' ') && !q.contains('+'))
        assertTrue(q.contains("%20"))
        assertEquals("Cộng Cà Phê, Nguyễn Huệ", decodedQ(uri))
    }

    @Test
    fun invalidCoordinatesFallBackToAddress() {
        val addr = "Số 1 Lê Lợi"
        val cases = listOf(
            place(91.0, 106.7, addr),
            place(Double.NaN, 106.7, addr),
            place(10.7, null, addr),
            place(null, 106.7, addr),
            place(10.7, 181.0, addr),
        )
        for (p in cases) {
            assertFalse(p.hasCoordinates)
            val uri = MapsUri.navigationUri(p)
            assertEquals(addr, decodedQ(uri))
            assertFalse(uri.contains("106.7"))
        }
    }

    @Test
    fun emptyAddressWithoutCoordinatesDoesNotCrash() {
        assertEquals("google.navigation:q=&mode=l", MapsUri.navigationUri(place(null, null, "")))
        assertEquals("google.navigation:q=&mode=l", MapsUri.navigationUri(place(null, null, "   ")))
    }

    @Test
    fun webUriWithCoordinates() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=10.776900%2C106.700900&travelmode=two-wheeler",
            MapsUri.webUri(place(10.7769, 106.7009)),
        )
    }

    @Test
    fun navigationUriDefaultsToTwoWheeler() {
        assertEquals(
            MapsUri.navigationUri(place(10.7769, 106.7009), TravelMode.TWO_WHEELER),
            MapsUri.navigationUri(place(10.7769, 106.7009)),
        )
    }

    @Test
    fun navigationUriUsesSelectedMode() {
        assertEquals("google.navigation:q=10.776900,106.700900&mode=d", MapsUri.navigationUri(place(10.7769, 106.7009), TravelMode.DRIVE))
    }

    @Test
    fun webUriWithAddress() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Ben%20Thanh%20Market&travelmode=two-wheeler",
            MapsUri.webUri(place(null, null, "Ben Thanh Market")),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Ben%20Thanh%20Market&travelmode=two-wheeler",
            MapsUri.webUri(place(91.0, 106.7, "Ben Thanh Market")),
        )
    }

    @Test
    fun validCoordinatesEdgeValues() {
        assertTrue(MapsUri.validCoordinates(90.0, 180.0))
        assertTrue(MapsUri.validCoordinates(-90.0, -180.0))
        assertTrue(MapsUri.validCoordinates(0.0, 0.0))
        assertFalse(MapsUri.validCoordinates(90.0001, 0.0))
        assertFalse(MapsUri.validCoordinates(-90.0001, 0.0))
        assertFalse(MapsUri.validCoordinates(0.0, 180.0001))
        assertFalse(MapsUri.validCoordinates(0.0, -180.0001))
        assertFalse(MapsUri.validCoordinates(Double.NaN, 0.0))
        assertFalse(MapsUri.validCoordinates(0.0, Double.POSITIVE_INFINITY))
    }
}
