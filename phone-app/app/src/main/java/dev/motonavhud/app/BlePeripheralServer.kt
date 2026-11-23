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
 * Phone = BLE peripheral (docs/protocol.md v1.2, kênh C). NAV chỉ Notify (không Read). Mỗi peer có MTU riêng,
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
    private var includeName = true
    private var lastBuild: ((Int) -> ByteArray?)? = null
    private var inFlight: String? = null
    private var inFlightSince = 0L
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

    /** Lỗi cuối cùng của phiên [gen]: đóng phiên (callback muộn bị bỏ qua) và báo lên bên gọi. Gọi dưới [lock]. */
    private fun fail(gen: Int, reason: String) {
        if (gen != generation) return
        NavLog.i("ble fail reason=$reason")
        stopLocked()
        NavBus.bleStatus = reason
        NavBus.subscribers = 0
        onFailure(generation, reason)
    }

    private fun updateWarning() {
        NavBus.warning = if (warnPeers.isEmpty()) "" else
            "Payload không vừa MTU của đồng hồ (cho phép ${warnPeers.values.min()} byte): không gửi. Tăng MTU phía đồng hồ."
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
            NavLog.i("gatt server open gen=$gen")
            if (!server.addService(service)) {
                NavLog.i("gatt addService rejected")
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

    private fun stopLocked() {
        running = false
        generation++ // vô hiệu hóa callback muộn của phiên cũ
        runCatching { advCallback?.let { advertiser?.stopAdvertising(it) } }
        runCatching { gatt?.close() }
        advCallback = null
        advertiser = null
        advertising = false
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
            if (inFlight != null && SystemClock.elapsedRealtime() - inFlightSince > IN_FLIGHT_TIMEOUT_MS) {
                NavLog.i("notify timeout")
                // Không nhả slot khi chưa có xác nhận (callback muộn có thể nhả nhầm tin mới): đóng phiên và dựng lại.
                fail(generation, "notify bị treo, dựng lại GATT")
                return
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
            if (bytes == null) {
                warnPeers[peer.device.address] = peer.limit
                updateWarning()
                continue
            }
            if (warnPeers.remove(peer.device.address) != null) updateWarning()
            if (notify(peer, bytes)) {
                inFlight = peer.device.address
                inFlightSince = SystemClock.elapsedRealtime()
                rr = (rr + n + 1) % list.size
                return
            }
            NavLog.i("notify failed peer=${NavLog.addr(peer.device.address)}")
        }
    }

    private fun notify(peer: Peer, payload: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = characteristic ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.notifyCharacteristicChanged(peer.device, ch, false, payload) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = payload
                @Suppress("DEPRECATION")
                g.notifyCharacteristicChanged(peer.device, ch, false)
            }
        } catch (e: Exception) {
            NavBus.bleStatus = "notify lỗi: ${e.javaClass.simpleName}"
            false
        }
    }

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
        NavLog.i("adv start name=$includeName")
        try {
            adv.startAdvertising(settings, data, scan, cb)
        } catch (e: Exception) {
            advCallback = null
            fail(gen, "quảng bá lỗi: ${e.javaClass.simpleName}")
        }
    }

    private fun restartAdvertising(gen: Int) {
        if (!running || gen != generation) return
        NavLog.v("adv restart")
        runCatching { advCallback?.let { advertiser?.stopAdvertising(it) } }
        advCallback = null // callback muộn của lần quảng bá cũ không còn là "hiện tại"
        advertising = false
        startAdvertising(gen)
    }

    private inner class AdvCb(private val gen: Int) : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            synchronized(lock) {
                if (gen != generation || this !== advCallback) return
                advertising = true
                NavLog.i("adv started")
                setStatus(gen, "đang quảng bá" + if (includeName) "" else " (không kèm tên)")
            }
            onChange()
        }

        override fun onStartFailure(errorCode: Int) {
            synchronized(lock) {
                if (gen != generation || this !== advCallback) return
                NavLog.i("adv failed code=$errorCode")
                if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                    advertising = true
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
            if (gen == generation) NavBus.subscribers = peers.values.count { it.subscribed }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            synchronized(lock) {
                if (gen != generation || !running) return
                NavLog.i("gatt service added status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising(gen) else fail(gen, "addService lỗi ($status)")
            }
            onChange()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            synchronized(lock) {
                if (gen != generation || !running) return
                NavLog.i("peer ${if (newState == BluetoothProfile.STATE_CONNECTED) "connect" else "disconnect"} peer=${NavLog.addr(device.address)} status=$status")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    peers.getOrPut(device.address) { Peer(device) }
                    startAdvertising(gen) // advertising connectable có thể bị dừng khi có kết nối
                } else {
                    peers.remove(device.address)
                    warnPeers.remove(device.address)
                    updateWarning()
                    // inFlight giữ nguyên tới khi có onNotificationSent hoặc timeout (xem send()).
                    restartAdvertising(gen)
                    pump()
                }
                updateCount()
            }
            onChange()
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            synchronized(lock) {
                if (gen == generation) peers[device.address]?.mtu = mtu
                NavLog.i("peer mtu=$mtu peer=${NavLog.addr(device.address)}")
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
                peer.subscribed = enable
                if (!enable && warnPeers.remove(device.address) != null) updateWarning()
                peer.pending = if (enable) lastBuild else null // snapshot ngay khi subscribe
                if (responseNeeded && !respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)) {
                    // Stack không nhận phản hồi: hoàn tác, không bơm snapshot.
                    peer.subscribed = prevSubscribed
                    peer.pending = prevPending
                    if (prevWarn != null) { warnPeers[device.address] = prevWarn; updateWarning() }
                    updateCount()
                    NavLog.i("cccd response failed, rolled back peer=${NavLog.addr(device.address)}")
                    return
                }
                NavLog.i("cccd ${if (enable) "subscribe" else "unsubscribe"} peer=${NavLog.addr(device.address)}")
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
                if (status != BluetoothGatt.GATT_SUCCESS) NavLog.i("notify callback status=$status peer=${NavLog.addr(device.address)}")
                if (inFlight == device.address) inFlight = null
                pump()
            }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6e4f0001-b5a3-f393-e0a9-e50e24dcca9e")
        val NAV_UUID: UUID = UUID.fromString("6e4f0002-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val IN_FLIGHT_TIMEOUT_MS = 2000L
    }
}
