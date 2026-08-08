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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    surfaceContainer = DlLight.background,
    surfaceContainerHigh = DlLight.surfaceTint, surfaceContainerHighest = DlLight.surfaceTint,
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
    surfaceContainerHigh = DlDark.surfaceTint, surfaceContainerHighest = DlDark.surfaceTint,
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
 * The canonical ramp's WEIGHTS on Android's own font.
 *
 * SF Rounded has no counterpart here and the type ramp stays native, so Roboto keeps the
 * voice while the emphasis pattern carries over: bold heroes and stat values, bold titles,
 * semibold headlines, medium captions, bold badges.
 *
 * Built on first composition rather than on class load, so a plain JVM unit test can read
 * the token tables in this file without a type ramp being raised behind it.
 */
private val SprossTypography: Typography by lazy {
    Typography().run {
        copy(
            headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodySmall = bodySmall.copy(fontWeight = FontWeight.Medium),
            labelMedium = labelMedium.copy(fontWeight = FontWeight.Bold),
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
