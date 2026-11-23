package dev.motonavhud.app

import android.os.SystemClock
import dev.motonavhud.core.AutoStartGate
import dev.motonavhud.core.NavState
import java.util.concurrent.CopyOnWriteArraySet

/** Trạng thái dùng chung trong process: nguồn (listener/demo) -> service -> UI. */
object NavBus {
    val autoStartGate = AutoStartGate()
    @Volatile var mapsState: NavState = NavState.IDLE; private set
    @Volatile var mapsStateAt: Long = 0L; private set
    @Volatile var mapsVersion: Int = 0; private set

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

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** Một cập nhật thật từ Maps (hoặc IDLE khi notification biến mất/listener ngắt). Trả về version mới. */
    @Synchronized
    fun publishMaps(state: NavState, atMs: Long = SystemClock.elapsedRealtime()): Int {
        mapsState = state
        mapsStateAt = atMs
        return ++mapsVersion
    }

    /** Làm mới mốc thời gian khi xác nhận notification Maps đang chọn vẫn còn sống (đứng đèn đỏ/đường thẳng). Trả null nếu đã có bản mới hơn. */
    @Synchronized
    fun touchMaps(expectedVersion: Int, atMs: Long = SystemClock.elapsedRealtime()): Int? {
        if (mapsVersion != expectedVersion || mapsState.k == NavState.K_IDLE) return null
        mapsStateAt = atMs
        return ++mapsVersion
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    val hasListeners: Boolean get() = listeners.isNotEmpty()
    fun changed() { listeners.forEach { it() } }
}
