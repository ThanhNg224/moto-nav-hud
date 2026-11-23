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
}
