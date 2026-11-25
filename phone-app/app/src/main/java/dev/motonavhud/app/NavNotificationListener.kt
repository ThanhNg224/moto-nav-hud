package dev.motonavhud.app

import android.Manifest
import android.app.Notification
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.motonavhud.core.ManeuverIconClassifier
import dev.motonavhud.core.MapsNotificationParser
import dev.motonavhud.core.NavState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class NavNotificationListener : NotificationListenerService() {
    /** Key & postTime của notification Maps đang là nguồn; chỉ khi chính nó biến mất mới xóa tuyến. */
    private var selectedKey: String? = null
    private var selectedVersion: Int = -1
    private var selectedPostTime: Long = -1L

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
            selectedPostTime = -1L
        }
        NavBus.publishMaps(NavState.IDLE)
        NavBus.autoStartGate.onMapsIdle(SystemClock.elapsedRealtime())
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
                selectedPostTime = -1L
                NavLog.i("listener source cleared (not ongoing)")
                NavBus.publishMaps(NavState.IDLE)
                NavBus.autoStartGate.onMapsIdle(SystemClock.elapsedRealtime())
            }
            return
        }
        val x = n.extras
        // Chỉ trừ progressMax - progress làm mét còn lại khi có bằng chứng ProgressStyle có segments theo mét (>100).
        val template = x.getString(Notification.EXTRA_TEMPLATE)
        val progressMax = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = x.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        @Suppress("DEPRECATION")
        val segments = x.get("android.progressSegments") as? Collection<*>
        val progressRemainingM = MapsNotificationParser.extractProgressRemainingM(
            category = n.category,
            template = template,
            indeterminate = indeterminate,
            hasSegments = !segments.isNullOrEmpty(),
            progress = progress,
            progressMax = progressMax,
        )
        val detailed = MapsNotificationParser.parseDetailed(
            x.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            x.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            x.getCharSequence("android.shortCriticalText")?.toString(),
            progressRemainingM,
        )
        val parsed = detailed.state
        // Dự phòng bảo thủ: chỉ dùng largeIcon khi văn bản KHÔNG nói hướng (không mơ hồ trái/phải) và category=navigation.
        val state = if (!detailed.ambiguous && parsed.k == NavState.K_NAVIGATING && parsed.m == NavState.M_UNKNOWN && n.category == Notification.CATEGORY_NAVIGATION) {
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
        selectedPostTime = sbn.postTime
        NavLog.v("parse k=${state.k} m=${state.m} d=${state.d} street.len=${state.s.length} seed=$seed")
        val now = SystemClock.elapsedRealtime()
        val ageMs = if (seed) (System.currentTimeMillis() - sbn.postTime).coerceAtLeast(0L) else 0L
        selectedVersion = NavBus.publishMaps(state, now - ageMs, sbn.postTime, aliveAtMs = now)
        if (state.k == NavState.K_NAVIGATING) {
            NavBus.autoStartGate.onMapsActive(now)
            if (!seed || ageMs < 10_000L) tryAutoStart()
        } else if (state.k == NavState.K_IDLE) {
            NavBus.autoStartGate.onMapsIdle(now)
        }
    }

    private fun tryAutoStart() {
        val btPermOk = Build.VERSION.SDK_INT < 31 || (
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            )
        val btOn = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        if (!NavBus.autoStartGate.tryAcquireAutoStart(Prefs.isAutoStart(this), NavBus.serviceRunning, btPermOk && btOn)) return
        try {
            NavForegroundService.start(this)
            NavLog.i("auto-start triggered")
        } catch (e: Exception) {
            NavBus.autoStartGate.onAutoStartBlocked()
            NavLog.i("auto-start blocked: ${e.javaClass.simpleName}")
            NavBus.warning = "Android chặn tự bật nền — bấm Bắt đầu hoặc dùng ô Quick Settings"
            NavBus.changed()
        }
    }

    @Synchronized
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE || sbn.key != selectedKey) return
        selectedKey = null
        selectedVersion = -1
        selectedPostTime = -1L
        NavLog.i("listener source removed")
        NavBus.publishMaps(NavState.IDLE)
        NavBus.autoStartGate.onMapsIdle(SystemClock.elapsedRealtime())
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

    /**
     * Tách riêng "thông báo còn hiện diện" khỏi "dữ liệu rẽ còn mới" (chạy nền, tránh kẹt UI thread):
     * - Nếu thông báo biến mất hoặc hết cờ ongoing -> xóa về IDLE ngay.
     * - Nếu có bản đăng cùng key mới hơn (postTime tăng, bị lỡ callback) -> đọc bù với tuổi thật theo postTime.
     * - Nếu cùng postTime cũ -> KHÔNG làm mới `a`, để cơ chế hết hạn an toàn tự ẩn mũi tên nếu Maps bị treo.
     */
    private fun verifySelectedAlive() {
        val key: String
        val ver: Int
        val prevPostTime: Long
        synchronized(this) {
            key = selectedKey ?: return
            ver = selectedVersion
            prevPostTime = selectedPostTime
        }
        val active: StatusBarNotification? = try {
            getActiveNotifications(arrayOf(key))?.firstOrNull {
                it.key == key && it.packageName == MAPS_PACKAGE && (it.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            }
        } catch (_: Exception) {
            return // lỗi IPC: không làm mới, để cơ chế hết hạn an toàn tự xử lý
        }
        if (active == null) {
            synchronized(this) {
                if (selectedKey != key || selectedVersion != ver) return
                selectedKey = null
                selectedVersion = -1
                selectedPostTime = -1L
                NavLog.i("listener source gone on verify")
                NavBus.publishMaps(NavState.IDLE)
                NavBus.autoStartGate.onMapsIdle(SystemClock.elapsedRealtime())
            }
            return
        }
        synchronized(this) {
            if (selectedKey != key || selectedVersion != ver) return // nguồn đã đổi trong lúc kiểm tra: xác nhận này không còn đúng
            // còn hiện diện: chỉ gia hạn k=1 qua khoảng im, không làm mới `a`
            NavBus.mapsAlive = NavBus.MapsAlive(ver, SystemClock.elapsedRealtime())
        }
        if (active.postTime > prevPostTime) {
            synchronized(this) {
                if (selectedKey != key || selectedVersion != ver) return
                handle(active, seed = true)
            }
        }
    }

    companion object {
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
        @Volatile private var instance: NavNotificationListener? = null
        private val verifyExec = Executors.newSingleThreadExecutor()
        private val verifyBusy = AtomicBoolean(false)

        /** Yêu cầu kiểm tra bất đồng bộ xem notification Maps đang chọn còn sống hay có post mới bị lỡ không. */
        fun verifySourceAliveAsync() {
            val l = instance ?: return
            if (!verifyBusy.compareAndSet(false, true)) return
            verifyExec.execute {
                try { l.verifySelectedAlive() } finally { verifyBusy.set(false) }
            }
        }
    }
}
