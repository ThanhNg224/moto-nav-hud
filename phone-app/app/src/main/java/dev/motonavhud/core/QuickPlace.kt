package dev.motonavhud.core

import java.net.URLEncoder
import java.util.Locale

/** Số địa điểm nhanh tối đa được lưu và hiển thị. */
const val MAX_QUICK_PLACES = 3

enum class PlaceKind { HOME, WORK, OTHER }

/** Địa điểm nhanh; [lat]/[lng] có thể thiếu, khi đó dẫn đường bằng địa chỉ. */
data class QuickPlace(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val kind: PlaceKind,
) {
    /** true khi cả lat và lng đều có và nằm trong khoảng hợp lệ. */
    val hasCoordinates: Boolean
        get() = lat != null && lng != null && MapsUri.validCoordinates(lat, lng)
}

/** Tạo URI mở Google Maps dẫn đường theo phương tiện [TravelMode] (mặc định xe hai bánh). */
object MapsUri {
    fun validCoordinates(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

    /** google.navigation:q=<lat>,<lng>&mode=<urlMode> khi có tọa độ hợp lệ, ngược lại google.navigation:q=<địa chỉ đã mã hóa>&mode=<urlMode> */
    fun navigationUri(p: QuickPlace, mode: TravelMode = TravelMode.DEFAULT): String {
        val c = coords(p)
        val q = if (c != null) "${fmt(c.first)},${fmt(c.second)}" else encodeAddress(p.address)
        return "google.navigation:q=$q&mode=${mode.urlMode}"
    }

    /** https://www.google.com/maps/dir/?api=1&destination=<đích mã hóa>&travelmode=<webMode> : dự phòng khi không có app Maps. */
    fun webUri(p: QuickPlace, mode: TravelMode = TravelMode.DEFAULT): String {
        val c = coords(p)
        val dest = if (c != null) encodeAddress("${fmt(c.first)},${fmt(c.second)}") else encodeAddress(p.address)
        return "https://www.google.com/maps/dir/?api=1&destination=$dest&travelmode=${mode.webMode}"
    }

    private fun coords(p: QuickPlace): Pair<Double, Double>? {
        val la = p.lat
        val lo = p.lng
        return if (la != null && lo != null && validCoordinates(la, lo)) la to lo else null
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun encodeAddress(s: String): String = URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")
}
