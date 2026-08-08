package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.catalog.LanguageChoices.Selection
import net.spross.kern.catalog.LanguageChoices.TargetChoice
import net.spross.kern.model.LanguageInfo

/**
 * The picker rules: how a language is named, what the target side offers, and how a tap
 * moves the pair. Catalogs here are generated because the offer turns on the 50-concept
 * coverage threshold — the shared [Fixture] is deliberately below it.
 */
class LanguageChoicesTest {

    /** A row names the language in its own words first — a flag alone is not identifiable. */
    @Test
    fun aPickerRowCarriesTheEndonymAndTheExonym() {
        val uk = LanguageInfo(code = "uk", name = "Українська", englishName = "Ukrainian", flag = "🇺🇦")
        assertEquals("🇺🇦 Українська · Ukrainian", LanguageChoices.pickerRow("uk", uk))
        assertEquals("🇺🇦 Ukrainian", LanguageChoices.pickerLabel("uk", uk))
    }

    @Test
    fun oneNameIsEnoughWhereBothAgree() {
        val en = LanguageInfo(code = "en", name = "English", englishName = "English", flag = "🇬🇧")
        assertEquals("🇬🇧 English", LanguageChoices.pickerRow("en", en))
    }

    @Test
    fun anUnknownLanguageFallsBackToItsUppercasedCode() {
        assertEquals("XX", LanguageChoices.pickerRow("xx", null))
        assertEquals("XX", LanguageChoices.pickerLabel("xx", null))
    }

    /** The swap row's count is the pair the tap would join, which is not the one on screen. */
    @Test
    fun targetChoicesIncludeTheCurrentSourceWithTheSwappedPairsCount() {
        assertEquals(
            listOf(
                TargetChoice("de", 55), // en→de: the shared concepts plus de's feminines
                TargetChoice("en", 50), // de→en: en realizes no feminine, so five fewer
                TargetChoice("sw", 50),
                TargetChoice("uk", 50),
            ),
            LanguageChoices.targetChoices(catalog, Selection("en", "de")),
        )
    }

    @Test
    fun targetChoicesOmitTheSourceWhileNoTargetIsChosen() {
        val choices = LanguageChoices.targetChoices(catalog, Selection("en", null))
        assertEquals(listOf("de", "sw", "uk"), choices.map { it.code })
    }

    /** uk teaches nothing back, so there is no swapped pair to offer a row for. */
    @Test
    fun targetChoicesOmitTheSourceWhenTheSwappedPairIsNotJoinable() {
        val choices = LanguageChoices.targetChoices(catalog, Selection("en", "uk"))
        assertEquals(listOf("de", "sw", "uk"), choices.map { it.code })
    }

    @Test
    fun pickingTheSourceOnTheTargetSideSwaps() {
        assertEquals(
            Selection("de", "en"),
            LanguageChoices.pickTarget(Selection("en", "de"), "en"),
        )
    }

    @Test
    fun pickingAnotherTargetJustRetargets() {
        assertEquals(
            Selection("en", "sw"),
            LanguageChoices.pickTarget(Selection("en", "de"), "sw"),
        )
    }

    @Test
    fun pickingTheTargetOnTheSourceSideSwaps() {
        assertEquals(
            Selection("de", "en"),
            LanguageChoices.pickSource(catalog, Selection("en", "de"), "de"),
        )
    }

    @Test
    fun sourceChangeKeepsAStillLearnableTarget() {
        assertEquals(
            Selection("de", "sw"),
            LanguageChoices.pickSource(catalog, Selection("en", "sw"), "de"),
        )
    }

    @Test
    fun sourceChangeFallsBackToTheFirstTargetWhenThePickTurnsInvalid() {
        // uk is learnable from en but not from sw → fall back to sw's first target.
        assertEquals(
            Selection("sw", "en"),
            LanguageChoices.pickSource(catalog, Selection("en", "uk"), "sw"),
        )
    }

    /** Nothing is chosen yet, so there is nothing to exchange — the tap must not blank the source. */
    @Test
    fun swappingWhileNoTargetIsChosenChangesNothing() {
        assertEquals(
            Selection("en", null),
            LanguageChoices.pickTarget(Selection("en", null), "en"),
        )
    }

    /** A source tap never leaves the pair half-chosen: the first learnable target fills in. */
    @Test
    fun aSourceTapLeavesALearnableTarget() {
        assertEquals(
            Selection("en", "de"),
            LanguageChoices.pickSource(catalog, Selection("en", null), "en"),
        )
    }

    @Test
    fun chromeReadsTheKnownLanguageWhereItExistsAndEnglishOtherwise() {
        assertEquals("de", LanguageChoices.chromeLanguage("de"))
        assertEquals("en", LanguageChoices.chromeLanguage("en"))
        assertEquals("en", LanguageChoices.chromeLanguage("sw"))
        assertEquals("en", LanguageChoices.chromeLanguage("uk"))
    }

    /** An immersion subtitle has no fallback: absent means no subtitle, never an English one. */
    @Test
    fun aLanguageWithoutChromeCarriesNoImmersionSubtitle() {
        assertTrue(LanguageChoices.hasChrome("de"))
        assertFalse(LanguageChoices.hasChrome("uk"))
        assertFalse(LanguageChoices.hasChrome("es"))
    }

    private companion object {
        const val SHARED = 50
        const val FEMININES = 5

        /**
         * A catalog whose pairs are deliberately lopsided:
         * en, de and sw share [SHARED] concepts, de adds [FEMININES] feminines of them
         * (so en→de counts five more than de→en — the base carries the prompt),
         * and uk realizes only feminines of concepts nobody but en has,
         * so en teaches uk while uk teaches nothing at all.
         * Declaration order is en, de, sw, uk — [Catalog.availableTargets] answers in it.
         */
        val catalog: Catalog = run {
            val concepts =
                (0 until SHARED).map { """{ "slug": "a$it", "kind": "noun" }""" } +
                    (0 until FEMININES).map {
                        """{ "slug": "af$it", "kind": "noun", "feminineOf": "a$it" }"""
                    } +
                    (0 until SHARED).map { """{ "slug": "c$it", "kind": "noun" }""" } +
                    (0 until SHARED).map {
                        """{ "slug": "cf$it", "kind": "noun", "feminineOf": "c$it" }"""
                    }
            Catalog.load(
                MapCatalogSource(
                    mapOf(
                        "areas.json" to
                            """[{ "group": "g", "titles": {}, "areas": [{ "area": "core", "emoji": "📦" }] }]""",
                        "languages.json" to """
                            {
                             "en": { "name": "English", "englishName": "English", "flag": "🇬🇧" },
                             "de": { "name": "Deutsch", "englishName": "German", "flag": "🇩🇪" },
                             "sw": { "name": "Kiswahili", "englishName": "Swahili", "flag": "🇹🇿" },
                             "uk": { "name": "Українська", "englishName": "Ukrainian", "flag": "🇺🇦" }
                            }
                        """.trimIndent(),
                        "core/concepts.json" to concepts.joinToString(",", "[", "]"),
                        "core/en.json" to words(
                            "en",
                            (0 until SHARED).map { "a$it" } + (0 until SHARED).map { "c$it" },
                        ),
                        "core/de.json" to words(
                            "de",
                            (0 until SHARED).map { "a$it" } + (0 until FEMININES).map { "af$it" },
                        ),
                        "core/sw.json" to words("sw", (0 until SHARED).map { "a$it" }),
                        "core/uk.json" to words("uk", (0 until SHARED).map { "cf$it" }),
                    ),
                ),
            )
        }

        fun words(code: String, slugs: List<String>): String = slugs.joinToString(
            separator = ",",
            prefix = """{ "title": "Core", "words": {""",
            postfix = "} }",
        ) { """"$it": { "text": "$code-$it" }""" }
    }
}
