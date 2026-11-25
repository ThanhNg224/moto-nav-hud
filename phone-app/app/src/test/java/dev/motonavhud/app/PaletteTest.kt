package dev.motonavhud.app

import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun ok(fg: Int, bg: Int, min: Double = 4.5) {
        val r = Palette.contrast(fg, bg)
        assertTrue("contrast %.2f < %.1f".format(r, min), r >= min)
    }

    @Test
    fun accentsReadableOnSurfaces() {
        for (t in listOf(Palette.LIGHT, Palette.DARK)) {
            for (accent in listOf(t.teal, t.ok, t.caution, t.orange, t.danger)) {
                ok(accent, t.card)
                ok(accent, t.bg)
                ok(accent, t.bar)
            }
            ok(t.ink, t.card); ok(t.muted, t.card); ok(t.muted, t.bg)
            ok(t.chipText, t.chip)
            ok(t.caution, t.cautionTint)
            for (fill in listOf(t.teal, t.ok, t.caution)) ok(t.onAccent, fill)
        }
    }

    @Test
    fun filledButtons() {
        ok(Palette.WHITE, Palette.FILL_OK)
        ok(Palette.WHITE, Palette.FILL_DANGER)
    }

    @Test
    fun designTokensReadable() {
        for (t in listOf(Palette.LIGHT, Palette.DARK)) {
            ok(t.on, t.bg); ok(t.on, t.s1); ok(t.on, t.s2)
            ok(t.dim, t.bg); ok(t.dim, t.s1); ok(t.dim, t.s2)
            ok(t.accent, t.bg); ok(t.accent, t.s1)
            ok(t.onAccent, t.accent)
            ok(t.onAccentTint, t.accentTint)
            ok(t.onWarnTint, t.warnTint)
            ok(t.onErrorTint, t.errorTint)
            ok(t.error, t.bg); ok(t.error, t.s1)
            ok(t.outline, t.bg, 3.0); ok(t.outline, t.s1, 3.0); ok(t.warn, t.bg, 3.0)
        }
    }
}
