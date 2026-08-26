package net.spross.kern.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Five surfaces draw Spross and only one of them can be the palette: [Palette] is it.
 *
 * The iOS app and Android link kern, so they read the hex values from [Palette] itself and
 * cannot disagree with it. The watch app and the two widget extensions do NOT link kern —
 * a color table is no reason to pull a Kotlin/Native framework into a tiny standalone
 * target — so each keeps a hand-written copy, and a copy is a thing that drifts. This test
 * reads those three files as text and holds every value they carry to [Palette].
 *
 * A copy keeps only the tokens it uses — the phone widget needs a handful of hues and
 * nothing else — so the check runs the other way round: every token a file DECLARES must
 * name a canonical token and carry its hex.
 *
 * The watch APP is the one copy that is a single column: it renders on black and says so.
 * Both widget copies declare PAIRS and are held to both columns, even the complication
 * that only ever wears the dark half — a half-checked table is where the other half drifts.
 *
 * Gradle does not track these Swift sources as test inputs the way it tracks the classpath
 * (`kern/build.gradle.kts` names the trees so it can): after a palette-only edit that
 * somehow escapes them, run with `--rerun-tasks`.
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

private val LIGHT: (Swatch) -> Int = { it.light }
private val DARK: (Swatch) -> Int = { it.dark }

/** `static let wlDer = Color(watchHex: 0x90CBFF)` — a single-column Swift copy. */
private val COPY_TOKEN =
    Regex("""static let [a-z]{2}(\w+) = Color\(\w+: 0x([0-9A-Fa-f]{6})\)""")

/** `static let wgDer = Color(wgLight: 0x134E85, wgDark: 0x90CBFF)` — a two-column one. */
private val COPY_PAIR =
    Regex(
        """static let [a-z]{2}(\w+) = Color\(\w+: 0x([0-9A-Fa-f]{6}), \w+: 0x([0-9A-Fa-f]{6})\)"""
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
    COPY_TOKEN.findAll(read(path))
        .associate { it.groupValues[1].lowercase() to it.groupValues[2].uppercase() }

/** Token name → light hex to dark hex, for the copies that declare both. */
private fun swiftPairs(path: String): Map<String, Pair<String, String>> =
    COPY_PAIR.findAll(read(path)).associate {
        it.groupValues[1].lowercase() to (it.groupValues[2].uppercase() to it.groupValues[3].uppercase())
    }

/** The repo root, found by walking up the way the real-catalog tests find `catalog/`. */
private val repoRoot: File by lazy {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        if (File(dir, CANON).isFile) return@lazy dir
        dir = dir.parentFile
    }
    error("$CANON not found above ${System.getProperty("user.dir")}")
}

private fun read(path: String): String =
    File(repoRoot, path).takeIf { it.isFile }?.readText()
        ?: fail("$path: missing — a palette copy cannot be checked against nothing")
