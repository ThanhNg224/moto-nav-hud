package dev.motonavhud.app

import android.os.SystemClock
import dev.motonavhud.core.AutoStartGate
import dev.motonavhud.core.NavState
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/** Ảnh chụp bất biến của nguồn Maps: đọc/ghi nguyên tử để tránh ghép nhầm state cũ với timestamp mới. */
data class MapsSnapshot(
    val state: NavState,
    val atMs: Long,
    val version: Int,
)

/** Trạng thái BLE của điện thoại (peripheral). Mỗi giá trị tương ứng một chuỗi UI riêng. */
enum class BleState {
    STOPPED, STARTING, ADVERTISING, ADVERTISING_SAVER, CONNECTED,
    BT_OFF, ERROR_RETRY, ERROR_STOPPED, AUTO_STOPPED, NOTIFY_ERROR,
}

/** Cảnh báo cho rider; chi tiết kỹ thuật nằm ở [NavBus.warning]. */
enum class WarnCode { NONE, MTU_TOO_SMALL }

/** Trạng thái dùng chung trong process: nguồn (listener/demo) -> service -> UI. */
object NavBus {
    val autoStartGate = AutoStartGate()
    @Volatile var mapsSnapshot: MapsSnapshot = MapsSnapshot(NavState.IDLE, 0L, 0); private set

    val mapsState: NavState get() = mapsSnapshot.state

    /** Chạy thử chỉ được bắt đầu khi không có chỉ đường Google Maps thật (Maps luôn thắng demo). */
    fun canStartDemo(): Boolean = mapsState.k == NavState.K_IDLE

    /** Tóm tắt cho thông báo và ô Quick Settings: dựa trên người nhận + sức khỏe gửi + trạng thái thực sự được gửi. */
    enum class ServiceStatus { WAITING, CONNECTED, NAVIGATING, ARRIVED, DEMO, BT_OFF, DELIVERY_PROBLEM }

    fun serviceStatus(): ServiceStatus = when {
        bleState == BleState.BT_OFF -> ServiceStatus.BT_OFF
        deliveryError || bleState == BleState.ERROR_RETRY || bleState == BleState.NOTIFY_ERROR || warnCode != WarnCode.NONE ->
            ServiceStatus.DELIVERY_PROBLEM
        subscribers == 0 -> ServiceStatus.WAITING
        currentDemo -> ServiceStatus.DEMO
        currentState.k == NavState.K_ARRIVED -> ServiceStatus.ARRIVED
        currentState.k != NavState.K_IDLE -> ServiceStatus.NAVIGATING
        else -> ServiceStatus.CONNECTED
    }

    /** Log một sự kiện vòng đời: `event key=value ... t=<ms kể từ lúc service tạo>`. Giá trị không chứa khoảng trắng (dùng [tok]). */
    fun logEvent(line: String) = NavLog.i("$line t=${SystemClock.elapsedRealtime() - serviceStartedAt}")

    fun tok(s: String) = s.replace(Regex("\\s+"), "_")

    /** Ghi đồng thời mã trạng thái BLE, chi tiết kỹ thuật và chuỗi chẩn đoán. */
    fun setBle(state: BleState, status: String, detail: String = "") {
        bleState = state
        bleDetail = detail
        bleStatus = status
    }

    /** Đổi nguồn demo/live tăng [frameEpoch] ngay: khung xếp hàng của nguồn cũ bị bỏ lúc gửi (xem NavForegroundService). */
    @Volatile var demo = false
        set(v) {
            if (field != v) {
                field = v
                epoch.incrementAndGet()
            }
        }
    private val epoch = AtomicInteger()
    val frameEpoch: Int get() = epoch.get()
    @Volatile var serviceRunning = false
    /** elapsedRealtime lúc service tạo; mốc `t=` của log vòng đời kết nối. */
    @Volatile var serviceStartedAt = 0L
    /** Lý do bật service (manual/tile/auto), chỉ để log. */
    @Volatile var startReason = "auto"
    @Volatile var subscribers = 0
    @Volatile var currentState: NavState = NavState.IDLE
    /** Đánh dấu demo của đúng khung đã render vào [currentState] (cập nhật cùng lúc). */
    @Volatile var currentDemo = false
    /** Lỗi gửi notify còn hiệu lực: chỉ xóa khi một lần gửi hoàn tất thành công hoặc phiên/peer kết thúc. */
    @Volatile var deliveryError = false
    @Volatile var idleRemainingSec = -1
    @Volatile var lastPayload = ""
    /** Chuỗi chẩn đoán cho log/khung debug. UI chính hiển thị chữ từ [bleState], không in chuỗi này. */
    @Volatile var bleStatus = "chưa chạy"
    /** Trạng thái BLE dạng mã: service/BlePeripheralServer ghi, UI dịch sang chuỗi theo ngôn ngữ. */
    @Volatile var bleState = BleState.STOPPED
    /** Chi tiết kỹ thuật không dịch (tên exception, số lần retry, số phút auto-stop...). */
    @Volatile var bleDetail = ""
    @Volatile var listenerConnected = false
    /** Chuỗi chẩn đoán cho cảnh báo. UI chính dịch theo [warnCode]. Rỗng = không có. */
    @Volatile var warning = ""
    @Volatile var warnCode = WarnCode.NONE

    /** Callback nhẹ để đánh thức NavForegroundService ngay lập tức khi Maps đổi trạng thái (không tính vào UI listeners). */
    @Volatile var onServiceWake: (() -> Unit)? = null

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** Một cập nhật thật từ Maps (hoặc IDLE khi notification biến mất/listener ngắt). Trả về version mới. */
    fun publishMaps(state: NavState, atMs: Long = SystemClock.elapsedRealtime()): Int {
        val ver = synchronized(this) {
            val next = mapsSnapshot.version + 1
            mapsSnapshot = MapsSnapshot(state, atMs, next)
            next
        }
        if (state.k != NavState.K_IDLE && demo) {
            demo = false // Maps thật thắng demo ngay lập tức (đồng thời bump epoch để bỏ khung demo xếp hàng)
            changed()
        }
        onServiceWake?.invoke()
        return ver
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    val hasListeners: Boolean get() = listeners.isNotEmpty()
    fun changed() { listeners.forEach { it() } }
}
