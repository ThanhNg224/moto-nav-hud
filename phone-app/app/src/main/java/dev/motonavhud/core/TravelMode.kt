package dev.motonavhud.core

/** Phương tiện dẫn đường trong Google Maps. [urlMode] cho google.navigation: (d, l, b, w); [webMode] cho URL https. */
enum class TravelMode(val id: String, val urlMode: String, val webMode: String) {
    TWO_WHEELER("two_wheeler", "l", "two-wheeler"),
    DRIVE("drive", "d", "driving"),
    BICYCLE("bicycle", "b", "bicycling"),
    WALK("walk", "w", "walking");

    companion object {
        val DEFAULT = TWO_WHEELER

        fun fromId(id: String?): TravelMode = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
