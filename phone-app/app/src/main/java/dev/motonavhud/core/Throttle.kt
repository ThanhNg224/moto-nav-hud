package dev.motonavhud.core

import kotlin.math.abs

/**
 * Giữ tin mới nhất và quyết định gửi gì, khi nào (docs/protocol.md v1.1).
 * - [offer] = một cập nhật THẬT từ nguồn (Maps/demo); chỉ giữ tin mới nhất.
 * - [poll] gọi định kỳ: gửi khi k/m/x/s đổi hoặc d lệch >= 10 m, hoặc heartbeat 1000 ms.
 * - `a` = tuổi của lần cập nhật thật gần nhất; heartbeat không làm mới. Với k=0 (không có dữ liệu
 *   dẫn đường để già đi) `a` luôn là 0.
 * - Nguồn im quá [expiryMs] thì gửi k=0 (k=2 nếu đang tính lại), không bao giờ giữ k=1 với dữ liệu cũ.
 *   k=1 dùng [navStaleMs] (75 s, Maps có thể không đăng lại thông báo tới ~60 s); các k khác dùng [staleMs] (15 s).
 * - `z` do bên gọi tạo mỗi lần service khởi động; `q` tăng nghiêm ngặt từ 1; `i` tăng khi (m,x,s) đổi,
 *   khi bắt đầu dẫn đường (k về 1) hoặc khi d tăng vọt (> [stepJumpM]) sang bước mới.
 */
class Throttle(
    private val clock: () -> Long,
    val z: Int,
    private val heartbeatMs: Long = 1000,
    private val distanceStep: Int = 10,
    private val staleMs: Long = 15_000,
    private val navStaleMs: Long = 75_000,
    private val stepJumpM: Int = 30,
) {
    /** Rendered = trạng thái + tuổi nguồn tại một thời điểm gửi/đọc cụ thể. */
    data class Rendered(val state: NavState, val a: Int)

    /**
     * Snapshot giữ MỐC THỜI GIAN NGUỒN ([sourceAt]); tuổi `a` và hết hạn được tính lại bằng [render]
     * ngay lúc đọc/gửi thật, nên snapshot xếp hàng lâu không bao giờ mang tuổi cũ hay k=1 đã hết hạn.
     */
    data class Emission(
        val state: NavState, val z: Int, val q: Int, val i: Int,
        val sourceAt: Long?, val staleMs: Long = 15_000, val navStaleMs: Long = 75_000,
    ) {
        fun render(now: Long): Rendered {
            if (state.k == NavState.K_IDLE) return Rendered(state, 0)
            val at = sourceAt ?: return Rendered(NavState.IDLE, 0)
            val age = (now - at).coerceIn(0, 999_999L)
            if (now - at > expiryMs(state.k, staleMs, navStaleMs) && state.k != NavState.K_REROUTING) {
                return Rendered(NavState.IDLE, 0)
            }
            return Rendered(state, age.toInt())
        }
    }

    private var latest: NavState = NavState.IDLE
    private var lastRealAt: Long? = null
    private var lastSent: NavState? = null
    private var lastSentAt = 0L
    private var q = 0
    private var stepId = 1

    @Synchronized
    fun offer(state: NavState, atMs: Long = clock()) {
        val prev = latest
        if (lastRealAt != null && newStep(prev, state)) stepId++
        latest = state
        lastRealAt = atMs
    }

    @Synchronized
    fun poll(): Emission? {
        val now = clock()
        val eff = effective(now)
        val prev = lastSent
        val due = prev == null || now - lastSentAt >= heartbeatMs || changed(prev, eff)
        if (!due) return null
        lastSent = eff
        lastSentAt = now
        q++
        return Emission(eff, z, q, stepId, lastRealAt, staleMs, navStaleMs)
    }

    private fun effective(now: Long): NavState {
        val at = lastRealAt ?: return NavState.IDLE
        if (now - at <= expiryMs(latest.k, staleMs, navStaleMs)) return latest
        return if (latest.k == NavState.K_REROUTING) latest else NavState.IDLE
    }

    private fun newStep(a: NavState, b: NavState): Boolean {
        if (b.k != NavState.K_NAVIGATING) return false
        if (a.k != NavState.K_NAVIGATING) return true
        if (a.m != b.m || a.x != b.x || a.s != b.s) return true
        return a.d >= 0 && b.d >= 0 && b.d - a.d > stepJumpM
    }

    private fun changed(a: NavState, b: NavState): Boolean {
        if (a.k != b.k || a.m != b.m || a.x != b.x || a.s != b.s) return true
        if (a.d == b.d) return false
        if (a.d < 0 || b.d < 0) return true
        return abs(a.d - b.d) >= distanceStep
    }

    companion object {
        /** Ngưỡng im (ms) trước khi nguồn bị coi là hết hạn; dùng chung cho [effective] và [Emission.render]. */
        internal fun expiryMs(k: Int, staleMs: Long, navStaleMs: Long): Long =
            if (k == NavState.K_NAVIGATING) navStaleMs else staleMs
    }
}
