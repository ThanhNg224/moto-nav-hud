package dev.motonavhud.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import dev.motonavhud.core.NavState
import dev.motonavhud.core.PayloadBuilder
import dev.motonavhud.core.Throttle
import dev.motonavhud.core.Transport
import kotlin.random.Random

/** Sở hữu BLE server và vòng lặp throttle/heartbeat (tick 200 ms, heartbeat 1 s do [Throttle] quyết định). */
class NavForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ble: BlePeripheralServer
    private lateinit var throttle: Throttle
    private val transports = mutableListOf<Transport>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewAt = 0L
    private var seenVersion = -1
    private var demoStart = 0L
    private var wasDemo = false
    private var created = false
    private var active = false // false ngay khi bắt đầu dừng: mọi retry/callback muộn phải thoát
    private var bleRetries = 0
    private var retry: Runnable? = null
    private var lastLogAt = 0L
    private var lastLogKmi = ""
    private var lastAgeBucket = 0
    private var lastVerifyAt = 0L
    private var idleSince = 0L
    private var lastSubscribers = -1
    private var lastNotifState = NavBus.ServiceStatus.WAITING
    private var tickEpoch = 0
    private var frameDemo = false // chế độ khung đã lấy mẫu ở đầu tick: dùng chung cho offer, emission và payload
    private var keepStopReason = false
    private var stopReason: String? = null

    /** Ghi đúng một dòng `svc_stop` cho mỗi lần dừng: lý do cụ thể được ghi trước, [onDestroy] chỉ ghi nếu chưa có. */
    private fun markStop(reason: String) {
        if (stopReason != null) return
        stopReason = reason
        NavBus.logEvent("svc_stop reason=$reason uptime_ms=${SystemClock.elapsedRealtime() - NavBus.serviceStartedAt}")
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val nextMs = step()
            if (active && nextMs > 0L) handler.postDelayed(this, nextMs)
        }
    }

    private fun wakeTickNow() {
        handler.post {
            if (!active) return@post
            handler.removeCallbacks(tick)
            tick.run()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    NavLog.i("bt state=on")
                    if (!active) return
                    cancelRetry()
                    bleRetries = 0
                    startBle()
                }
                BluetoothAdapter.STATE_OFF -> {
                    NavLog.i("bt state=off")
                    cancelRetry()
                    ble.stop()
                    NavBus.setBle(BleState.BT_OFF, "Bluetooth đã tắt, chờ bật lại")
                    NavBus.changed()
                    syncNotification()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            markStop("manual")
            beginStop()
            NavBus.autoStartGate.onManualStop(NavBus.mapsState.k != NavState.K_IDLE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    /** Bước đầu của mọi đường dừng: chặn retry/tick muộn; phần dọn còn lại nằm ở [onDestroy]. */
    private fun beginStop() {
        active = false
        cancelRetry()
        handler.removeCallbacks(tick)
    }

    /** Vuốt khỏi Recents / xóa RAM: dừng hẳn như bấm Dừng nhưng KHÔNG tạm ngưng tự bật (không gọi onManualStop). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        NavBus.logEvent("svc_task_removed")
        markStop("task_removed")
        beginStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onCreate() {
        super.onCreate()
        EventLog.init(applicationContext)
        NavBus.serviceStartedAt = SystemClock.elapsedRealtime()
        requestedStopReason = null
        val channel = NotificationChannel(CHANNEL_ID, "MotoNav", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(NavBus.ServiceStatus.WAITING), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            NavBus.autoStartGate.onAutoStartBlocked()
            keepStopReason = true
            markStop("error")
            NavBus.setBle(BleState.ERROR_STOPPED, "không chạy được foreground service: ${e.javaClass.simpleName}", e.javaClass.simpleName)
            NavBus.changed()
            stopSelf()
            return
        }

        throttle = Throttle(clock = { SystemClock.elapsedRealtime() }, z = Random.nextInt(1, Int.MAX_VALUE))
        ble = BlePeripheralServer(
            this,
            {
                NavBus.changed()
                wakeTickNow()
            },
            { gen, reason -> handler.post { onBleFailure(gen, reason) } },
        )
        transports += ble
        val err = ble.start()
        if (err != null) {
            // Lỗi không phục hồi được lúc khởi động (Bluetooth tắt, thiếu quyền...): dừng gọn, giữ lý do trên UI.
            NavBus.autoStartGate.onBleExhausted(NavBus.mapsState.k != NavState.K_IDLE)
            keepStopReason = true
            if (getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == false) {
                markStop("bt_off")
                NavBus.setBle(BleState.BT_OFF, "không khởi động được: $err", err)
            } else {
                markStop("error")
                NavBus.setBle(BleState.ERROR_STOPPED, "không khởi động được: $err", err)
            }
            NavBus.changed()
            stopSelf()
            return
        }
        created = true
        active = true
        NavBus.logEvent("svc_start reason=${NavBus.startReason}")
        NavBus.setBle(BleState.STARTING, "đang khởi động BLE")
        NavBus.warning = ""
        NavBus.warnCode = WarnCode.NONE
        NavBus.autoStartGate.onServiceStarted()
        NavBus.onServiceWake = { wakeTickNow() }
        registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        NavBus.serviceRunning = true
        NavBus.idleRemainingSec = -1
        NavBus.changed()
        NavTileService.requestRefresh(this)
        handler.post(tick)
    }

    /** Lỗi cuối cùng của phiên BLE: thử dựng lại tối đa [MAX_BLE_RETRIES] lần, quá thì dừng service (nhả wake lock). */
    private fun onBleFailure(gen: Int, reason: String) {
        if (!active || gen != ble.currentGeneration) return // lỗi của phiên đã bị thay thế
        if (++bleRetries > MAX_BLE_RETRIES) {
            active = false
            NavBus.autoStartGate.onBleExhausted(NavBus.mapsState.k != NavState.K_IDLE)
            NavBus.logEvent("ble_failure retry=$bleRetries giveup=1 reason=${NavBus.tok(reason)}")
            markStop("error")
            NavBus.setBle(BleState.ERROR_STOPPED, "BLE lỗi, đã dừng: $reason", reason)
            NavBus.changed()
            stopSelf()
            return
        }
        NavBus.logEvent("ble_failure retry=$bleRetries backoff_ms=${1000L * bleRetries} reason=${NavBus.tok(reason)}")
        NavBus.setBle(BleState.ERROR_RETRY, "BLE lỗi ($reason), thử lại lần $bleRetries", bleRetries.toString())
        NavBus.changed()
        syncNotification()
        cancelRetry()
        val r = Runnable {
            retry = null
            if (active && gen == ble.currentGeneration && getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true) startBle()
        }
        retry = r
        handler.postDelayed(r, 1000L * bleRetries)
    }

    private fun cancelRetry() {
        retry?.let { handler.removeCallbacks(it) }
        retry = null
    }

    private fun startBle() {
        val err = ble.start()
        if (err == null) NavBus.setBle(BleState.STARTING, "đang khởi động BLE")
        NavBus.changed()
        if (err != null) onBleFailure(ble.currentGeneration, err)
    }

    private fun buildNotification(s: NavBus.ServiceStatus): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, NavForegroundService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val mapsPi = PendingIntent.getActivity(this, 2, QuickLaunchActivity.openMapsIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = when (s) {
            NavBus.ServiceStatus.WAITING -> R.string.svc_notif_waiting
            NavBus.ServiceStatus.CONNECTED -> R.string.svc_notif_connected
            NavBus.ServiceStatus.NAVIGATING -> R.string.svc_notif_navigating
            NavBus.ServiceStatus.ARRIVED -> R.string.svc_notif_arrived
            NavBus.ServiceStatus.DEMO -> R.string.svc_notif_demo
            NavBus.ServiceStatus.BT_OFF -> R.string.svc_notif_bt_off
            NavBus.ServiceStatus.DELIVERY_PROBLEM -> R.string.svc_notif_error
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hud_arrow)
            .setContentTitle(getString(R.string.svc_notif_title))
            .setContentText(getString(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.svc_notif_open_maps), mapsPi).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.svc_notif_stop), stopPi).build())
            .build()
    }

    /** Chỉ đăng lại thông báo (cùng id) và làm mới ô Quick Settings khi trạng thái có nghĩa đổi, không theo từng tick. */
    private fun syncNotification() {
        if (!active) return
        val s = NavBus.serviceStatus()
        if (s == lastNotifState) return
        lastNotifState = s
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
        NavTileService.requestRefresh(this)
    }

    /** Giữ CPU thức khi có đồng hồ nối hoặc Maps/Demo đang chạy; thả WakeLock lúc chờ rảnh để tiết kiệm pin. */
    private fun syncWakeLock(needed: Boolean, now: Long) {
        if (!needed) {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLockRenewAt = 0L
            return
        }
        if (wakeLock?.isHeld == true && now < wakeLockRenewAt) return
        val pm = getSystemService(PowerManager::class.java)
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "motonavhud:heartbeat").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        wl.acquire(WAKE_LOCK_TIMEOUT_MS)
        wakeLockRenewAt = now + WAKE_LOCK_TIMEOUT_MS / 2
    }

    /** Trả chu kỳ tick tiếp theo (ms), hoặc -1 nếu service đang dừng. */
    private fun step(): Long {
        val now = SystemClock.elapsedRealtime()
        val snap = NavBus.mapsSnapshot
        if (NavBus.demo && !NavBus.canStartDemo()) {
            // Google Maps thật luôn thắng demo: tắt demo, vòng này sẽ offer lại khung thật.
            NavBus.demo = false
            NavBus.changed()
        }
        // Đọc epoch TRƯỚC cờ demo: nếu đổi giữa hai lần đọc thì khung bị coi là cũ (an toàn), tick sau lấy mẫu lại.
        tickEpoch = NavBus.frameEpoch
        val demoNow = NavBus.demo
        if (demoNow != frameDemo) {
            frameDemo = demoNow
            ble.invalidatePending()
        }
        val subs = ble.subscriberCount
        val activeWork = NavBus.demo || snap.state.k != NavState.K_IDLE || subs > 0
        syncWakeLock(activeWork, now)

        if (frameDemo) {
            if (demoStart == 0L) demoStart = now
            throttle.offer(demoState(now - demoStart), now)
            seenVersion = snap.version
            wasDemo = true
        } else {
            demoStart = 0L
            // Định kỳ 3 s kiểm tra sự hiện diện & đọc bù postTime mới hơn (không làm mới a nếu post cũ giữ nguyên).
            if (snap.state.k != NavState.K_IDLE && now - snap.atMs >= VERIFY_ALIVE_MS && now - lastVerifyAt >= VERIFY_ALIVE_MS) {
                lastVerifyAt = now
                NavNotificationListener.verifySourceAliveAsync()
            }
            if (wasDemo || snap.version != seenVersion) {
                // Thoát Demo hoặc có snapshot bất biến mới: offer nguyên bộ (state, atMs) cùng lúc.
                seenVersion = snap.version
                wasDemo = false
                throttle.offer(snap.state, snap.atMs)
            }
        }
        if (ble.isAdvertising || subs > 0) bleRetries = 0
        // Kiểm tra auto-stop độc lập với throttle.poll() trên mỗi nhịp.
        val autoStopMin = Prefs.autoStopMinutes(this)
        if (autoStopMin > 0 && NavBus.currentState.k == NavState.K_IDLE && !NavBus.demo) {
            if (idleSince == 0L) idleSince = now
            val remainMs = autoStopMin * 60_000L - (now - idleSince)
            if (remainMs <= 0L) {
                active = false
                NavLog.i("auto-stop idle=${autoStopMin}m")
                markStop("idle")
                NavBus.setBle(BleState.AUTO_STOPPED, "tự dừng sau $autoStopMin phút chờ", autoStopMin.toString())
                NavBus.idleRemainingSec = -1
                NavBus.changed()
                stopSelf()
                return -1L
            }
            NavBus.idleRemainingSec = ((remainMs + 999) / 1000).toInt()
        } else {
            idleSince = 0L
            NavBus.idleRemainingSec = -1
        }
        val nextTickMs = if (activeWork) FAST_TICK_MS else IDLE_TICK_MS
        val alive = NavBus.mapsAlive
        if (!frameDemo && alive != null && alive.version == snap.version) throttle.confirmAlive(alive.atMs)
        val e = throttle.poll()
        if (e == null) {
            syncNotification()
            return nextTickMs
        }
        val rendered = e.render(now)
        NavBus.currentState = rendered.state
        NavBus.currentDemo = frameDemo && rendered.state.k != NavState.K_IDLE
        syncNotification()
        if (rendered.state.k != NavState.K_IDLE) {
            idleSince = 0L
            NavBus.idleRemainingSec = -1
        }

        // Payload được dựng LÚC GỬI THẬT từ mốc thời gian nguồn, nên a/hết hạn luôn tính lại.
        // Closure không có side-effect: cảnh báo MTU do BlePeripheralServer suy ra từ lần gửi thật của từng peer.
        val demoAtEmit = frameDemo
        val epochAtEmit = tickEpoch
        val build = { limit: Int ->
            if (NavBus.frameEpoch != epochAtEmit) BlePeripheralServer.STALE_FRAME else buildPayload(e, limit, demoAtEmit)
        }
        transports.forEach { it.send(build) }
        logPayload(e, now)
        val subsNow = ble.subscriberCount
        NavBus.subscribers = subsNow
        if (subsNow != lastSubscribers) {
            lastSubscribers = subsNow
            NavTileService.requestRefresh(this)
        }
        // Chỉ serialize bản xem trước khi MainActivity đang mở: tiết kiệm CPU/RAM khi tắt màn hình bỏ túi.
        if (NavBus.hasListeners) {
            val preview = buildPayload(e, PayloadBuilder.MAX_BYTES, demoAtEmit)
            NavBus.lastPayload = preview?.let { String(it, Charsets.UTF_8) } ?: "(không vừa)"
            NavBus.changed()
        }
        return if (activeWork || subsNow > 0 || rendered.state.k != NavState.K_IDLE) FAST_TICK_MS else IDLE_TICK_MS
    }

    /** Ghi log ngay khi k/m/i đổi; nhịp đều không đổi chỉ ghi tối đa 1 dòng mỗi 30 s để không spam logcat. */
    private fun logPayload(e: Throttle.Emission, now: Long) {
        val r = e.render(now)
        // Tuổi nguồn thô (render() đưa về IDLE/0 khi quá 15 s nên không dùng r.a).
        val rawAge = e.sourceAt?.let { (now - it).coerceAtLeast(0) } ?: 0L
        // Bucket: 1 = "cũ" (>= 10 s), 2 = quá 75 s (chỉ còn nhờ thông báo Maps xác nhận còn sống, tối đa 10 phút).
        val bucket = if (e.state.k != 1) 0 else if (rawAge > 75000) 2 else if (rawAge > 10000) 1 else 0
        if (bucket != lastAgeBucket) {
            lastAgeBucket = bucket
            NavBus.logEvent("src_age_cross bucket=$bucket a=$rawAge k=${e.state.k}")
        }
        val kmi = "${r.state.k}/${r.state.m}/${e.i}"
        if (kmi == lastLogKmi && now - lastLogAt < PAYLOAD_HEARTBEAT_LOG_MS) return
        lastLogKmi = kmi
        lastLogAt = now
        NavLog.i("payload q=${e.q} k=${r.state.k} m=${r.state.m} d=${r.state.d} i=${e.i} a=${r.a}")
    }

    private fun buildPayload(e: Throttle.Emission, limit: Int, demo: Boolean): ByteArray? {
        val r = e.render(SystemClock.elapsedRealtime())
        return (PayloadBuilder.build(r.state, e.z, e.q, e.i, r.a, Prefs.token(this), limit, demo && r.state.k != NavState.K_IDLE) as? PayloadBuilder.Result.Ok)?.bytes
    }

    // Nhãn demo tổng hợp theo ngôn ngữ; tên đường demo (3, 6) là dữ liệu mẫu, không phải tên đường Maps thật.
    private val demoNames by lazy {
        mapOf(
            1 to getString(R.string.svc_demo_straight), 2 to getString(R.string.svc_demo_slight_left), 3 to "Nguyễn Văn Linh",
            4 to getString(R.string.svc_demo_sharp_left), 5 to getString(R.string.svc_demo_slight_right), 6 to "Lê Văn Lương",
            7 to getString(R.string.svc_demo_sharp_right), 8 to getString(R.string.svc_demo_uturn),
            9 to getString(R.string.svc_demo_roundabout), 10 to getString(R.string.svc_demo_keep_left),
            11 to getString(R.string.svc_demo_keep_right), 12 to getString(R.string.svc_demo_arrive),
            13 to getString(R.string.svc_demo_depart),
        )
    }

    /** Demo: maneuver 1..13, mỗi bước 4 s, d giảm 260 -> 20 m (đủ thời gian quan sát mũi tên và nghe trọn 2 ngưỡng rung). */
    private fun demoState(elapsed: Long): NavState {
        val idx = ((elapsed / DEMO_STEP_MS) % 13).toInt() + 1
        val frac = (elapsed % DEMO_STEP_MS).toDouble() / DEMO_STEP_MS
        val d = (260 - 240 * frac).toInt()
        return NavState(
            k = if (idx == 12) NavState.K_ARRIVED else NavState.K_NAVIGATING,
            m = idx,
            x = if (idx == 9) 2 else 0,
            d = d,
            s = demoNames.getValue(idx),
            e = "12:45",
            r = 4200,
        )
    }

    override fun onDestroy() {
        active = false
        NavBus.onServiceWake = null
        cancelRetry()
        markStop(requestedStopReason ?: "destroy")
        requestedStopReason = null
        handler.removeCallbacks(tick)
        if (created) {
            runCatching { unregisterReceiver(btReceiver) }
            ble.stop()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        NavBus.autoStartGate.onServiceStopped()
        NavBus.serviceRunning = false
        NavBus.demo = false
        NavBus.currentState = NavState.IDLE
        NavBus.currentDemo = false
        NavBus.deliveryError = false
        NavBus.idleRemainingSec = -1
        NavBus.lastPayload = ""
        NavBus.subscribers = 0
        NavBus.warning = ""
        NavBus.warnCode = WarnCode.NONE
        // Lý do dừng (lỗi/tự dừng) được giữ cho UI; mọi trường hợp khác về STOPPED.
        if (!keepStopReason && NavBus.bleState != BleState.ERROR_STOPPED && NavBus.bleState != BleState.AUTO_STOPPED) {
            NavBus.setBle(BleState.STOPPED, "chưa chạy")
        }
        NavBus.changed()
        NavTileService.requestRefresh(this)
        super.onDestroy()
    }

    companion object {
        /** Lý do dừng do người dùng yêu cầu qua stopService (không đi qua ACTION_STOP), để onDestroy log đúng. */
        @Volatile private var requestedStopReason: String? = null
        private const val CHANNEL_ID = "nav_service"
        private const val ACTION_STOP = "dev.motonavhud.app.ACTION_STOP"
        private const val NOTIF_ID = 1
        private const val FAST_TICK_MS = 200L
        private const val IDLE_TICK_MS = 1000L
        private const val MAX_BLE_RETRIES = 3
        private const val DEMO_STEP_MS = 4000L
        private const val VERIFY_ALIVE_MS = 3000L
        private const val PAYLOAD_HEARTBEAT_LOG_MS = 30_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L

        fun start(ctx: Context, reason: String = "auto") {
            NavBus.startReason = reason
            ctx.startForegroundService(Intent(ctx, NavForegroundService::class.java))
        }

        fun startByUser(ctx: Context, reason: String = "manual") {
            NavBus.autoStartGate.onManualStartRequested()
            start(ctx, reason)
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, NavForegroundService::class.java))

        fun stopByUser(ctx: Context) {
            if (NavBus.serviceRunning) requestedStopReason = "manual"
            NavBus.autoStartGate.onManualStop(NavBus.mapsState.k != NavState.K_IDLE)
            stop(ctx)
        }
    }
}
