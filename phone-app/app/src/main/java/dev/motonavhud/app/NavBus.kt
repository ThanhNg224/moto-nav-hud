package dev.motonavhud.app

import android.os.SystemClock
import dev.motonavhud.core.AutoStartGate
import dev.motonavhud.core.NavState
import java.util.concurrent.CopyOnWriteArraySet

/** Ảnh chụp bất biến của nguồn Maps: đọc/ghi nguyên tử để tránh ghép nhầm state cũ với timestamp mới. */
data class MapsSnapshot(
    val state: NavState,
    val atMs: Long,
    val version: Int,
)

/** Trạng thái dùng chung trong process: nguồn (listener/demo) -> service -> UI. */
object NavBus {
    val autoStartGate = AutoStartGate()
    @Volatile var mapsSnapshot: MapsSnapshot = MapsSnapshot(NavState.IDLE, 0L, 0); private set

    val mapsState: NavState get() = mapsSnapshot.state

    @Volatile var demo = false
    @Volatile var serviceRunning = false
    @Volatile var subscribers = 0
    @Volatile var currentState: NavState = NavState.IDLE
    @Volatile var idleRemainingSec = -1
    @Volatile var lastPayload = ""
    @Volatile var bleStatus = "chưa chạy"
    @Volatile var listenerConnected = false
    /** Cảnh báo hiện trên UI (vd payload không vừa MTU của peer). Rỗng = không có. */
    @Volatile var warning = ""

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
        onServiceWake?.invoke()
        return ver
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    val hasListeners: Boolean get() = listeners.isNotEmpty()
    fun changed() { listeners.forEach { it() } }
}
