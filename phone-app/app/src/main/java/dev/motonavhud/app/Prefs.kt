package dev.motonavhud.app

import android.content.Context
import dev.motonavhud.core.TravelMode

object Prefs {
    private const val FILE = "prefs"
    private const val KEY_CAPTURE = "capture"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTO_STOP_MIN = "auto_stop_min"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_OPEN_MAPS_ON_START = "open_maps_on_start"
    private const val KEY_TRAVEL_MODE = "travel_mode"
    const val DEFAULT_TOKEN = "MOTONAV1"
    const val DEFAULT_AUTO_STOP_MIN = 10
    val AUTO_STOP_OPTIONS = intArrayOf(0, 5, 10, 15)

    private val tokenRx = Regex("^[A-Za-z0-9]{1,8}$")

    fun isAutoStart(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_START, true)

    fun setAutoStart(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_START, on).apply()
    }

    /** Bấm bắt đầu (nút chính, ô Quick Settings) thì mở luôn Google Maps. */
    fun isOpenMapsOnStart(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_OPEN_MAPS_ON_START, true)

    fun setOpenMapsOnStart(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_OPEN_MAPS_ON_START, on).apply()
    }

    /** Phương tiện dẫn đường Google Maps; mặc định xe hai bánh. */
    fun travelMode(ctx: Context): TravelMode =
        TravelMode.fromId(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TRAVEL_MODE, null))

    fun setTravelMode(ctx: Context, mode: TravelMode) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_TRAVEL_MODE, mode.id).apply()
    }

    fun isCapture(ctx: Context) =ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_CAPTURE, false)

    fun setCapture(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_CAPTURE, on).apply()
    }

    /** Tự dừng BLE service sau N phút rảnh (k=0, không Demo). 0 = tắt. */
    fun autoStopMinutes(ctx: Context): Int {
        val v = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_AUTO_STOP_MIN, DEFAULT_AUTO_STOP_MIN)
        return if (v in AUTO_STOP_OPTIONS) v else DEFAULT_AUTO_STOP_MIN
    }

    fun setAutoStopMinutes(ctx: Context, minutes: Int) {
        if (minutes !in AUTO_STOP_OPTIONS) return
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_AUTO_STOP_MIN, minutes).apply()
    }

    /** Mã ghép `p` (docs/protocol.md): 1-8 ký tự [A-Za-z0-9], phải trùng watch-app/src/common/config.js. */
    fun token(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)?.takeIf { tokenRx.matches(it) } ?: DEFAULT_TOKEN

    /** Trả false nếu mã không hợp lệ (không lưu). */
    fun setToken(ctx: Context, value: String): Boolean {
        if (!tokenRx.matches(value)) return false
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, value).apply()
        return true
    }
}
