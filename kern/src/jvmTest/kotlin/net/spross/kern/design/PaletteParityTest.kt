package net.spross.kern.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Five surfaces draw Spross and only one of them can be the palette.
 *
 * The design tokens stay native on each platform — the spacing, the type ramp and the
 * hex pairs are the one part of the app kern deliberately does NOT own
 * (`docs/portability.md` § Stays native). What kern can own is the AGREEMENT: the watch,
 * the two widget extensions and the Android app each keep a hand-written copy of the
 * table because none of those targets links the app's design tokens, and a copy is a
 * thing that drifts. This test reads all five files as text and holds every copied value
 * to `App/Sources/Design/Theme.swift`.
 *
 * A copy keeps only the tokens it uses — the phone widget needs three hues and nothing
 * else — so the check runs the other way round: every token a file DECLARES must name a
 * canonical token and carry its hex. The Android cut is the exception, being a full
 * re-cut of both columns rather than a handful of borrowed hues.
 *
 * Gradle does not track these Swift and Kotlin sources as test inputs (the same caveat
 * the real-catalog lints carry): after a palette-only edit, run with `--rerun-tasks`.
 */
class PaletteParityTest {

    /**
     * The canonical table itself, so a rename breaks here rather than silently emptying
     * every comparison below.
     */
    @Test
    fun theCanonicalTableNamesTheSixteenTokensEveryCopyIsHeldTo() {
        assertEquals(
            setOf(
                "background", "surface", "surfacetint", "separator", "borderstrong",
                "textprimary", "textsecondary", "oncolor",
                "accent", "teal", "success", "amber", "wrong",
                "der", "die", "das",
            ),
            canon.keys,
            "$CANON: the token table changed shape — every copy below reads it by name",
        )
    }

    /** The Android cut is a full re-cut: both columns, all sixteen, nothing invented. */
    @Test
    fun theAndroidTableCarriesBothColumnsWhole() {
        for ((table, column) in listOf("DlLight" to LIGHT, "DlDark" to DARK)) {
            val tokens = composeTable(table)
            assertEquals(
                canon.keys, tokens.keys,
                "$ANDROID: `$table` is not the canonical table token for token",
            )
            assertColumn("$ANDROID `$table`", tokens, column)
        }
    }

    /** watchOS renders on black, so the watch app copies the dark column. */
    @Test
    fun theWatchAppCarriesTheDarkColumn() {
        assertColumn(WATCH, swiftCopy(WATCH), DARK)
    }

    /** A home-screen widget is read on paper, so it copies the light column. */
    @Test
    fun thePhoneWidgetCarriesTheLightColumn() {
        assertColumn(WIDGET, swiftCopy(WIDGET), LIGHT)
    }

    /** A complication sits on the same black the watch app does. */
    @Test
    fun theWatchComplicationCarriesTheDarkColumn() {
        assertColumn(WATCH_WIDGET, swiftCopy(WATCH_WIDGET), DARK)
    }

    private fun assertColumn(
        where: String,
        tokens: Map<String, String>,
        column: (Pair<String, String>) -> String,
    ) {
        assertTrue(
            tokens.isNotEmpty(),
            "$where: no palette tokens found — the copy this check reads has been reshaped",
        )
        for ((name, hex) in tokens) {
            val pair = canon[name] ?: fail("$where: `$name` names no token in $CANON")
            assertEquals(
                column(pair), hex,
                "$where: `$name` drifted from $CANON",
            )
        }
    }
}

private const val CANON = "App/Sources/Design/Theme.swift"
private const val ANDROID = "android/src/main/kotlin/net/spross/app/ui/Theme.kt"
private const val WATCH = "Watch/Sources/WatchTheme.swift"
private const val WIDGET = "Widgets/Sources/WordWidgetView.swift"
private const val WATCH_WIDGET = "WatchWidgets/Sources/WatchWordWidgetView.swift"

private val LIGHT: (Pair<String, String>) -> String = { it.first }
private val DARK: (Pair<String, String>) -> String = { it.second }

/** `static let dlAccent = Color(light: 0xA23B0B, dark: 0xFF9A6B)` — the truth. */
private val CANON_TOKEN =
    Regex("""static let dl(\w+) = Color\(light: 0x([0-9A-Fa-f]{6}), dark: 0x([0-9A-Fa-f]{6})\)""")

/** `static let wlDer = Color(watchHex: 0x90CBFF)` — the shape every Swift copy declares. */
private val COPY_TOKEN =
    Regex("""static let [a-z]{2}(\w+) = Color\(\w+: 0x([0-9A-Fa-f]{6})\)""")

/** `der = Color(0xFF134E85)` — the shape the Compose table declares. */
private val COMPOSE_TOKEN = Regex("""(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)""")

/** Token name → light hex to dark hex. */
private val canon: Map<String, Pair<String, String>> by lazy {
    CANON_TOKEN.findAll(read(CANON)).associate {
        it.groupValues[1].lowercase() to (it.groupValues[2].uppercase() to it.groupValues[3].uppercase())
    }
}

private fun swiftCopy(path: String): Map<String, String> =
    COPY_TOKEN.findAll(read(path))
        .associate { it.groupValues[1].lowercase() to it.groupValues[2].uppercase() }

private fun composeTable(name: String): Map<String, String> {
    val source = read(ANDROID)
    val start = source.indexOf("val $name = DlColors(")
    if (start < 0) fail("$ANDROID: no `val $name = DlColors(` table to read")
    val end = source.indexOf("\n)", start)
    if (end < 0) fail("$ANDROID: `$name` is never closed")
    return COMPOSE_TOKEN.findAll(source.substring(start, end))
        .associate { it.groupValues[1].lowercase() to it.groupValues[2].uppercase() }
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
