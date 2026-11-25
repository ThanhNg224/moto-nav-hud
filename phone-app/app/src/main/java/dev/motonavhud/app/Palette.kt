package dev.motonavhud.app

import kotlin.math.pow

/**
 * Bảng màu chung phone + watch (giữ nhận diện thương hiệu). Không phụ thuộc Android để test được trên JVM.
 *
 * Giao diện v0.5: nền gần đen, một màu nhấn teal (cùng màu vòng trên đồng hồ), các hàng phẳng ngăn bằng đường kẻ.
 * Nhóm token mới (bg, s1..s3, line, on, dim, accent...) dùng cho màn hình; nhóm cũ (teal, ok, caution, orange, danger)
 * giữ nguyên làm màu trạng thái của vòng trên đồng hồ ([ArrowPreviewView]).
 * PaletteTest kiểm tỉ lệ tương phản WCAG (chữ nhỏ >= 4.5) cho các cặp dùng thật.
 */
object Palette {
    class Tokens(
        // Nhóm cũ: màu trạng thái vòng (ArrowPreviewView), giữ nguyên ý nghĩa.
        val bg: Int,
        val card: Int,
        val border: Int,
        val ink: Int,
        val muted: Int,
        val divider: Int,
        val bar: Int,
        val chip: Int,
        val chipText: Int,
        val teal: Int,
        val ok: Int,
        val caution: Int,
        val orange: Int,
        val danger: Int,
        /** Màu chữ đặt trên nền accent / teal / ok / caution của cùng bảng. */
        val onAccent: Int,
        val cautionTint: Int,
        // Nhóm token giao diện mới.
        /** Bề mặt: s1 nhẹ nhất (thẻ nhóm), s2 (nút phụ), s3 (nút nhấn mạnh phụ / thanh tiến độ). */
        val s1: Int,
        val s2: Int,
        val s3: Int,
        val line: Int,
        /** Chữ chính / chữ phụ. */
        val on: Int,
        val dim: Int,
        /** Màu nhấn duy nhất của giao diện (teal). */
        val accent: Int,
        val accentTint: Int,
        val onAccentTint: Int,
        val warnTint: Int,
        val onWarnTint: Int,
        /** Chấm trạng thái chờ (không dùng làm chữ). */
        val warn: Int,
        val error: Int,
        val errorTint: Int,
        val onErrorTint: Int,
        /** Viền nút phụ / ô nhập (thành phần giao diện, cần >= 3:1). */
        val outline: Int,
    )

    /** Màu cố định của chiếc đồng hồ vẽ trên màn hình điện thoại (thiết bị luôn tối ở cả hai theme). */
    object Device {
        val BEZEL_LIGHT = c(0x4A5359)
        val BEZEL_MID = c(0x171A1D)
        val BEZEL_DARK = c(0x2C3338)
        val STRAP = c(0x1A1E21)
        val STRAP_EDGE = c(0x2A3034)
        val CROWN = c(0x3A4248)
        /** Theme tối: nền gần đen nuốt mất thân đồng hồ, nên dây sáng hơn và viền ngoài có đường rim mảnh. */
        val STRAP_ON_DARK = c(0x2A3236)
        val STRAP_EDGE_ON_DARK = c(0x4A545A)
        val RIM_ON_DARK = c(0x6B757A)
        val SCREEN = c(0x000000)
        val RING_TRACK = c(0x161E2B)
        val RING = c(0x2DD4BF)
        val TEXT = c(0xF8FAFC)
        val TEXT_DIM = c(0x94A3B8)
    }

    private fun c(rgb: Int) = (0xFF000000L or rgb.toLong()).toInt()

    val FILL_OK = c(0x047857)
    val FILL_DANGER = c(0xB91C1C)
    val WHITE = c(0xFFFFFF)
    val BLACK = c(0x000000)

    val LIGHT = Tokens(
        bg = c(0xF5F6F6), card = c(0xFFFFFF), border = c(0xE2E8F0), ink = c(0x101314), muted = c(0x565F63),
        divider = c(0xDADEE0), bar = c(0xFFFFFF), chip = c(0xE2E8F0), chipText = c(0x334155),
        teal = c(0x00796B), ok = c(0x047857), caution = c(0xB45309), orange = c(0xC2410C), danger = c(0xB91C1C),
        onAccent = WHITE, cautionTint = c(0xFFFBEB),
        s1 = c(0xECEEEF), s2 = c(0xE2E5E6), s3 = c(0xD5D9DB), line = c(0xDADEE0),
        on = c(0x101314), dim = c(0x565F63),
        accent = c(0x00796B), accentTint = c(0xD6EFEA), onAccentTint = c(0x00443B),
        warnTint = c(0xFBE8C2), onWarnTint = c(0x5B3A00), warn = c(0xB87400),
        error = c(0xB3261E), errorTint = c(0xF9DEDC), onErrorTint = c(0x410E0B),
        outline = c(0x7A8387),
    )

    val DARK = Tokens(
        bg = c(0x0B0D0E), card = c(0x121517), border = c(0x23282B), ink = c(0xECEFF0), muted = c(0x9AA4A8),
        divider = c(0x23282B), bar = c(0x0B0D0E), chip = c(0x22272A), chipText = c(0xCBD5E1),
        teal = c(0x2DD4BF), ok = c(0x34D399), caution = c(0xFFD000), orange = c(0xFF5A36), danger = c(0xEF4444),
        onAccent = c(0x02201B), cautionTint = c(0x2A1F08),
        s1 = c(0x121517), s2 = c(0x181C1F), s3 = c(0x22272A), line = c(0x23282B),
        on = c(0xECEFF0), dim = c(0x9AA4A8),
        accent = c(0x2DD4BF), accentTint = c(0x0F2F2A), onAccentTint = c(0x7FEBDB),
        warnTint = c(0x3A2A06), onWarnTint = c(0xFFC95C), warn = c(0xFFB938),
        error = c(0xFF6B57), errorTint = c(0x3B1A16), onErrorTint = c(0xFFB4A8),
        outline = c(0x6B757A),
    )

    fun of(dark: Boolean) = if (dark) DARK else LIGHT

    private fun lum(argb: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    /** Tỉ lệ tương phản WCAG 2.x giữa hai màu opaque. */
    fun contrast(a: Int, b: Int): Double {
        val x = lum(a)
        val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
