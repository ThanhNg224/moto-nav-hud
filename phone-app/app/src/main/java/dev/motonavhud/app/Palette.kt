package dev.motonavhud.app

import kotlin.math.pow

/**
 * Bảng màu chung phone + watch (giữ nhận diện thương hiệu). Không phụ thuộc Android để test được trên JVM.
 *
 * Tỉ lệ tương phản WCAG đã tính (chữ nhỏ cần >= 4.5, chữ lớn >= 3); PaletteTest kiểm lại các cặp này.
 * Sáng (trên thẻ trắng / nền #F1F5F9):  teal #0F766E 5.47/5.00, ok #047857 5.48/5.01, caution #B45309 5.02/4.58,
 *   orange #C2410C 5.18/4.73, danger #B91C1C 6.47/5.91, muted #475569 7.58/6.92.
 * Tối (trên thẻ #151B24 / nền #0B0E14):  teal #2DD4BF 9.29/10.38, ok #34D399 9.00/10.05, caution #FFD000 11.76/13.13,
 *   orange #FF5A36 5.57/6.23, danger #EF4444 4.60/5.13, muted #94A3B8 6.75/7.53.
 * Chữ trên nền màu: sáng trắng trên teal 5.47, ok 5.48, caution 5.02; tối đen trên teal 11.28, ok 10.92, caution 14.27;
 *   trắng trên fillOk #047857 5.48, fillDanger #B91C1C 6.47. Chữ caution trên thẻ cảnh báo: sáng 4.84, tối 11.00.
 */
object Palette {
    class Tokens(
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
        /** Màu chữ đặt trên nền teal/ok/caution của cùng bảng. */
        val onAccent: Int,
        val cautionTint: Int,
    )

    private fun c(rgb: Int) = (0xFF000000L or rgb.toLong()).toInt()

    val FILL_OK = c(0x047857)
    val FILL_DANGER = c(0xB91C1C)
    val WHITE = c(0xFFFFFF)
    val BLACK = c(0x000000)

    val LIGHT = Tokens(
        bg = c(0xF1F5F9), card = c(0xFFFFFF), border = c(0xE2E8F0), ink = c(0x0F172A), muted = c(0x475569),
        divider = c(0xE2E8F0), bar = c(0xFFFFFF), chip = c(0xE2E8F0), chipText = c(0x334155),
        teal = c(0x0F766E), ok = c(0x047857), caution = c(0xB45309), orange = c(0xC2410C), danger = c(0xB91C1C),
        onAccent = WHITE, cautionTint = c(0xFFFBEB),
    )

    val DARK = Tokens(
        bg = c(0x0B0E14), card = c(0x151B24), border = c(0x243042), ink = c(0xF8FAFC), muted = c(0x94A3B8),
        divider = c(0x243042), bar = c(0x11161F), chip = c(0x1F2937), chipText = c(0xCBD5E1),
        teal = c(0x2DD4BF), ok = c(0x34D399), caution = c(0xFFD000), orange = c(0xFF5A36), danger = c(0xEF4444),
        onAccent = BLACK, cautionTint = c(0x2A1F08),
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
