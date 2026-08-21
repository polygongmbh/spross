package net.spross.kern.arch

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The chrome table's SHAPE, gated rather than written down.
 *
 * It earns a test because the failure it prevents names nothing: a table that outgrows the
 * JVM's method descriptor dies at class LOAD with `ClassFormatError: Too many arguments in
 * method signature`, pointing at no field, no line and no remedy, and taking down every test
 * that touches chrome rather than the one that broke it.
 *
 * Read as text out of the platform tree, the way `LayerBoundaryTest` and the palette gates do:
 * `kern/build.gradle.kts` already names `android/src/main` a `jvmTest` input, so an edit to
 * `Chrome.kt` re-runs this with no wiring of its own, and it runs where no Android SDK exists.
 */
class ChromeTableShapeTest {

    private val chrome = File("../android/src/main/kotlin/net/spross/app/Chrome.kt")

    /**
     * A chrome table is an INTERFACE that two generated objects implement, never a class with
     * a parameter per string.
     *
     * A constructor is what imposed the ceiling — a descriptor holds 255 slots including
     * `this`, and a `data class` spends eight more on `copy$default`'s bitmasks — and it is
     * also what made both tables one positional list that had to agree. An interface removes
     * the ceiling rather than raising it, and turns a missing string into
     * "does not implement abstract member", named, at the table that is short of it.
     */
    @Test
    fun theChromeTableIsAnInterfaceRatherThanAConstructor() {
        val text = chrome.readText()
        assertTrue(
            "interface Chrome {" in text,
            "Chrome.kt must declare `interface Chrome {` — see this test's KDoc for why a " +
                "constructor cannot hold this many strings.",
        )
        assertTrue(
            "class Chrome(" !in text,
            "Chrome is a class again: at this many strings its constructor overflows the JVM's " +
                "255-slot method descriptor and every chrome test dies on a ClassFormatError " +
                "that names no field. Keep it an interface.",
        )
    }

    /**
     * No class in the platform trees takes a constructor this wide — the general net under the
     * rule above, since the next god-object will not be called Chrome.
     *
     * The bar sits well under the JVM's own so it fails with room to fix rather than at the
     * cliff, and because a `data class` quietly spends another eight slots on bitmasks.
     */
    @Test
    fun noPlatformClassTakesAConstructorTooWideForTheJvm() {
        val offenders = platformSources()
            .mapNotNull { file ->
                val widest = file.readText()
                    .split(Regex("""^(?:@\w+\s*\n)*(?:internal |private )?(?:data )?class \w+\(""", RegexOption.MULTILINE))
                    .drop(1)
                    .maxOfOrNull { body -> Regex("""^\s{4}(?:override )?val \w+:""", RegexOption.MULTILINE).findAll(body.substringBefore("\n)")).count() }
                    ?: 0
                if (widest > CONSTRUCTOR_BAR) "${file.name}: $widest" else null
            }
        assertTrue(
            offenders.isEmpty(),
            "a constructor is carrying more than $CONSTRUCTOR_BAR parameters: ${offenders.joinToString()}. " +
                "The JVM caps a method descriptor at 255 slots including `this`, and a data class's " +
                "copy\$default adds one int per 32 parameters plus a marker on top — so this dies at " +
                "class load with a ClassFormatError that names nothing. Split it, or make it an interface.",
        )
    }

    /**
     * Every chrome field is a string, a list of them, or a map of them — the whole of what the
     * generator can emit (`scripts/chrome.py`).
     *
     * Also the guard on the stability promise: `@Stable` is unchecked, so a mutable field type
     * would let Compose skip a table that had changed and leave a stale word on screen. The
     * generator cannot write one; this refuses one written by hand.
     */
    @Test
    fun everyChromeFieldIsAStringAListOfThemOrAMapOfThem() {
        val allowed = setOf("String", "List<String>", "Map<String, String>")
        val wrong = Regex("""^\s{4}val (\w+): (.+?)$""", RegexOption.MULTILINE)
            .findAll(chrome.readText())
            .map { it.groupValues[1] to it.groupValues[2].substringBefore("//").trim().removeSuffix(",").trim() }
            .filterNot { (_, type) -> type in allowed }
            .toList()
        assertTrue(
            wrong.isEmpty(),
            "chrome fields may only be String, List<String> or Map<String, String> — the whole " +
                "of what scripts/chrome.py emits, and what keeps the table's stability promise " +
                "honest: ${wrong.joinToString { "${it.first}: ${it.second}" }}",
        )
    }

    private fun platformSources(): List<File> =
        listOf(File("../android/src/main/kotlin"))
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }

    private companion object {
        /**
         * Well under the JVM's 255 so a break arrives with room to fix it, and under it by more
         * than the eight slots a data class spends without saying so.
         */
        const val CONSTRUCTOR_BAR = 200
    }
}
