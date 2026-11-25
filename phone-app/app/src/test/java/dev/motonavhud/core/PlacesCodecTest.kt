package dev.motonavhud.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesCodecTest {
    private fun place(id: String, lat: Double? = null, lng: Double? = null, kind: PlaceKind = PlaceKind.OTHER) =
        QuickPlace(id, "Tên $id", "Địa chỉ $id, Quận 1", lat, lng, kind)

    @Test
    fun roundTripWithAndWithoutCoordinates() {
        val list = listOf(
            place("a", 10.7769, 106.7009, PlaceKind.HOME),
            place("b", null, null, PlaceKind.WORK),
            place("c", -33.868820, 151.209346, PlaceKind.OTHER),
        )
        assertEquals(list, PlacesCodec.decode(PlacesCodec.encode(list)))
    }

    @Test
    fun emptyListRoundTrip() {
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode(PlacesCodec.encode(emptyList())))
    }

    @Test
    fun nullBlankGarbageOrNonArrayReturnsEmpty() {
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode(null))
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode(""))
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode("   "))
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode("not json"))
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode("[{\"id\":"))
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode("{\"id\":\"x\",\"name\":\"y\"}"))
        assertEquals(emptyList<QuickPlace>(), PlacesCodec.decode("\"chuỗi\""))
    }

    @Test
    fun skipsInvalidItems() {
        val json = """
            [
              {"name":"Thiếu id","address":"x"},
              {"id":"  ","name":"Id trắng"},
              {"id":"noname","address":"x"},
              {"id":"blank","name":"   "},
              5,
              {"id":"ok","name":"Hợp lệ","address":"Quận 3","kind":"WORK"}
            ]
        """.trimIndent()
        val result = PlacesCodec.decode(json)
        assertEquals(1, result.size)
        assertEquals(QuickPlace("ok", "Hợp lệ", "Quận 3", null, null, PlaceKind.WORK), result[0])
    }

    @Test
    fun capsAtMaxQuickPlaces() {
        val list = (1..5).map { place("p$it") }
        val result = PlacesCodec.decode(PlacesCodec.encode(list))
        assertEquals(MAX_QUICK_PLACES, result.size)
        assertEquals(listOf("p1", "p2", "p3"), result.map { it.id })
    }

    @Test
    fun unknownOrMissingKindBecomesOther() {
        val json = """
            [
              {"id":"1","name":"Phòng gym","address":"A","kind":"GYM"},
              {"id":"2","name":"Không có kind","address":"B"},
              {"id":"3","name":"Nhà","address":"C","kind":"HOME"}
            ]
        """.trimIndent()
        val kinds = PlacesCodec.decode(json).map { it.kind }
        assertEquals(listOf(PlaceKind.OTHER, PlaceKind.OTHER, PlaceKind.HOME), kinds)
    }

    @Test
    fun halfCoordinatesAreDropped() {
        val result = PlacesCodec.decode("""[{"id":"1","name":"N","address":"A","lat":10.5}]""")
        assertEquals(1, result.size)
        assertNull(result[0].lat)
        assertNull(result[0].lng)
        assertTrue(!result[0].hasCoordinates)

        val onlyLng = PlacesCodec.decode("""[{"id":"1","name":"N","address":"A","lng":106.7}]""")
        assertNull(onlyLng[0].lat)
        assertNull(onlyLng[0].lng)
    }

    @Test
    fun outOfRangeCoordinatesAreDropped() {
        val result = PlacesCodec.decode("""[{"id":"1","name":"N","address":"A","lat":95,"lng":106}]""")
        assertNull(result[0].lat)
        assertNull(result[0].lng)
    }

    @Test
    fun missingAddressBecomesEmpty() {
        val result = PlacesCodec.decode("""[{"id":"1","name":"N","lat":10.5,"lng":106.7}]""")
        assertEquals("", result[0].address)
    }

    @Test
    fun skipsPlaceWithNoUsableDestination() {
        val result = PlacesCodec.decode("""[{"id":"1","name":"N"},{"id":"2","name":"M","address":"  "},{"id":"3","name":"K","address":"A"}]""")
        assertEquals(listOf("3"), result.map { it.id })
    }

    @Test
    fun encodeOmitsCoordinateKeysWhenAbsent() {
        val json = PlacesCodec.encode(listOf(place("a")))
        assertTrue(!json.contains("\"lat\""))
        assertTrue(!json.contains("\"lng\""))
        assertTrue(json.contains("\"kind\":\"OTHER\""))
    }
}
