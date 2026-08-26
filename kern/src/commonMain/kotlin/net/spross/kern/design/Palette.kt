package net.spross.kern.design

/** One named color as its light/dark hex pair (0xRRGGBB, no alpha) — see [Palette]. */
data class Swatch(val light: Int, val dark: Int)

/**
 * The app's full color table. The single source for iOS app and Android, which link this
 * directly; the Watch app and both widget extensions don't link kern (avoids pulling a
 * Kotlin/Native framework into a standalone target over a color table) and hand-copy instead —
 * `PaletteParityTest` (`:kern` jvmTest) checks those three against these same values.
 */
object Palette {
    // Surfaces — stone paper with a moss cast, never plain white/gray.
    val background = Swatch(0xF2F1EA, 0x121714)
    val surface = Swatch(0xFBFBF6, 0x1C231E)
    val surfaceTint = Swatch(0xE5E8DE, 0x27302A)

    /**
     * Decorative hairline — card edges, the reveal divider, the ring groove.
     * Deliberately below 3:1: the card's fill and shadow carry its boundary.
     */
    val separator = Swatch(0xD3D6CA, 0x3A443D)

    /**
     * A line that must be SEEN — the answer field's edge is a control boundary,
     * so it owes 3:1 where the decorative hairline does not.
     */
    val borderStrong = Swatch(0x868D7C, 0x707C72)

    // Text — deep forest ink instead of pure black/gray.
    val textPrimary = Swatch(0x1E2620, 0xE9F0EA)
    val textSecondary = Swatch(0x4F584E, 0xADBBAF)

    /** Text/glyphs drawn ON a saturated accent fill (buttons, article pills). */
    val onColor = Swatch(0xFBFBF6, 0x121714)

    // Accents — ink strength.
    val accent = Swatch(0xA23B0B, 0xFF9A6B) // clay
    val teal = Swatch(0x0D566E, 0x6FCFE8) // ocean
    val success = Swatch(0x256232, 0x8AE39B) // forest
    val amber = Swatch(0x87510A, 0xF2C078) // ochre — a near miss, or an answer shown
    val wrong = Swatch(0x99322E, 0xF08D86) // brick — a miss

    /**
     * The consolidated/"grown" rung's own color — NOT [teal]. [teal] sits only ~15°
     * from [der] on the hue wheel (both read as blue at a badge's size); this one is
     * pulled toward green until it reads unmistakably as jade rather than another blue.
     */
    val grown = Swatch(0x0F766E, 0x5EEAD4)

    // Gendered articles.
    val der = Swatch(0x134E85, 0x90CBFF)
    val die = Swatch(0x9A2050, 0xFF9EC0)
    val das = Swatch(0x18602C, 0x6FDC85)
}
