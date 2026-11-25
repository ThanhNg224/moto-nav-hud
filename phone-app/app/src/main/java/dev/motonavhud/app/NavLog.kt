package dev.motonavhud.app

import android.util.Log
import dev.motonavhud.app.BuildConfig

/** Logcat duy nhất, tag "MotoNav", sự kiện ngắn dạng `event key=value`. Không bao giờ log nguyên văn notification. */
object NavLog {
    private const val TAG = "MotoNav"

    fun i(event: String) {
        Log.i(TAG, event)
        EventLog.log(event)
    }

    /** Sự kiện dài dòng: chỉ ở bản debug. */
    fun v(event: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, event)
            EventLog.log(event)
        }
    }

    /** Chỉ 5 ký tự cuối của địa chỉ MAC. */
    fun addr(a: String) = a.takeLast(5)
}
