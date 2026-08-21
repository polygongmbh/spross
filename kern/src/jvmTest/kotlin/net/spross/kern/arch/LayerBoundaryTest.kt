package net.spross.kern.arch

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Kern decides, the platforms render.
 *
 * A kern DECISION is a value the engine computed from a rule: which rating an answer earns,
 * which tone a turn plays, which tier the advance is armed at. A platform may READ one —
 * `when (phase) { Review -> palette.success }` is rendering, which is its job. It may not
 * MINT one: writing `Rating.Hard` or `ToneKind.Correct` into an assignment, an argument or a
 * return is a platform deciding a rule kern owns, and a rule decided twice drifts. Both
 * produce screens once graded a typo themselves, and for thirteen days iOS called it Good
 * while Android called it Hard.
 *
 * The list of decision types is NOT written down here — a hand-kept list goes stale the
 * first time kern grows an enum, and a stale list is the same prose-rot this gate exists to
 * catch. It is DERIVED:
 *
 * > A type kern MINTS is a type kern decides. A type kern only ever READS is an INPUT, and a
 * > platform is exactly where an input should be born.
 *
 * That is what keeps `SelfGrading.Verdict` out of it: kern only ever reads a Verdict, because
 * the learner is the one who presses it. The shape scanned for is the enum, which is what the
 * repo's decisions are spelled as; a sealed hierarchy carries its own payload and is read
 * through `is`, which this cannot tell apart from minting.
 *
 * Waive a genuine platform-only decision with `// layer-ok: <reason>` on the line or the one
 * above it.
 *
 * The Swift and Kotlin trees are read as TEXT, the way `PaletteParityTest` reads the palette
 * copies; `kern/build.gradle.kts` names them as test inputs so an edit to one re-runs this.
 */
class LayerBoundaryTest {

    /**
     * The derivation itself, so a broken scan fails loudly rather than passing every check
     * below vacuously.
     */
    @Test
    fun kernMintsEnoughOfItsOwnTypesForTheDerivationToMeanAnything() {
        assertTrue(
            decisions.size >= 10,
            "only ${decisions.size} kern decision types derived from ${declared.size} declared " +
                "enums — the declaration scan is broken, and a broken scan makes the boundary " +
                "check below pass on nothing",
        )
    }

    @Test
    fun noPlatformMintsAKernDecision() {
        val bad = platformFiles().flatMap { (rel, file) ->
            mints(file.readText(), decisions).map { (line, hit, text) -> "$rel:$line: $hit  |  $text" }
        }
        assertTrue(
            bad.isEmpty(),
            "a platform decides a rule kern owns — call the kern function that already returns " +
                "it, or waive with `// layer-ok: <reason>`:\n" + bad.joinToString("\n"),
        )
    }
}

// ---------------------------------------------------------------- the scan

private val PLATFORM_ROOTS = listOf(
    "App/Sources", "android/src/main", "Watch/Sources",
    "Widgets/Sources", "WatchWidgets/Sources", "Shared/Sources",
)

private const val KERN = "kern/src/commonMain"

/** The shape a decision is spelled as. */
private val DECL = Regex("""^\s*(?:public\s+)?enum class\s+(\w+)""", RegexOption.MULTILINE)

private fun typeRef(names: Collection<String>) =
    Regex("""(?:\b\w+\.)?\b(${names.joinToString("|")})\.([A-Za-z]\w*)""")

/**
 * The positions that only READ a value someone else decided: a `when` branch label, an
 * equality test, an `is` bind, a Swift `case`, an elvis or `??` default (the value kern
 * would have given), an import, an extension's receiver, a membership test, a subscript.
 */
private fun reads(names: Collection<String>): List<Regex> {
    val t = "(?:" + names.joinToString("|") + ")"
    return listOf(
        """(^|[{;(])\s*(?:\w+\.)?$t\.\w+\s*(,\s*(?:\w+\.)?$t\.\w+\s*)*->""",
        """\bcase\s+\.?\w*$t?""",
        """[=!]=\s*(?:\w+\.)?$t\.""",
        """\bis\s+(?:\w+\.)?$t\.""",
        """\?:\s*(?:\w+\.)?$t\.""",
        """\?\?\s*\.?\w*$t?\.""",
        """^\s*import\b""",
        """\bin\s+(?:setOf|listOf|arrayOf)\(""",
        """\b(?:val|var|fun)\s+(?:\w+\.)?$t\.""",
        """\[(?:\w+\.)?$t\.""",
    ).map { Regex(it) }
}

/** Members the compiler adds; naming one is never a decision. */
private val SYNTHESIZED = setOf(
    "allCases", "entries", "values", "valueOf", "shared",
    "Companion", "companion", "self", "init", "rawValue",
)

private fun mints(source: String, names: Collection<String>): List<Triple<Int, String, String>> {
    if (names.isEmpty()) return emptyList()
    val ref = typeRef(names)
    val read = reads(names)
    val lines = source.split("\n")
    val out = mutableListOf<Triple<Int, String, String>>()
    for ((i, raw) in lines.withIndex()) {
        val code = raw.substringBefore("//")
        val hit = ref.find(code) ?: continue
        if (hit.groupValues[2] in SYNTHESIZED) continue
        if (read.any { it.containsMatchIn(code) }) continue
        if ("layer-ok:" in raw || (i > 0 && "layer-ok:" in lines[i - 1])) continue
        out += Triple(i + 1, hit.value, raw.trim().take(84))
    }
    return out
}

// ------------------------------------------------------- the derived list

private fun kotlinFiles(dir: String): List<File> =
    File(repoRoot, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

/** Every type kern declares that could carry a decision. */
private val declared: Set<String> by lazy {
    kotlinFiles(KERN)
        .flatMap { f -> DECL.findAll(f.readText()).map { it.groupValues[1] } }
        .toSet()
}

/** …narrowed to the ones kern MINTS. Minting is deciding; reading is not. */
private val decisions: Set<String> by lazy {
    val minted = mutableSetOf<String>()
    val ref = typeRef(declared)
    val read = reads(declared)
    for (file in kotlinFiles(KERN)) {
        for (raw in file.readText().split("\n")) {
            val code = raw.substringBefore("//")
            val hit = ref.find(code) ?: continue
            if (hit.groupValues[2] in SYNTHESIZED) continue
            if (read.any { it.containsMatchIn(code) }) continue
            minted += hit.groupValues[1]
        }
    }
    minted
}

private fun platformFiles(): List<Pair<String, File>> = PLATFORM_ROOTS.flatMap { root ->
    File(repoRoot, root).walkTopDown()
        .filter { it.isFile && (it.extension == "swift" || it.extension == "kt") }
        .filterNot { Regex("""(^|/)(test|Tests|androidTest)(/|$)""").containsMatchIn(it.parent) }
        .map { it.relativeTo(repoRoot).path to it }
        .toList()
}.sortedBy { it.first }

/** The repo root, found by walking up the way the palette and catalog lints find theirs. */
private val repoRoot: File by lazy {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        if (File(dir, KERN).isDirectory && File(dir, "App/Sources").isDirectory) return@lazy dir
        dir = dir.parentFile
    }
    error("$KERN not found above ${System.getProperty("user.dir")}")
}
