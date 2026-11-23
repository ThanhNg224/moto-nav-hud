package dev.motonavhud.core

import kotlin.math.abs

/**
 * Bộ phân loại dự phòng bảo thủ cho `largeIcon` (mũi tên dẫn đường) của Google Maps
 * khi phần chữ không có động từ chỉ hướng (ví dụ chỉ có khoảng cách + tên đường).
 *
 * Yêu cầu bắt buộc thân dưới hẹp (1 trục dọc đơn) để không bao giờ nhận nhầm chữ U (quay đầu),
 * vòng xuyến hoặc ảnh minh hoạ thành mũi tên rẽ trái/phải/thẳng. Mơ hồ -> [NavState.M_UNKNOWN].
 */
object ManeuverIconClassifier {
    const val GRID = 16

    fun classify(mask: BooleanArray): Int {
        if (mask.size != GRID * GRID) return NavState.M_UNKNOWN

        var total = 0
        var topCount = 0
        var stemCount = 0
        var topXSum = 0.0
        var stemXSum = 0.0
        var topLeft = 0
        var topRight = 0
        var topMinX = GRID
        var topMaxX = -1
        var stemMinX = GRID
        var stemMaxX = -1
        var minY = GRID
        var maxY = -1

        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                if (!mask[y * GRID + x]) continue
                total++
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (y < 8) {
                    topCount++
                    topXSum += x
                    if (x < topMinX) topMinX = x
                    if (x > topMaxX) topMaxX = x
                    if (x < 8) topLeft++ else topRight++
                }
                if (y >= 10) {
                    stemCount++
                    stemXSum += x
                    if (x < stemMinX) stemMinX = x
                    if (x > stemMaxX) stemMaxX = x
                }
            }
        }
        // Quá ít hoặc gần như phủ kín khung -> không phải mũi tên rõ ràng.
        if (total < 14 || total > (GRID * GRID * 0.60).toInt()) return NavState.M_UNKNOWN
        if (topCount < 6 || stemCount < 4) return NavState.M_UNKNOWN
        if (maxY - minY + 1 < 10) return NavState.M_UNKNOWN

        // Yêu cầu thân dưới (y >= 10) là một cột dọc đơn hẹp (loại chữ U hai chân và vòng xuyến).
        val stemSpanX = stemMaxX - stemMinX + 1
        if (stemSpanX > 4) return NavState.M_UNKNOWN

        val topSpanX = topMaxX - topMinX + 1
        val topCx = topXSum / topCount
        val stemCx = stemXSum / stemCount
        val shift = topCx - stemCx

        // Rẽ trái: thân đơn ở nửa phải, đầu mũi tên vươn mạnh sang mép trái.
        if (stemCx >= 8.0 && topMinX <= 3 && shift <= -2.5 && topLeft >= topRight + 4 && topSpanX >= stemSpanX + 4) {
            return NavState.M_LEFT
        }
        // Rẽ phải: thân đơn ở nửa trái, đầu mũi tên vươn mạnh sang mép phải.
        if (stemCx <= 7.0 && topMaxX >= 12 && shift >= 2.5 && topRight >= topLeft + 4 && topSpanX >= stemSpanX + 4) {
            return NavState.M_RIGHT
        }
        // Đi thẳng: thân đơn chính giữa, đầu đối xứng phía trên và rộng hơn thân.
        if (stemCx in 6.0..9.0 && abs(shift) <= 0.7 && abs(topLeft - topRight) <= 2 && topSpanX >= stemSpanX + 3) {
            return NavState.M_STRAIGHT
        }
        return NavState.M_UNKNOWN
    }
}
