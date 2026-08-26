package net.spross.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.design.Palette
import net.spross.kern.design.Swatch

/**
 * This cut declares Compose colors, never color VALUES — every token is kern's [Palette]
 * entry, made opaque.
 *
 * Sixteen fields wired across by hand is where a token silently takes its neighbor's hue
 * or its other column, so the mapping is checked rather than trusted: kern's own
 * `PaletteParityTest` cannot hold this table any more, having become the table's source.
 */
class ThemePaletteTest {

    private data class Token(
        val name: String,
        val swatch: Swatch,
        val light: Color,
        val dark: Color,
    )

    private val tokens = listOf(
        Token("background", Palette.background, DlLight.background, DlDark.background),
        Token("surface", Palette.surface, DlLight.surface, DlDark.surface),
        Token("surfaceTint", Palette.surfaceTint, DlLight.surfaceTint, DlDark.surfaceTint),
        Token("separator", Palette.separator, DlLight.separator, DlDark.separator),
        Token("borderStrong", Palette.borderStrong, DlLight.borderStrong, DlDark.borderStrong),
        Token("textPrimary", Palette.textPrimary, DlLight.textPrimary, DlDark.textPrimary),
        Token("textSecondary", Palette.textSecondary, DlLight.textSecondary, DlDark.textSecondary),
        Token("onColor", Palette.onColor, DlLight.onColor, DlDark.onColor),
        Token("accent", Palette.accent, DlLight.accent, DlDark.accent),
        Token("teal", Palette.teal, DlLight.teal, DlDark.teal),
        Token("success", Palette.success, DlLight.success, DlDark.success),
        Token("amber", Palette.amber, DlLight.amber, DlDark.amber),
        Token("wrong", Palette.wrong, DlLight.wrong, DlDark.wrong),
        Token("der", Palette.der, DlLight.der, DlDark.der),
        Token("die", Palette.die, DlLight.die, DlDark.die),
        Token("das", Palette.das, DlLight.das, DlDark.das),
    )

    /**
     * Each field carries its OWN token's hex, from its own column, at full alpha —
     * kern's hexes carry none, and a token that lost it draws nothing at all.
     */
    @Test
    fun everyTokenCarriesItsKernHexInBothColumns() {
        for (token in tokens) {
            assertEquals(opaque(token.swatch.light), token.light.toArgb(), "${token.name} light")
            assertEquals(opaque(token.swatch.dark), token.dark.toArgb(), "${token.name} dark")
        }
    }

    /** A token added to kern reaches this cut, rather than existing only up there. */
    @Test
    fun theTableIsAsWideAsKernsOwn() {
        val declared = Palette.javaClass.declaredFields
            .filter { it.type == Swatch::class.java }
            .map { it.name }
            .toSet()
        assertEquals(declared, tokens.map { it.name }.toSet())
    }

    private fun opaque(hex: Int) = (0xFF000000L or hex.toLong()).toInt()
}
