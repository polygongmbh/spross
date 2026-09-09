package net.spross.kern.design

import net.spross.kern.repoText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Many surfaces draw Spross and only one of them can be the palette: [Palette] is it.
 *
 * The iOS app and Android link kern, so they read the hex values from [Palette] itself and
 * cannot disagree with it. Everything else hand-copies, and a copy is a thing that drifts:
 * the watch app and the two widget extensions do NOT link kern — a color table is no reason
 * to pull a Kotlin/Native framework into a tiny standalone target — the website and the
 * print sheets cannot, and Android's window background is XML the theme reads before Compose
 * draws a frame. This test reads all of them as text and holds every value to [Palette].
 *
 * A copy keeps only the tokens it uses — the phone widget needs a handful of hues and
 * nothing else — so the check runs the other way round: every token a file DECLARES must
 * name a canonical token and carry its hex. The CSS copies also declare tokens that are
 * theirs alone (foliage, paper stock, a shadow), so those are named exempt rather than
 * skipped, and a new one has to be classified before it can ship.
 *
 * The watch APP is the one copy that is a single column: it renders on black and says so,
 * as do the print sheets. Every other copy declares PAIRS and is held to both columns, even
 * the complication that only ever wears the dark half — a half-checked table is where the
 * other half drifts.
 *
 * Gradle does not see these Swift, CSS and XML sources from the classpath;
 * `kern/build.gradle.kts` names the trees as inputs so a palette edit re-runs this test.
 */
class PaletteParityTest {

    /**
     * The canonical table itself, so a token renamed or added in [Palette] breaks here
     * rather than silently emptying — or silently skipping — a comparison below.
     */
    @Test
    fun theCanonicalTableNamesTheSeventeenTokensEveryCopyIsHeldTo() {
        assertEquals(
            setOf(
                "background", "surface", "surfacetint", "separator", "borderstrong",
                "textprimary", "textsecondary", "oncolor",
                "accent", "teal", "success", "amber", "wrong", "grown",
                "der", "die", "das",
            ),
            canon.keys,
            "$CANON: the token table changed shape — every copy below reads it by name",
        )
    }

    /** watchOS renders on black, so the watch app copies the dark column and only it. */
    @Test
    fun theWatchAppCarriesTheDarkColumn() {
        assertColumn(WATCH, swiftCopy(WATCH), DARK)
    }

    /** A home-screen widget follows the phone's scheme, so it copies both columns. */
    @Test
    fun thePhoneWidgetCarriesBothColumns() {
        assertPairs(WIDGET, swiftPairs(WIDGET))
    }

    /** A complication wears the dark half; it carries the light one so this can check it. */
    @Test
    fun theWatchComplicationCarriesBothColumns() {
        assertPairs(WATCH_WIDGET, swiftPairs(WATCH_WIDGET))
    }

    /** The website carries the whole table, both columns, under its own token names. */
    @Test
    fun theWebsiteCarriesBothColumns() {
        assertPairs(SITE, cssPairs(SITE))
    }

    /** Print is ink on paper: one column, and the sheet's own stock colors beside it. */
    @Test
    fun thePrintSheetsCarryTheLightColumn() {
        assertColumn(PRINT, cssColumn(cssDeclarations(PRINT), PRINT), LIGHT)
    }

    /**
     * The window background is XML because the theme needs it before Compose draws:
     * a value picked here rather than copied is a flash of the wrong app on every cold start.
     */
    @Test
    fun theAndroidWindowBackgroundCarriesBothColumns() {
        assertEquals(hex(Palette.background.light), androidWindowBackground(ANDROID_COLORS))
        assertEquals(hex(Palette.background.dark), androidWindowBackground(ANDROID_NIGHT_COLORS))
    }

    private fun assertPairs(where: String, tokens: Map<String, Pair<String, String>>) {
        assertColumn(where, tokens.mapValues { it.value.first }, LIGHT)
        assertColumn(where, tokens.mapValues { it.value.second }, DARK)
    }

    private fun assertColumn(
        where: String,
        tokens: Map<String, String>,
        column: (Swatch) -> Int,
    ) {
        assertTrue(
            tokens.isNotEmpty(),
            "$where: no palette tokens found — the copy this check reads has been reshaped",
        )
        for ((name, hex) in tokens) {
            val swatch = canon[name] ?: fail("$where: `$name` names no token in $CANON")
            assertEquals(
                hex(column(swatch)), hex,
                "$where: `$name` drifted from $CANON",
            )
        }
    }
}

private const val CANON = "kern/src/commonMain/kotlin/net/spross/kern/design/Palette.kt"
private const val WATCH = "Watch/Sources/WatchTheme.swift"
private const val WIDGET = "Widgets/Sources/WordWidgetView.swift"
private const val WATCH_WIDGET = "WatchWidgets/Sources/WatchWordWidgetView.swift"
private const val SITE = "web/site.css"
private const val PRINT = "marketing/print/print.css"
private const val ANDROID_COLORS = "android/src/main/res/values/colors.xml"
private const val ANDROID_NIGHT_COLORS = "android/src/main/res/values-night/colors.xml"

private val LIGHT: (Swatch) -> Int = { it.light }
private val DARK: (Swatch) -> Int = { it.dark }

/** `let der = Color(watchHex: 0x90CBFF)` — a single-column Swift copy. */
private val COPY_TOKEN =
    Regex("""\blet (\w+) = Color\(\w+: 0x([0-9A-Fa-f]{6})\)""")

/** `let der = Color(light: 0x134E85, dark: 0x90CBFF)` — a two-column one. */
private val COPY_PAIR =
    Regex(
        """\blet (\w+) = Color\(\w+: 0x([0-9A-Fa-f]{6}), \w+: 0x([0-9A-Fa-f]{6})\)"""
    )

private fun hex(value: Int): String = "%06X".format(value)

/**
 * Token name → its pair, read off [Palette]'s own properties rather than any text:
 * every `Swatch`-valued getter the object declares, so a token added there is checked
 * (or, until it is named above, caught by the shape test) instead of quietly skipped.
 */
private val canon: Map<String, Swatch> by lazy {
    Palette::class.java.declaredMethods
        .filter { it.returnType == Swatch::class.java && it.parameterCount == 0 }
        .associate { it.name.removePrefix("get").lowercase() to it.invoke(Palette) as Swatch }
}

private fun swiftCopy(path: String): Map<String, String> =
    COPY_TOKEN.findAll(repoText(path))
        .associate { it.groupValues[1].lowercase() to it.groupValues[2].uppercase() }

/** Token name → light hex to dark hex, for the copies that declare both. */
private fun swiftPairs(path: String): Map<String, Pair<String, String>> =
    COPY_PAIR.findAll(repoText(path)).associate {
        it.groupValues[1].lowercase() to (it.groupValues[2].uppercase() to it.groupValues[3].uppercase())
    }


/** `--border-strong: #868D7C;` — a CSS copy names the tokens in its own words. */
private val CSS_TOKEN = Regex("""--([a-z0-9-]+): #([0-9A-Fa-f]{6});""")

/** The marker the website's dark column opens on. */
private const val CSS_DARK = "prefers-color-scheme: dark"

/** CSS property name → the canonical token it restates. */
private val CSS_NAMES = mapOf(
    "bg" to "background", "surface" to "surface", "surface-tint" to "surfacetint",
    "tint" to "surfacetint", "separator" to "separator", "line" to "separator",
    "border-strong" to "borderstrong", "text" to "textprimary", "text-2" to "textsecondary",
    "on-color" to "oncolor", "accent" to "accent", "ocean" to "teal", "forest" to "success",
    "ochre" to "amber", "brick" to "wrong",
)

/** Foliage, paper stock and sand are the sheets' own — they name no [Palette] token. */
private val CSS_EXEMPT = setOf("leaf-a", "leaf-b", "stem", "paper", "sand", "sand-soft")

/** `<color name="spross_window_background">#FFF2F1EA</color>` — Android's `#AARRGGBB`. */
private val ANDROID_WINDOW =
    Regex("""<color name="spross_window_background">#[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})<""")

/** Declarations in file order, so the website's second block can be read as its dark column. */
private fun cssDeclarations(path: String): List<Pair<String, String>> =
    CSS_TOKEN.findAll(repoText(path))
        .map { it.groupValues[1] to it.groupValues[2].uppercase() }
        .toList()

/**
 * The canonical tokens one column declares, refusing a property that is neither canonical
 * nor named exempt: an unclassified token is a copy nobody decided the ownership of.
 */
private fun cssColumn(declared: List<Pair<String, String>>, where: String): Map<String, String> =
    declared.mapNotNull { (name, hex) ->
        CSS_NAMES[name]?.let { it to hex }
            ?: if (name in CSS_EXEMPT) null else fail("$where: `--$name` names no token in $CANON")
    }.toMap()

/** Token name → light hex to dark hex, for a stylesheet that declares both columns. */
private fun cssPairs(path: String): Map<String, Pair<String, String>> {
    val text = repoText(path)
    val split = text.indexOf(CSS_DARK)
    if (split < 0) fail("$path: no `$CSS_DARK` block — the copy this check reads has been reshaped")
    val declared = cssDeclarations(path)
    val lightCount = CSS_TOKEN.findAll(text.take(split)).count()
    val light = cssColumn(declared.take(lightCount), path)
    val dark = cssColumn(declared.drop(lightCount), path)
    return light.mapValues { (name, hex) ->
        hex to (dark[name] ?: fail("$path: `$name` has no dark column"))
    }
}

private fun androidWindowBackground(path: String): String =
    ANDROID_WINDOW.find(repoText(path))?.groupValues?.get(1)?.uppercase()
        ?: fail("$path: no `spross_window_background` — the copy this check reads has been reshaped")
