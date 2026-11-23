package dev.motonavhud.app

import android.content.Context

object Prefs {
    private const val FILE = "prefs"
    private const val KEY_CAPTURE = "capture"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTO_STOP_MIN = "auto_stop_min"
    const val DEFAULT_TOKEN = "MOTONAV1"
    const val DEFAULT_AUTO_STOP_MIN = 10
    val AUTO_STOP_OPTIONS = intArrayOf(0, 5, 10, 15)

    private val tokenRx = Regex("^[A-Za-z0-9]{1,8}$")

    fun isCapture(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_CAPTURE, false)

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
