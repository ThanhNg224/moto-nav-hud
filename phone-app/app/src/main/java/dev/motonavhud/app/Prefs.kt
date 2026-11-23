package dev.motonavhud.app

import android.content.Context

object Prefs {
    private const val FILE = "prefs"
    private const val KEY_CAPTURE = "capture"
    private const val KEY_TOKEN = "token"
    const val DEFAULT_TOKEN = "MOTONAV1"

    private val tokenRx = Regex("^[A-Za-z0-9]{1,8}$")

    fun isCapture(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_CAPTURE, false)

    fun setCapture(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_CAPTURE, on).apply()
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
