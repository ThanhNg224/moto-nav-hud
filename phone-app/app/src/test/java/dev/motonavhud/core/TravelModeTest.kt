package dev.motonavhud.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TravelModeTest {
    private val place = QuickPlace("p1", "Chợ", "Ben Thanh Market", 10.7769, 106.7009, PlaceKind.OTHER)

    @Test
    fun fromIdReturnsMatchingMode() {
        assertEquals(TravelMode.TWO_WHEELER, TravelMode.fromId("two_wheeler"))
        assertEquals(TravelMode.DRIVE, TravelMode.fromId("drive"))
        assertEquals(TravelMode.BICYCLE, TravelMode.fromId("bicycle"))
        assertEquals(TravelMode.WALK, TravelMode.fromId("walk"))
    }

    @Test
    fun fromIdFallsBackToDefaultForUnknownOrNull() {
        assertEquals(TravelMode.TWO_WHEELER, TravelMode.fromId(null))
        assertEquals(TravelMode.TWO_WHEELER, TravelMode.fromId(""))
        assertEquals(TravelMode.TWO_WHEELER, TravelMode.fromId("transit"))
        assertEquals(TravelMode.DEFAULT, TravelMode.fromId(null))
    }

    @Test
    fun urlModeLetters() {
        assertEquals("l", TravelMode.TWO_WHEELER.urlMode)
        assertEquals("d", TravelMode.DRIVE.urlMode)
        assertEquals("b", TravelMode.BICYCLE.urlMode)
        assertEquals("w", TravelMode.WALK.urlMode)
    }

    @Test
    fun navigationUriForEachMode() {
        assertEquals("google.navigation:q=10.776900,106.700900&mode=l", MapsUri.navigationUri(place, TravelMode.TWO_WHEELER))
        assertEquals("google.navigation:q=10.776900,106.700900&mode=d", MapsUri.navigationUri(place, TravelMode.DRIVE))
        assertEquals("google.navigation:q=10.776900,106.700900&mode=b", MapsUri.navigationUri(place, TravelMode.BICYCLE))
        assertEquals("google.navigation:q=10.776900,106.700900&mode=w", MapsUri.navigationUri(place, TravelMode.WALK))
    }

    @Test
    fun webUriForEachMode() {
        val prefix = "https://www.google.com/maps/dir/?api=1&destination=10.776900%2C106.700900&travelmode="
        assertEquals(prefix + "two-wheeler", MapsUri.webUri(place, TravelMode.TWO_WHEELER))
        assertEquals(prefix + "driving", MapsUri.webUri(place, TravelMode.DRIVE))
        assertEquals(prefix + "bicycling", MapsUri.webUri(place, TravelMode.BICYCLE))
        assertEquals(prefix + "walking", MapsUri.webUri(place, TravelMode.WALK))
    }
}
