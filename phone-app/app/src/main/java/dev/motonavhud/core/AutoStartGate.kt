package dev.motonavhud.core

/**
 * Bộ điều phối tự động bật BLE service khi Google Maps bắt đầu dẫn đường.
 *
 * Bảo đảm:
 * - Không gửi nhiều yêu cầu startForegroundService trùng nhau khi start đang xử lý ([startInFlight]).
 * - Khi người dùng chủ động dừng HUD giữa chuyến đi, hoặc khi Android chặn start nền / BLE hết retry,
 *   sẽ tạm ngưng tự bật cho tới khi Google Maps hết dẫn đường (K_IDLE) liên tục ít nhất [rearmIdleMs]
 *   (tránh Maps gỡ rồi đăng lại notification giữa chuyến làm bật ngược ý người dùng).
 * - Người dùng bấm Bắt đầu thủ công luôn xóa trạng thái tạm ngưng ngay lập tức.
 */
class AutoStartGate(private val rearmIdleMs: Long = DEFAULT_REARM_IDLE_MS) {
    private var startInFlight = false
    private var suppressed = false
    private var idleSinceMs = -1L

    /**
     * Ghi nhận Maps đang có chuyến đi chủ động (không còn rảnh).
     * Nếu trước đó Maps đã ở trạng thái rảnh liên tục ít nhất [rearmIdleMs], xem đây là chuyến đi mới và mở khóa tự bật.
     */
    @Synchronized
    fun onMapsActive(nowMs: Long) {
        if (suppressed && idleSinceMs >= 0L && nowMs - idleSinceMs >= rearmIdleMs) {
            suppressed = false
        }
        idleSinceMs = -1L
    }

    /** Ghi nhận Maps đang rảnh (K_IDLE). Chỉ mở khóa lại sau khi rảnh liên tục đủ [rearmIdleMs]. */
    @Synchronized
    fun onMapsIdle(nowMs: Long) {
        if (!suppressed) {
            idleSinceMs = -1L
            return
        }
        if (idleSinceMs < 0L) {
            idleSinceMs = nowMs
            return
        }
        if (nowMs - idleSinceMs >= rearmIdleMs) {
            suppressed = false
            idleSinceMs = -1L
        }
    }

    /**
     * Quyết định có nên gọi startForegroundService tự động hay không.
     * Nếu trả `true`, tự động đánh dấu [startInFlight] để chống gọi trùng.
     */
    @Synchronized
    fun tryAcquireAutoStart(enabled: Boolean, serviceRunning: Boolean, btReady: Boolean): Boolean {
        if (!enabled || serviceRunning || !btReady || startInFlight || suppressed) return false
        startInFlight = true
        return true
    }

    /** Người dùng chủ động bấm Bắt đầu (MainActivity / Quick Settings Tile): xóa mọi tạm ngưng. */
    @Synchronized
    fun onManualStartRequested() {
        suppressed = false
        idleSinceMs = -1L
    }

    /** Service đã khởi động thành công (onCreate hoàn tất). */
    @Synchronized
    fun onServiceStarted() {
        startInFlight = false
        suppressed = false
        idleSinceMs = -1L
    }

    /** Service dừng xong (dọn cờ in-flight nếu lỗi khởi động sớm). */
    @Synchronized
    fun onServiceStopped() {
        startInFlight = false
    }

    /** Android từ chối startForegroundService từ nền: ngừng tự bật cho chuyến này để không lặp lỗi mỗi giây. */
    @Synchronized
    fun onAutoStartBlocked() {
        startInFlight = false
        suppressed = true
        idleSinceMs = -1L
    }

    /** Người dùng chủ động bấm Dừng: nếu Maps đang dẫn đường thì tạm ngưng tự bật cho hết chuyến này. */
    @Synchronized
    fun onManualStop(mapsActive: Boolean) {
        startInFlight = false
        if (mapsActive) {
            suppressed = true
            idleSinceMs = -1L
        }
    }

    /** BLE tự bỏ cuộc sau khi hết lượt retry: không để nhịp notification Maps lập tức khởi động lại vòng lặp lỗi. */
    @Synchronized
    fun onBleExhausted(mapsActive: Boolean) {
        startInFlight = false
        if (mapsActive) {
            suppressed = true
            idleSinceMs = -1L
        }
    }

    @Synchronized
    fun isSuppressed(): Boolean = suppressed

    @Synchronized
    fun isStartInFlight(): Boolean = startInFlight

    companion object {
        const val DEFAULT_REARM_IDLE_MS = 30_000L
    }
}
