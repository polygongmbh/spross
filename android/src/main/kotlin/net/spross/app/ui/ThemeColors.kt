package net.spross.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import net.spross.kern.design.Palette
import net.spross.kern.design.Swatch

// Spross colors, Android cut. The rest of the tokens are Theme.kt.
//
// kern's `Palette` is the canonical table and every hex below is READ from it — this cut
// declares only the Compose `Color` type around those values, never a value of its own.
// The spacing and the type ramp stay native on each platform.
//
// Every pairing clears WCAG AA — 4.5:1 for text, 3:1 for controls — in BOTH schemes, and
// it does so because the values are read rather than re-picked. Two rules keep it that way:
//
// 1. Accents are cut at INK strength, not fill strength: the light values read as text on
//    the paper AND on their own 14 % wash, which is the tightest constraint.
// 2. Anything drawn ON an accent fill takes `onColor`, never `Color.White` — the dark
//    scheme's accents are pastels, where white sinks to ~1.8:1.

/**
 * The palette one scheme wide.
 *
 * The two instances below are the two columns of the canonical table; a screen never
 * picks between them, [SprossTheme] provides the one the system asked for.
 */
@Immutable
class ThemeColors(
    /** Screen background — stone paper with a moss cast, never plain white or gray. */
    val background: Color,
    /** Card and panel fill. */
    val surface: Color,
    /** Recessed chip, pill and track fill. */
    val surfaceTint: Color,
    /**
     * Decorative hairline — card edges, the reveal divider, the ring groove.
     * Deliberately below 3:1: a card's fill and shadow carry its boundary.
     */
    val separator: Color,
    /** A line that must be SEEN — a control edge owes 3:1 where the hairline does not. */
    val borderStrong: Color,
    /** Primary ink, deep forest instead of pure black. */
    val textPrimary: Color,
    /** Secondary ink; also the fallback for an article whose gender the box cannot name. */
    val textSecondary: Color,
    /** Text and glyphs drawn ON a saturated accent fill — never `Color.White`. */
    val onColor: Color,
    /** Clay: the primary accent. */
    val accent: Color,
    /** Ocean: the secondary accent. */
    val teal: Color,
    /** Forest: right answers, consolidated cards, foliage. */
    val success: Color,
    /** Ochre: the reveal, the tough answer, the word still being learnt — never red. */
    val amber: Color,
    /**
     * Jade — the consolidated/"grown" Sprosse's own color. Not [teal]: that one sits too
     * close to [der] on the hue wheel to read as anything but another blue at a badge's
     * size, so this one is its own token, pulled toward green instead.
     */
    val grown: Color,
    /**
     * Muted brick. The aggregate progress bar and the learner's own "unknown" verdict
     * wear it; a card telling someone they were wrong never does.
     */
    val wrong: Color,
    /** Masculine article blue. */
    val der: Color,
    /** Feminine article berry. */
    val die: Color,
    /** German's neuter green — a two-gender language never reaches it. */
    val das: Color,
) {
    /**
     * The tinted-pill fill: an accent at 14 % over the card surface, flattened.
     * Opaque rather than translucent, so a pill reads the same whatever it sits on.
     */
    fun wash(color: Color): Color = color.copy(alpha = 0.14f).compositeOver(surface)
}

/** A canonical hex (0xRRGGBB, no alpha) as the opaque Compose color it names. */
private fun Int.opaque(): Color = Color(0xFF000000L or toLong())

/**
 * A kern [Swatch] in the scheme currently on screen.
 *
 * Reads the system setting [SprossTheme] itself reads, so a color resolved here and one
 * taken from [Theme.colors] can never land in different columns of the same table.
 */
@Composable
@ReadOnlyComposable
fun Swatch.tint(): Color = if (isSystemInDarkTheme()) dark.opaque() else light.opaque()

/** The light column of the canonical table. */
val ThemeLight = ThemeColors(
    background = Palette.background.light.opaque(),
    surface = Palette.surface.light.opaque(),
    surfaceTint = Palette.surfaceTint.light.opaque(),
    separator = Palette.separator.light.opaque(),
    borderStrong = Palette.borderStrong.light.opaque(),
    textPrimary = Palette.textPrimary.light.opaque(),
    textSecondary = Palette.textSecondary.light.opaque(),
    onColor = Palette.onColor.light.opaque(),
    accent = Palette.accent.light.opaque(),
    teal = Palette.teal.light.opaque(),
    success = Palette.success.light.opaque(),
    amber = Palette.amber.light.opaque(),
    grown = Palette.grown.light.opaque(),
    wrong = Palette.wrong.light.opaque(),
    der = Palette.der.light.opaque(),
    die = Palette.die.light.opaque(),
    das = Palette.das.light.opaque(),
)

/** The dark column of the canonical table. */
val ThemeDark = ThemeColors(
    background = Palette.background.dark.opaque(),
    surface = Palette.surface.dark.opaque(),
    surfaceTint = Palette.surfaceTint.dark.opaque(),
    separator = Palette.separator.dark.opaque(),
    borderStrong = Palette.borderStrong.dark.opaque(),
    textPrimary = Palette.textPrimary.dark.opaque(),
    textSecondary = Palette.textSecondary.dark.opaque(),
    onColor = Palette.onColor.dark.opaque(),
    accent = Palette.accent.dark.opaque(),
    teal = Palette.teal.dark.opaque(),
    success = Palette.success.dark.opaque(),
    amber = Palette.amber.dark.opaque(),
    grown = Palette.grown.dark.opaque(),
    wrong = Palette.wrong.dark.opaque(),
    der = Palette.der.dark.opaque(),
    die = Palette.die.dark.opaque(),
    das = Palette.das.dark.opaque(),
)

// The M3 roles the tokens answer for. There is no container TIER in the canonical
// palette — its container IS the accent's own 14 % wash, so the container roles are
// composited rather than given hexes of their own, and `on*Container` is the accent
// itself: exactly the tinted-pill pairing the contrast note was cut for.
// The three fills (card, paper, recessed) lie on M3's five-step container ramp, which
// runs the other way in the dark, where the paper is the deepest tone.

internal val SprossLight = lightColorScheme(
    primary = ThemeLight.accent, onPrimary = ThemeLight.onColor,
    primaryContainer = ThemeLight.wash(ThemeLight.accent), onPrimaryContainer = ThemeLight.accent,
    secondary = ThemeLight.teal, onSecondary = ThemeLight.onColor,
    secondaryContainer = ThemeLight.wash(ThemeLight.teal), onSecondaryContainer = ThemeLight.teal,
    tertiary = ThemeLight.success, onTertiary = ThemeLight.onColor,
    tertiaryContainer = ThemeLight.wash(ThemeLight.success), onTertiaryContainer = ThemeLight.success,
    error = ThemeLight.wrong, onError = ThemeLight.onColor,
    errorContainer = ThemeLight.wash(ThemeLight.wrong), onErrorContainer = ThemeLight.wrong,
    background = ThemeLight.background, onBackground = ThemeLight.textPrimary,
    surface = ThemeLight.surface, onSurface = ThemeLight.textPrimary,
    surfaceVariant = ThemeLight.surfaceTint, onSurfaceVariant = ThemeLight.textSecondary,
    // why: M3 would otherwise wash every elevated surface toward `primary` — a Spross
    // card takes its boundary from fill, hairline and shadow, never from a tonal tint.
    surfaceTint = Color.Transparent,
    outline = ThemeLight.borderStrong, outlineVariant = ThemeLight.separator,
    surfaceContainerLowest = ThemeLight.surface, surfaceContainerLow = ThemeLight.surface,
    // why: the container tiers are what a MENU and a DIALOG are drawn on, and nothing else
    // reads them now that every panel takes the card recipe directly. Pointed at the paper
    // a card is cut from: `surfaceContainer` was the page background itself, so an open
    // language menu was invisible but for its shadow, and the reset dialog arrived in the
    // recessed mint the chips wear.
    surfaceContainer = ThemeLight.surface,
    surfaceContainerHigh = ThemeLight.surface, surfaceContainerHighest = ThemeLight.surfaceTint,
    surfaceBright = ThemeLight.surface, surfaceDim = ThemeLight.surfaceTint,
)

internal val SprossDark = darkColorScheme(
    primary = ThemeDark.accent, onPrimary = ThemeDark.onColor,
    primaryContainer = ThemeDark.wash(ThemeDark.accent), onPrimaryContainer = ThemeDark.accent,
    secondary = ThemeDark.teal, onSecondary = ThemeDark.onColor,
    secondaryContainer = ThemeDark.wash(ThemeDark.teal), onSecondaryContainer = ThemeDark.teal,
    tertiary = ThemeDark.success, onTertiary = ThemeDark.onColor,
    tertiaryContainer = ThemeDark.wash(ThemeDark.success), onTertiaryContainer = ThemeDark.success,
    error = ThemeDark.wrong, onError = ThemeDark.onColor,
    errorContainer = ThemeDark.wash(ThemeDark.wrong), onErrorContainer = ThemeDark.wrong,
    background = ThemeDark.background, onBackground = ThemeDark.textPrimary,
    surface = ThemeDark.surface, onSurface = ThemeDark.textPrimary,
    surfaceVariant = ThemeDark.surfaceTint, onSurfaceVariant = ThemeDark.textSecondary,
    surfaceTint = Color.Transparent,
    outline = ThemeDark.borderStrong, outlineVariant = ThemeDark.separator,
    surfaceContainerLowest = ThemeDark.background, surfaceContainerLow = ThemeDark.surface,
    surfaceContainer = ThemeDark.surface,
    surfaceContainerHigh = ThemeDark.surface, surfaceContainerHighest = ThemeDark.surfaceTint,
    surfaceBright = ThemeDark.surfaceTint, surfaceDim = ThemeDark.background,
)
