package dev.motonavhud.core

/** Trạng thái dẫn đường theo docs/protocol.md (v1). `v`, `q`, `t` do [PayloadBuilder] thêm vào. */
data class NavState(
    val k: Int = K_IDLE,
    val m: Int = M_UNKNOWN,
    val x: Int = 0,
    val d: Int = -1,
    val s: String = "",
    val e: String = "",
    val r: Int = -1,
) {
    companion object {
        const val K_IDLE = 0
        const val K_NAVIGATING = 1
        const val K_REROUTING = 2
        const val K_ARRIVED = 3

        const val M_UNKNOWN = 0
        const val M_STRAIGHT = 1
        const val M_SLIGHT_LEFT = 2
        const val M_LEFT = 3
        const val M_SHARP_LEFT = 4
        const val M_SLIGHT_RIGHT = 5
        const val M_RIGHT = 6
        const val M_SHARP_RIGHT = 7
        const val M_UTURN = 8
        const val M_ROUNDABOUT = 9
        const val M_KEEP_LEFT = 10
        const val M_KEEP_RIGHT = 11
        const val M_ARRIVE = 12
        const val M_DEPART = 13

        val IDLE = NavState()
    }
}
