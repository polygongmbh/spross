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
 * Seventeen fields wired across by hand is where a token silently takes its neighbor's hue
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
        Token("background", Palette.background, ThemeLight.background, ThemeDark.background),
        Token("surface", Palette.surface, ThemeLight.surface, ThemeDark.surface),
        Token("surfaceTint", Palette.surfaceTint, ThemeLight.surfaceTint, ThemeDark.surfaceTint),
        Token("separator", Palette.separator, ThemeLight.separator, ThemeDark.separator),
        Token("borderStrong", Palette.borderStrong, ThemeLight.borderStrong, ThemeDark.borderStrong),
        Token("textPrimary", Palette.textPrimary, ThemeLight.textPrimary, ThemeDark.textPrimary),
        Token("textSecondary", Palette.textSecondary, ThemeLight.textSecondary, ThemeDark.textSecondary),
        Token("onColor", Palette.onColor, ThemeLight.onColor, ThemeDark.onColor),
        Token("accent", Palette.accent, ThemeLight.accent, ThemeDark.accent),
        Token("teal", Palette.teal, ThemeLight.teal, ThemeDark.teal),
        Token("success", Palette.success, ThemeLight.success, ThemeDark.success),
        Token("amber", Palette.amber, ThemeLight.amber, ThemeDark.amber),
        Token("grown", Palette.grown, ThemeLight.grown, ThemeDark.grown),
        Token("wrong", Palette.wrong, ThemeLight.wrong, ThemeDark.wrong),
        Token("der", Palette.der, ThemeLight.der, ThemeDark.der),
        Token("die", Palette.die, ThemeLight.die, ThemeDark.die),
        Token("das", Palette.das, ThemeLight.das, ThemeDark.das),
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
