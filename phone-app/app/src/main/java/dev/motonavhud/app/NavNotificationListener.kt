package dev.motonavhud.app

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.motonavhud.core.ManeuverIconClassifier
import dev.motonavhud.core.MapsNotificationParser
import dev.motonavhud.core.NavState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class NavNotificationListener : NotificationListenerService() {
    /** Key của notification Maps đang là nguồn; chỉ khi chính nó biến mất mới xóa tuyến. */
    private var selectedKey: String? = null
    private var selectedVersion: Int = -1

    override fun onListenerConnected() {
        instance = this
        NavBus.listenerConnected = true
        NavLog.i("listener connected")
        NavBus.changed()
        // Đồng bộ các notification đã đăng trước khi kết nối (cấp quyền/rebind giữa chuyến đi), tuổi thật theo postTime.
        val active = try { activeNotifications?.toList().orEmpty() } catch (_: SecurityException) { emptyList() }
        active.filter { it.packageName == MAPS_PACKAGE }.sortedBy { it.postTime }.forEach { handle(it, seed = true) }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        NavBus.listenerConnected = false
        NavLog.i("listener disconnected")
        synchronized(this) {
            selectedKey = null
            selectedVersion = -1
        }
        NavBus.publishMaps(NavState.IDLE)
        NavBus.changed()
        requestRebind(ComponentName(this, NavNotificationListener::class.java))
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE) return
        handle(sbn, seed = false)
        if (Prefs.isCapture(this)) NotificationCapture.enqueue(this, sbn) // sau khi đã publish tuyến
    }

    @Synchronized
    private fun handle(sbn: StatusBarNotification, seed: Boolean) {
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) == 0) {
            // Nguồn đang chọn bỏ cờ ongoing (cập nhật tại chỗ): coi như biến mất.
            if (sbn.key == selectedKey) {
                selectedKey = null
                selectedVersion = -1
                NavLog.i("listener source cleared (not ongoing)")
                NavBus.publishMaps(NavState.IDLE)
            }
            return
        }
        val x = n.extras
        // Google Maps Android 16/17 Live Updates (Notification$ProgressStyle) đặt quãng đường còn lại ở progressMax - progress (mét).
        val template = x.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val progressMax = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = x.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val progressRemainingM = if (template.contains("ProgressStyle") && !indeterminate && progressMax > 0 && progress in 0..progressMax) {
            progressMax - progress
        } else {
            -1
        }
        val parsed = MapsNotificationParser.parse(
            x.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            x.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            x.getCharSequence("android.shortCriticalText")?.toString(),
            progressRemainingM,
        )
        // Dự phòng bảo thủ: chỉ đọc largeIcon của notification category=navigation khi phần chữ chưa có hướng rẽ.
        val state = if (parsed.k == NavState.K_NAVIGATING && parsed.m == NavState.M_UNKNOWN && n.category == Notification.CATEGORY_NAVIGATION) {
            val fromIcon = classifyLargeIcon(n.getLargeIcon())
            if (fromIcon != NavState.M_UNKNOWN) parsed.copy(m = fromIcon) else parsed
        } else {
            parsed
        }
        val key = sbn.key
        if (key != selectedKey) {
            // Notification ongoing khác (không phải dẫn đường) không được chiếm nguồn hay xóa tuyến.
            if (state.k == NavState.K_IDLE) return
            selectedKey = key
            NavLog.i("listener selected key=${key.hashCode()}")
        }
        NavLog.v("parse k=${state.k} m=${state.m} d=${state.d} street.len=${state.s.length} seed=$seed")
        val ageMs = if (seed) (System.currentTimeMillis() - sbn.postTime).coerceAtLeast(0) else 0L
        selectedVersion = NavBus.publishMaps(state, SystemClock.elapsedRealtime() - ageMs)
    }

    @Synchronized
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE || sbn.key != selectedKey) return
        selectedKey = null
        selectedVersion = -1
        NavLog.i("listener source removed")
        NavBus.publishMaps(NavState.IDLE)
    }

    /** Chuyển largeIcon của Google Maps thành lưới nhị phân 16x16 để phân loại hướng dự phòng. */
    private fun classifyLargeIcon(icon: Icon?): Int = try {
        val d = icon?.loadDrawable(this) ?: return NavState.M_UNKNOWN
        val g = ManeuverIconClassifier.GRID
        val bmp = Bitmap.createBitmap(g, g, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, g, g)
        d.draw(Canvas(bmp))
        val mask = BooleanArray(g * g)
        for (y in 0 until g) {
            for (x in 0 until g) {
                val px = bmp.getPixel(x, y)
                mask[y * g + x] = Color.alpha(px) >= 120
            }
        }
        bmp.recycle()
        ManeuverIconClassifier.classify(mask)
    } catch (_: Exception) {
        NavState.M_UNKNOWN
    }

    /** Kiểm tra notification Maps đang chọn có còn tồn tại trên hệ thống không (chạy nền, tránh kẹt UI thread). */
    private fun verifySelectedAlive() {
        val key: String
        val ver: Int
        synchronized(this) {
            key = selectedKey ?: return
            ver = selectedVersion
        }
        val alive: Boolean? = try {
            getActiveNotifications(arrayOf(key))?.any {
                it.key == key && it.packageName == MAPS_PACKAGE && (it.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            }
        } catch (_: Exception) {
            null
        }
        if (alive == null) return // lỗi IPC: không làm mới, để cơ chế hết hạn an toàn tự xử lý
        synchronized(this) {
            if (selectedKey != key || selectedVersion != ver) return
            if (alive) {
                NavBus.touchMaps(ver)?.let { selectedVersion = it }
            } else {
                selectedKey = null
                selectedVersion = -1
                NavLog.i("listener source gone on verify")
                NavBus.publishMaps(NavState.IDLE)
            }
        }
    }

    companion object {
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
        @Volatile private var instance: NavNotificationListener? = null
        private val verifyExec = Executors.newSingleThreadExecutor()
        private val verifyBusy = AtomicBoolean(false)

        /** Yêu cầu kiểm tra bất đồng bộ xem notification Maps đang chọn còn sống hay không. */
        fun verifySourceAliveAsync() {
            val l = instance ?: return
            if (!verifyBusy.compareAndSet(false, true)) return
            verifyExec.execute {
                try { l.verifySelectedAlive() } finally { verifyBusy.set(false) }
            }
        }
    }
}
