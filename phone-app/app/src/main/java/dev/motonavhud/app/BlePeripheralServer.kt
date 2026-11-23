package dev.motonavhud.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.motonavhud.core.PayloadBuilder
import dev.motonavhud.core.Transport
import java.util.UUID

/**
 * Phone = BLE peripheral (docs/protocol.md v1.3, kênh C). NAV chỉ Notify (không Read). Mỗi peer có MTU riêng,
 * chỉ nhận notify sau khi ghi CCCD hợp lệ; toàn server chỉ một notify đang bay, mỗi peer giữ duy nhất
 * "người dựng payload" mới nhất và payload được dựng LÚC GỬI THẬT (tuổi `a` luôn mới).
 * Mọi trạng thái nằm dưới [lock]; callback của phiên server đã đóng (generation cũ) bị bỏ qua.
 */
@SuppressLint("MissingPermission")
class BlePeripheralServer(
    private val ctx: Context,
    private val onChange: () -> Unit,
    /** Lỗi cuối cùng của một phiên (đăng ký service/quảng bá/notify treo): phiên đã được đóng, bên gọi quyết định thử lại hay dừng.
     *  Int là [currentGeneration] sau khi đóng: bên gọi bỏ qua lỗi cũ nếu từ đó đã start/stop lại. */
    private val onFailure: (Int, String) -> Unit,
) : Transport {

    private class Peer(val device: BluetoothDevice) {
        var mtu = 23
        var subscribed = false
        val connectedAt = SystemClock.elapsedRealtime()
        var subscribedAt = 0L
        var firstNotified = false
        var sentConfirmed = false
        var pending: ((Int) -> ByteArray?)? = null
        val limit get() = minOf(PayloadBuilder.MAX_BYTES, mtu - 3)
    }

    private val lock = Any()
    private val peers = LinkedHashMap<String, Peer>()
    private var generation = 0
    private var running = false
    private var gatt: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advCallback: AdvertiseCallback? = null
    private var advertising = false
    private var advStartedAt = 0L
    private var includeName = true
    private var lastBuild: ((Int) -> ByteArray?)? = null
    private var inFlight: String? = null
    private var inFlightSince = 0L
    private var inFlightId = 0L // id tăng dần của lần gửi đang giữ slot
    private var sendSeq = 0L
    private var inFlightOrphaned = false // peer của lần gửi đang bay đã ngắt/hủy subscribe; slot vẫn giữ tới khi hết hạn
    private var rr = 0
    private val warnPeers = HashMap<String, Int>() // peer -> giới hạn byte không đủ cho lõi payload

    val subscriberCount: Int get() = synchronized(lock) { peers.values.count { it.subscribed } }
    val currentGeneration: Int get() = synchronized(lock) { generation }
    val isRunning: Boolean get() = synchronized(lock) { running }
    val isAdvertising: Boolean get() = synchronized(lock) { advertising }

    private fun setStatus(gen: Int, text: String) {
        if (gen != generation) return
        NavBus.bleStatus = text
    }

    /** Bỏ mọi khung đang xếp hàng và snapshot subscribe đã lưu (đổi nguồn demo/live). Khung đang bay không thu hồi được. */
    fun invalidatePending() {
        synchronized(lock) {
            lastBuild = null
            for (peer in peers.values) peer.pending = null
        }
    }

    private fun markNotifyError(detail: String) {
        NavBus.deliveryError = true
        NavBus.setBle(BleState.NOTIFY_ERROR, "notify lỗi: $detail", detail)
    }

    private fun setState(gen: Int, state: BleState, text: String, detail: String = "") {
        if (gen != generation) return
        if (NavBus.deliveryError && (state == BleState.CONNECTED || state == BleState.ADVERTISING)) {
            NavBus.bleStatus = text // giữ NOTIFY_ERROR tới khi một lần gửi thành công hoặc phiên/peer kết thúc
            return
        }
        NavBus.setBle(state, text, detail)
    }

    /** Lỗi cuối cùng của phiên [gen]: đóng phiên (callback muộn bị bỏ qua) và báo lên bên gọi. Gọi dưới [lock]. */
    private fun fail(gen: Int, reason: String) {
        if (gen != generation) return
        NavBus.logEvent("ble_fail reason=${NavBus.tok(reason)}")
        stopLocked()
        NavBus.bleStatus = reason
        NavBus.subscribers = 0
        onFailure(generation, reason)
    }

    private fun updateWarning() {
        NavBus.warning = if (warnPeers.isEmpty()) "" else
            "Payload không vừa MTU của đồng hồ (cho phép ${warnPeers.values.min()} byte): không gửi. Tăng MTU phía đồng hồ."
        NavBus.warnCode = if (warnPeers.isEmpty()) WarnCode.NONE else WarnCode.MTU_TOO_SMALL
    }

    /** Trả null nếu chạy được, ngược lại là lý do (tiếng Việt). Gọi lại sau stop() để dựng lại server. */
    fun start(): String? = synchronized(lock) {
        if (running) stopLocked()
        if (Build.VERSION.SDK_INT >= 31) {
            for (p in listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)) {
                if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) return "thiếu quyền $p"
            }
        }
        try {
            val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return "máy không có Bluetooth"
            if (!adapter.isEnabled) return "Bluetooth đang tắt"
            val adv = adapter.bluetoothLeAdvertiser ?: return "máy không hỗ trợ BLE advertising"
            val gen = ++generation
            val server = manager.openGattServer(ctx, GattCb(gen)) ?: return "không mở được GATT server"

            // Chỉ Notify, không Read: người lạ không đọc được snapshot.
            val ch = BluetoothGattCharacteristic(NAV_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
            ch.addDescriptor(
                BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE),
            )
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(ch)
            gatt = server
            characteristic = ch
            advertiser = adv
            includeName = true
            running = true
            NavBus.logEvent("gatt_open gen=$gen")
            if (!server.addService(service)) {
                NavBus.logEvent("gatt_add_rejected gen=$gen")
                stopLocked()
                return "addService thất bại"
            }
            null
        } catch (e: Exception) {
            stopLocked()
            "lỗi BLE: ${e.javaClass.simpleName}"
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
        NavBus.subscribers = 0
        onChange()
    }

    private fun stopAdvertisingLocked(reason: String = "stop") {
        if (advertising || advCallback != null) NavBus.logEvent("adv_stop reason=$reason")
        runCatching { advCallback?.let { advertiser?.stopAdvertising(it) } }
        advCallback = null
        advertising = false
        advStartedAt = 0L
    }

    private fun stopLocked() {
        running = false
        generation++ // vô hiệu hóa callback muộn của phiên cũ
        stopAdvertisingLocked("session_stop")
        NavBus.deliveryError = false
        runCatching { gatt?.close() }
        advertiser = null
        gatt = null
        characteristic = null
        peers.clear()
        warnPeers.clear()
        updateWarning()
        inFlight = null
        lastBuild = null
    }

    override fun send(payload: ByteArray) = send { limit -> if (payload.size <= limit) payload else null }

    override fun send(build: (mtuLimit: Int) -> ByteArray?) {
        synchronized(lock) {
            if (!running) return
            lastBuild = build
            for (peer in peers.values) if (peer.subscribed) peer.pending = build
            val slotAddr = inFlight
            if (slotAddr != null && SystemClock.elapsedRealtime() - inFlightSince > IN_FLIGHT_TIMEOUT_MS) {
                val ms = SystemClock.elapsedRealtime() - inFlightSince
                val gone = peers[slotAddr]?.subscribed != true
                if (inFlightOrphaned || gone) {
                    // Peer đã đi (hoặc đã đi rồi quay lại): chỉ nhả slot (không dựng lại GATT, không ble_failure). Không bao giờ có 2 notify cùng bay.
                    NavBus.logEvent("notify_inflight_expired addr=${NavLog.addr(slotAddr)} id=$inFlightId ms=$ms peer_gone=$gone orphaned=$inFlightOrphaned")
                    inFlight = null
                } else {
                    NavBus.logEvent("notify_inflight_timeout ms=$ms addr=${NavLog.addr(slotAddr)} id=$inFlightId")
                    // Peer còn nối mà notify treo: không nhả slot khi chưa có xác nhận, đóng phiên và dựng lại.
                    fail(generation, "notify bị treo, dựng lại GATT")
                    return
                }
            }
            pump()
        }
    }

    /** Gửi cho peer kế tiếp có hàng chờ nếu slot toàn server đang rảnh. Gọi dưới [lock]. */
    private fun pump() {
        if (inFlight != null) return
        val list = peers.values.toList()
        if (list.isEmpty()) return
        for (n in list.indices) {
            val peer = list[(rr + n) % list.size]
            val build = peer.pending ?: continue
            peer.pending = null
            if (!peer.subscribed) continue
            val bytes = build(peer.limit)
            if (bytes != null && bytes.isEmpty()) continue // khung của nguồn đã đổi (demo/live): bỏ, không cảnh báo MTU
            if (bytes == null) {
                warnPeers[peer.device.address] = peer.limit
                updateWarning()
                continue
            }
            if (warnPeers.remove(peer.device.address) != null) updateWarning()
            if (notify(peer, bytes)) {
                inFlight = peer.device.address
                inFlightId = ++sendSeq
                inFlightOrphaned = false
                inFlightSince = SystemClock.elapsedRealtime()
                if (!peer.firstNotified) {
                    peer.firstNotified = true
                    val addr = NavLog.addr(peer.device.address)
                    val sub = if (peer.subscribedAt > 0L) peer.subscribedAt else inFlightSince
                    NavBus.logEvent("first_notify_sent addr=$addr since_connect_ms=${inFlightSince - peer.connectedAt}")
                    NavBus.logEvent(
                        "connect_summary addr=$addr connect_to_subscribe_ms=${sub - peer.connectedAt} " +
                            "subscribe_to_first_notify_ms=${inFlightSince - sub} svc_to_connect_ms=${peer.connectedAt - NavBus.serviceStartedAt}",
                    )
                }
                rr = (rr + n + 1) % list.size
                return
            }
        }
    }

    private fun notify(peer: Peer, payload: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = characteristic ?: return false
        return try {
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.notifyCharacteristicChanged(peer.device, ch, false, payload) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = payload
                @Suppress("DEPRECATION")
                g.notifyCharacteristicChanged(peer.device, ch, false)
            }
            if (!ok) {
                NavBus.logEvent("notify_rejected addr=${NavLog.addr(peer.device.address)}")
                markNotifyError("rejected")
            }
            ok
        } catch (e: Exception) {
            NavBus.logEvent("notify_rejected addr=${NavLog.addr(peer.device.address)} err=${e.javaClass.simpleName}")
            markNotifyError(e.javaClass.simpleName)
            false
        }
    }

    // Luôn quảng bá nhanh (LOW_LATENCY/HIGH) khi service chạy: đồng hồ mở muộn vẫn thấy ngay.
    // Đánh đổi: tốn pin radio hơn khi chờ; service vẫn tự dừng sau số phút rảnh đã đặt.
    private fun startAdvertising(gen: Int) {
        val adv = advertiser ?: return
        if (advertising || advCallback != null || gen != generation) return // đã quảng bá hoặc đang chờ kết quả
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // Gói chính: cờ (3) + UUID 128-bit (18) <= 31. Tên Bluetooth hệ thống (không đặt được tùy ý) chỉ ở scan response.
        val data = AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val scan = AdvertiseData.Builder().setIncludeDeviceName(includeName).build()
        val cb = AdvCb(gen)
        advCallback = cb
        NavBus.logEvent("adv_start mode=fast tx=high name=$includeName")
        try {
            adv.startAdvertising(settings, data, scan, cb)
        } catch (e: Exception) {
            advCallback = null
            fail(gen, "quảng bá lỗi: ${e.javaClass.simpleName}")
        }
    }

    private fun restartAdvertising(gen: Int) {
        if (!running || gen != generation) return
        stopAdvertisingLocked("restart")
        startAdvertising(gen)
    }

    private inner class AdvCb(private val gen: Int) : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            synchronized(lock) {
                if (gen != generation || this !== advCallback) return
                advertising = true
                advStartedAt = SystemClock.elapsedRealtime()
                NavBus.logEvent("adv_started mode=fast")
                setState(gen, BleState.ADVERTISING, "đang tìm đồng hồ" + if (includeName) "" else " (không kèm tên)")
            }
            onChange()
        }

        override fun onStartFailure(errorCode: Int) {
            synchronized(lock) {
                if (gen != generation || this !== advCallback) return
                NavBus.logEvent("adv_failed code=$errorCode")
                if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                    advertising = true
                    advStartedAt = SystemClock.elapsedRealtime()
                    return
                }
                advertising = false
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && includeName) {
                    includeName = false // tên Bluetooth quá dài: thử lại không kèm tên
                    advCallback = null
                    startAdvertising(gen)
                    return
                }
                fail(gen, "quảng bá lỗi (mã $errorCode)")
            }
            onChange()
        }
    }

    private inner class GattCb(private val gen: Int) : BluetoothGattServerCallback() {
        /** true nếu stack nhận phản hồi; false khi phiên đã đóng, sendResponse trả false hoặc ném lỗi. */
        private fun respond(device: BluetoothDevice, requestId: Int, code: Int, offset: Int, value: ByteArray?): Boolean {
            // Kiểm tra generation, sendResponse và cập nhật status cùng dưới lock, không đua với stopLocked().
            synchronized(lock) {
                if (gen != generation) return false
                val g = gatt ?: return false
                return try {
                    g.sendResponse(device, requestId, code, offset, value)
                } catch (e: Exception) {
                    setStatus(gen, "sendResponse lỗi: ${e.javaClass.simpleName}")
                    false
                }
            }
        }

        private fun updateCount() {
            if (gen == generation) {
                NavBus.subscribers = peers.values.count { it.subscribed }
                if (NavBus.subscribers == 0) NavBus.deliveryError = false // peer bị ảnh hưởng đã hết
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            synchronized(lock) {
                if (gen != generation || !running) return
                NavBus.logEvent("gatt_service_added status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising(gen) else fail(gen, "addService lỗi ($status)")
            }
            onChange()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            synchronized(lock) {
                if (gen != generation || !running) return
                val addr = NavLog.addr(device.address)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    NavBus.logEvent("peer_connect addr=$addr status=$status newState=$newState")
                    peers[device.address] = Peer(device)
                    // Chỉ giữ quảng bá nếu chưa có đồng hồ nào subscribe
                    if (peers.values.none { it.subscribed }) startAdvertising(gen)
                } else {
                    val connectedMs = peers[device.address]?.let { SystemClock.elapsedRealtime() - it.connectedAt } ?: -1L
                    NavBus.logEvent("peer_disconnect addr=$addr status=$status newState=$newState connected_ms=$connectedMs")
                    if (inFlight == device.address) inFlightOrphaned = true
                    peers.remove(device.address)
                    warnPeers.remove(device.address)
                    updateWarning()
                    if (peers.values.none { it.subscribed }) restartAdvertising(gen)
                    pump()
                }
                updateCount()
            }
            onChange()
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            synchronized(lock) {
                if (gen == generation) peers[device.address]?.mtu = mtu
                NavBus.logEvent("mtu_changed addr=${NavLog.addr(device.address)} mtu=$mtu")
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            if (descriptor.uuid != CCCD_UUID) { respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null); return }
            if (offset != 0) { respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null); return }
            val on = synchronized(lock) { peers[device.address]?.subscribed == true }
            respond(
                device, requestId, BluetoothGatt.GATT_SUCCESS, 0,
                if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            // Chỉ nhận 0x0001 (bật) / 0x0000 (tắt) ở offset 0, từ chối prepared write rõ ràng.
            val code = when {
                descriptor.uuid != CCCD_UUID -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                preparedWrite -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                offset != 0 -> BluetoothGatt.GATT_INVALID_OFFSET
                value == null || value.size != 2 -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                value[1].toInt() != 0 || (value[0].toInt() != 0 && value[0].toInt() != 1) -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                else -> BluetoothGatt.GATT_SUCCESS
            }
            if (code != BluetoothGatt.GATT_SUCCESS) {
                NavBus.logEvent("cccd_reject addr=${NavLog.addr(device.address)} code=$code")
                if (responseNeeded) respond(device, requestId, code, offset, null)
                return
            }
            val enable = value!![0].toInt() == 1
            // Commit rồi mới trả lời thành công.
            synchronized(lock) {
                if (gen != generation || !running) return
                val peer = peers.getOrPut(device.address) { Peer(device) }
                val prevSubscribed = peer.subscribed
                val prevPending = peer.pending
                val prevWarn = warnPeers[device.address]
                if (!enable && inFlight == device.address) inFlightOrphaned = true
                peer.subscribed = enable
                if (enable && !prevSubscribed) peer.subscribedAt = SystemClock.elapsedRealtime()
                if (!enable && warnPeers.remove(device.address) != null) updateWarning()
                peer.pending = if (enable) lastBuild else null // snapshot ngay khi subscribe
                if (responseNeeded && !respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)) {
                    // Stack không nhận phản hồi: hoàn tác, không bơm snapshot.
                    peer.subscribed = prevSubscribed
                    peer.pending = prevPending
                    if (prevWarn != null) { warnPeers[device.address] = prevWarn; updateWarning() }
                    updateCount()
                    NavBus.logEvent("cccd_rollback addr=${NavLog.addr(device.address)}")
                    return
                }
                NavBus.logEvent("cccd_write addr=${NavLog.addr(device.address)} action=${if (enable) "enable" else "disable"} responseNeeded=$responseNeeded")
                // Đã có đồng hồ subscribe -> dừng quảng bá BLE để tiết kiệm pin radio & giảm nhiễu sóng
                if (enable) {
                    stopAdvertisingLocked("subscribed")
                    setState(gen, BleState.CONNECTED, "đã kết nối Watch S5")
                } else if (peers.values.none { it.subscribed }) {
                    startAdvertising(gen)
                }
                updateCount()
                pump()
            }
            onChange()
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(lock) {
                if (gen != generation || !running) return
                // Callback chỉ hợp lệ khi slot đang thuộc đúng địa chỉ này; ngược lại bỏ qua hoàn toàn (kể cả deliveryError).
                if (inFlight != device.address) return
                val sentPeer = peers[device.address]
                // Slot mồ côi / peer không còn nối+subscribe: chỉ nhả slot, không đụng trạng thái gửi.
                val current = !inFlightOrphaned && sentPeer?.subscribed == true
                if (!current) {
                    inFlight = null
                    pump()
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    NavBus.logEvent("notify_sent addr=${NavLog.addr(device.address)} status=$status")
                    markNotifyError("status=$status")
                } else if (sentPeer != null && !sentPeer.sentConfirmed) {
                    sentPeer.sentConfirmed = true
                    NavBus.logEvent("notify_sent addr=${NavLog.addr(device.address)} status=$status")
                }
                if (status == BluetoothGatt.GATT_SUCCESS && NavBus.deliveryError) {
                    // Chỉ một lần gửi hoàn tất thành công mới xóa lỗi gửi.
                    NavBus.deliveryError = false
                    if (NavBus.bleState == BleState.NOTIFY_ERROR) NavBus.setBle(BleState.CONNECTED, "đã kết nối Watch S5")
                }
                inFlight = null
                pump()
            }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6e4f0001-b5a3-f393-e0a9-e50e24dcca9e")
        val NAV_UUID: UUID = UUID.fromString("6e4f0002-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        /** Giá trị trả về của build lambda cho khung đã lỗi thời (nguồn đổi): bỏ, không gửi. */
        val STALE_FRAME = ByteArray(0)
        private const val IN_FLIGHT_TIMEOUT_MS = 2000L
    }
}
