package net.spross.kern.design

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every pairing the app draws clears WCAG AA in both schemes:
 * 4.5:1 for anything read as text, 3:1 for a boundary that must be seen ([Palette.borderStrong]).
 * The decorative hairline ([Palette.separator]) is exempt by design.
 */
class PaletteContrastTest {

    private val paper = listOf("background" to Palette.background, "surface" to Palette.surface)

    private val inks = listOf(
        "textPrimary" to Palette.textPrimary, "textSecondary" to Palette.textSecondary,
        "accent" to Palette.accent, "teal" to Palette.teal, "success" to Palette.success,
        "amber" to Palette.amber, "wrong" to Palette.wrong, "settled" to Palette.settled,
        "der" to Palette.der, "die" to Palette.die, "das" to Palette.das,
    )

    @Test
    fun everyInkReadsAsTextOnPaperAndCard() {
        for ((ink, fg) in inks) for ((ground, bg) in paper) assertClears(4.5, ink, fg, ground, bg)
    }

    @Test
    fun textOnAFilledAccentReadsAsText() {
        for ((fill, bg) in inks.drop(2)) assertClears(4.5, "onColor", Palette.onColor, fill, bg)
    }

    @Test
    fun theAnswerFieldsEdgeIsSeen() {
        for ((ground, bg) in paper) assertClears(3.0, "borderStrong", Palette.borderStrong, ground, bg)
    }

    private fun assertClears(floor: Double, fgName: String, fg: Swatch, bgName: String, bg: Swatch) {
        for ((scheme, pair) in listOf("light" to (fg.light to bg.light), "dark" to (fg.dark to bg.dark))) {
            val ratio = contrast(pair.first, pair.second)
            assertTrue(ratio >= floor, "$fgName on $bgName ($scheme) is %.2f:1, under %.1f:1".format(ratio, floor))
        }
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
