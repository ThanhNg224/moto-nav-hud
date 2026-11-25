package dev.motonavhud.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Mã hóa danh sách địa điểm nhanh thành JSON (xem [decode] để đọc lại). */
object PlacesCodec {
    fun encode(list: List<QuickPlace>): String {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("address", p.address)
                .put("kind", p.kind.name)
            // Chỉ ghi tọa độ hợp lệ: NaN không đưa được vào JSONObject, và decode cũng bỏ tọa độ không hợp lệ.
            if (p.hasCoordinates) o.put("lat", p.lat).put("lng", p.lng)
            arr.put(o)
        }
        return arr.toString()
    }

    /** Không bao giờ ném lỗi: JSON hỏng, rỗng hoặc không phải mảng -> emptyList(); bỏ mục thiếu id/name; tối đa [MAX_QUICK_PLACES]. */
    fun decode(json: String?): List<QuickPlace> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return emptyList()
        }
        val out = ArrayList<QuickPlace>()
        for (i in 0 until arr.length()) {
            if (out.size >= MAX_QUICK_PLACES) break
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: continue
            val address = o.optString("address")
            val lat = o.optDouble("lat", Double.NaN)
            val lng = o.optDouble("lng", Double.NaN)
            val validCoords = MapsUri.validCoordinates(lat, lng)
            if (address.isBlank() && !validCoords) continue // không có đích nào dùng được
            val kind = PlaceKind.values().firstOrNull { it.name == o.optString("kind") } ?: PlaceKind.OTHER
            out.add(
                QuickPlace(
                    id = id,
                    name = name,
                    address = address,
                    lat = if (validCoords) lat else null,
                    lng = if (validCoords) lng else null,
                    kind = kind,
                ),
            )
        }
        return out
    }
}
