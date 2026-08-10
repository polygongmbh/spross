package net.spross.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import net.spross.app.R
import net.spross.kern.model.Gender
import net.spross.kern.model.articleGender

// Spross design tokens, Android cut.
//
// `App/Sources/Design/Theme.swift` is the canonical table; every hex below is copied
// from it value for value, and kern's `PaletteParityTest` fails the fast gate when the
// two drift. Only the RULES live in kern — the hex pairs, the spacing and the type ramp
// stay native on each platform (`docs/portability.md` § Stays native).
//
// Every pairing clears WCAG AA — 4.5:1 for text, 3:1 for controls — in BOTH schemes, and
// it does so because the values are copied rather than re-picked. Two rules keep it that way:
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
class DlColors(
    /** Screen background — stone paper with a moss cast, never plain white or grey. */
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

/** The light column of the canonical table. */
val DlLight = DlColors(
    background = Color(0xFFF2F1EA),
    surface = Color(0xFFFBFBF6),
    surfaceTint = Color(0xFFE5E8DE),
    separator = Color(0xFFD3D6CA),
    borderStrong = Color(0xFF868D7C),
    textPrimary = Color(0xFF1E2620),
    textSecondary = Color(0xFF4F584E),
    onColor = Color(0xFFFBFBF6),
    accent = Color(0xFFA23B0B),
    teal = Color(0xFF0D566E),
    success = Color(0xFF256232),
    amber = Color(0xFF87510A),
    wrong = Color(0xFF99322E),
    der = Color(0xFF134E85),
    die = Color(0xFF9A2050),
    das = Color(0xFF18602C),
)

/** The dark column of the canonical table. */
val DlDark = DlColors(
    background = Color(0xFF121714),
    surface = Color(0xFF1C231E),
    surfaceTint = Color(0xFF27302A),
    separator = Color(0xFF3A443D),
    borderStrong = Color(0xFF707C72),
    textPrimary = Color(0xFFE9F0EA),
    textSecondary = Color(0xFFADBBAF),
    onColor = Color(0xFF121714),
    accent = Color(0xFFFF9A6B),
    teal = Color(0xFF6FCFE8),
    success = Color(0xFF8AE39B),
    amber = Color(0xFFF2C078),
    wrong = Color(0xFFF08D86),
    der = Color(0xFF90CBFF),
    die = Color(0xFFFF9EC0),
    das = Color(0xFF6FDC85),
)

// The M3 roles the tokens answer for. There is no container TIER in the canonical
// palette — its container IS the accent's own 14 % wash, so the container roles are
// composited rather than given hexes of their own, and `on*Container` is the accent
// itself: exactly the tinted-pill pairing the contrast note was cut for.
// The three fills (card, paper, recessed) lie on M3's five-step container ramp, which
// runs the other way in the dark, where the paper is the deepest tone.

private val SprossLight = lightColorScheme(
    primary = DlLight.accent, onPrimary = DlLight.onColor,
    primaryContainer = DlLight.wash(DlLight.accent), onPrimaryContainer = DlLight.accent,
    secondary = DlLight.teal, onSecondary = DlLight.onColor,
    secondaryContainer = DlLight.wash(DlLight.teal), onSecondaryContainer = DlLight.teal,
    tertiary = DlLight.success, onTertiary = DlLight.onColor,
    tertiaryContainer = DlLight.wash(DlLight.success), onTertiaryContainer = DlLight.success,
    error = DlLight.wrong, onError = DlLight.onColor,
    errorContainer = DlLight.wash(DlLight.wrong), onErrorContainer = DlLight.wrong,
    background = DlLight.background, onBackground = DlLight.textPrimary,
    surface = DlLight.surface, onSurface = DlLight.textPrimary,
    surfaceVariant = DlLight.surfaceTint, onSurfaceVariant = DlLight.textSecondary,
    // why: M3 would otherwise wash every elevated surface toward `primary` — a Spross
    // card takes its boundary from fill, hairline and shadow, never from a tonal tint.
    surfaceTint = Color.Transparent,
    outline = DlLight.borderStrong, outlineVariant = DlLight.separator,
    surfaceContainerLowest = DlLight.surface, surfaceContainerLow = DlLight.surface,
    // why: the container tiers are what a MENU and a DIALOG are drawn on, and nothing else
    // reads them now that every panel takes the card recipe directly. Pointed at the paper
    // a card is cut from: `surfaceContainer` was the page background itself, so an open
    // language menu was invisible but for its shadow, and the reset dialog arrived in the
    // recessed mint the chips wear.
    surfaceContainer = DlLight.surface,
    surfaceContainerHigh = DlLight.surface, surfaceContainerHighest = DlLight.surfaceTint,
    surfaceBright = DlLight.surface, surfaceDim = DlLight.surfaceTint,
)

private val SprossDark = darkColorScheme(
    primary = DlDark.accent, onPrimary = DlDark.onColor,
    primaryContainer = DlDark.wash(DlDark.accent), onPrimaryContainer = DlDark.accent,
    secondary = DlDark.teal, onSecondary = DlDark.onColor,
    secondaryContainer = DlDark.wash(DlDark.teal), onSecondaryContainer = DlDark.teal,
    tertiary = DlDark.success, onTertiary = DlDark.onColor,
    tertiaryContainer = DlDark.wash(DlDark.success), onTertiaryContainer = DlDark.success,
    error = DlDark.wrong, onError = DlDark.onColor,
    errorContainer = DlDark.wash(DlDark.wrong), onErrorContainer = DlDark.wrong,
    background = DlDark.background, onBackground = DlDark.textPrimary,
    surface = DlDark.surface, onSurface = DlDark.textPrimary,
    surfaceVariant = DlDark.surfaceTint, onSurfaceVariant = DlDark.textSecondary,
    surfaceTint = Color.Transparent,
    outline = DlDark.borderStrong, outlineVariant = DlDark.separator,
    surfaceContainerLowest = DlDark.background, surfaceContainerLow = DlDark.surface,
    surfaceContainer = DlDark.surface,
    surfaceContainerHigh = DlDark.surface, surfaceContainerHighest = DlDark.surfaceTint,
    surfaceBright = DlDark.surfaceTint, surfaceDim = DlDark.background,
)

/**
 * One corner family, three sizes — control 14, tile 20, card 28.
 *
 * The extra-small slot takes the control radius too: the only thing reading it here is a
 * text field, and a text field is a control. A filled button asks for a full pill unless
 * its call site says otherwise, so those pass `shapes.small` by hand. Compose has no
 * continuous (squircle) corner; a plain rounded one is the accepted difference.
 */
val SprossShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The rounded face the canonical ramp asks for, in the nearest thing Android has.
 *
 * SF Rounded is Apple's and ships with no counterpart here, so the ramp stayed on Roboto —
 * and Roboto at M3's stock tracking is what made this cut read a decade older than the iOS
 * one. Nunito carries the same voice: humanist, rounded terminals, apertures wide enough to
 * hold up at caption size. It covers Latin, Latin-ext and Cyrillic, so a Ukrainian target is
 * set in the same face as a German one, and ONE variable file (`wght` 200–1000) serves every
 * weight the ramp names. Its licence is `android/licenses/Nunito-OFL.txt`.
 *
 * `♀` and `✔` fall outside its coverage and drop to the platform's symbol font. That is where
 * those two belong anyway — they are marks, not text.
 */
private val Nunito: FontFamily by lazy {
    FontFamily(
        weightedNunito(FontWeight.Normal),
        weightedNunito(FontWeight.Medium),
        weightedNunito(FontWeight.SemiBold),
        weightedNunito(FontWeight.Bold),
    )
}

/**
 * One instance of the variable file, pinned to one point on its weight axis.
 *
 * Both halves are needed and they are not the same thing: the [FontWeight] is what the ramp
 * is MATCHED on, `variationSettings` is what actually cuts the outline. Register the four
 * without the settings and every one of them renders at the file's default — ExtraLight.
 *
 * The variation-settings overload is still marked experimental; it is the only way to cut a
 * variable file from Compose, and the alternative is four static faces at three times the size.
 */
@OptIn(ExperimentalTextApi::class)
private fun weightedNunito(weight: FontWeight) =
    Font(
        R.font.nunito,
        weight,
        variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
    )

/**
 * A ramp entry on the rounded face, set SOLID.
 *
 * Tracking goes to zero everywhere. M3 cuts Roboto with up to +0.5 sp, which is the single
 * loudest "stock Android" tell in the whole ramp, and the canonical ramp is set solid — the
 * iOS cut inherits SF's own near-zero tracking without asking for it.
 *
 * A null [weight] or [size] keeps whatever the M3 slot already carries. A resized slot takes
 * its leading with it: M3's line heights are cut for M3's sizes, and holding one while moving
 * the other tightens the block instead of just growing the letters.
 */
private fun TextStyle.rounded(weight: FontWeight? = null, size: TextUnit? = null) = copy(
    fontFamily = Nunito,
    fontWeight = weight ?: fontWeight,
    fontSize = size ?: fontSize,
    lineHeight = if (size == null || !lineHeight.isSpecified) {
        lineHeight
    } else {
        (lineHeight.value + (size.value - fontSize.value)).sp
    },
    letterSpacing = 0.sp,
)

/**
 * The canonical ramp's WEIGHTS and SIZES on the rounded face.
 *
 * The emphasis pattern is the iOS one: bold heroes and stat values, bold titles, semibold
 * headlines, medium captions, bold badges. Every slot is listed, including the ones that
 * keep their M3 value — a slot left out is a slot still set in Roboto.
 *
 * Each slot below names the `DL.Fonts` token it answers for, and is cut to match it. M3's
 * own ramp runs a step under the canonical one across the middle of the range, which is why
 * the Android cut read denser and paler than the iOS one at the same palette: its two
 * workhorses were 14 sp and 16 sp Regular where the canonical ramp sets body and headline
 * a size up. The point sizes are Android's own — only the ROLE mapping is shared, and the
 * numbers themselves live in `App/Sources/Design/Theme.swift` for the iOS cut.
 *
 * Built on first composition rather than on class load, so a plain JVM unit test can read
 * the token tables in this file without a type ramp being raised behind it.
 */
private val SprossTypography: Typography by lazy {
    Typography().run {
        copy(
            displayLarge = displayLarge.rounded(),
            displayMedium = displayMedium.rounded(),
            // The emoji face of a Heute card; a glyph, not a ramp entry.
            displaySmall = displaySmall.rounded(),
            // hero — screen titles.
            headlineLarge = headlineLarge.rounded(FontWeight.Bold, 34.sp),
            // The card headword, and the one slot with NO iOS counterpart: it sits between
            // `title` and `hero`, above the 22 pt the iOS card actually sets. Kept there
            // because the headword is the whole point of a card and Android has the room —
            // but it is this cut's own call, not a token copied across, and a word too wide
            // for its card steps down instead of breaking ([Headword]).
            headlineMedium = headlineMedium.rounded(FontWeight.Bold),
            // statValue — section headings and the numbers on a stat tile. Bold is the
            // point of it: a stat set Regular is the one thing on the screen that has to
            // be read at a glance and reads as body copy instead.
            headlineSmall = headlineSmall.rounded(FontWeight.Bold),
            // title — a card's own heading.
            titleLarge = titleLarge.rounded(FontWeight.Bold),
            // headline — the workhorse row label.
            titleMedium = titleMedium.rounded(FontWeight.SemiBold, 17.sp),
            // headline, minor.
            titleSmall = titleSmall.rounded(FontWeight.SemiBold, 15.sp),
            // body — real copy.
            bodyLarge = bodyLarge.rounded(size = 17.sp),
            // subheadline — supporting copy under a heading.
            bodyMedium = bodyMedium.rounded(size = 15.sp),
            // caption.
            bodySmall = bodySmall.rounded(FontWeight.Medium),
            // headline — button text, which M3 leaves at body weight.
            labelLarge = labelLarge.rounded(FontWeight.SemiBold, 15.sp),
            // badge.
            labelMedium = labelMedium.rounded(FontWeight.Bold, 13.sp),
            labelSmall = labelSmall.rounded(FontWeight.Medium),
        )
    }
}

/** Spacing, the same six steps and the same numbers the canonical table names. */
object DlSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val LocalDlColors = staticCompositionLocalOf { DlLight }

/**
 * The tokens M3 has no role for — amber, the article trio, the on-accent ink — read
 * through here, `MaterialTheme.colorScheme`'s sibling. Everything that DOES have a role
 * is read from the color scheme, so a component picks it up without being told.
 */
object Dl {
    val colors: DlColors
        @Composable @ReadOnlyComposable get() = LocalDlColors.current
}

/**
 * The hue an ARTICLE wears: masculine blue, feminine berry, German's neuter green,
 * and nothing where the box cannot name a gender.
 *
 * Which article marks which gender is content, so [articleGender] answers it once for
 * every surface; only the three colors are this platform's, and they stay here.
 */
fun DlColors.articleTint(article: String?): Color? = when (articleGender(article)) {
    Gender.Masculine -> der
    Gender.Feminine -> die
    Gender.Neuter -> das
    null -> null
}

@Composable
fun SprossTheme(content: @Composable () -> Unit) {
    // why: the system setting decides, which is why both columns exist — the iOS cut
    // reaches the same place through its dynamic color providers.
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalDlColors provides if (dark) DlDark else DlLight) {
        MaterialTheme(
            colorScheme = if (dark) SprossDark else SprossLight,
            shapes = SprossShapes,
            typography = SprossTypography,
            content = content,
        )
    }
}
