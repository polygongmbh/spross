package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Join RULES spot-checked against the real catalog. Expectations are derived from the
 * catalog or exercised through a representative entry — never pinned field values, which
 * would measure content rather than code. Exact values belong in [CatalogFixtureTest],
 * which owns its fixture; content validity in [CatalogLintTest]. README §7.
 */
class RealCatalogJoinTest {
    private val catalog get() = RealCatalog.catalog

    private fun List<Card>.byId(id: String): Card =
        firstOrNull { it.id == id } ?: throw AssertionError("card $id not joined")

    /**
     * The join RULE, asserted relationally: a concept emits a card iff the target
     * realizes it and the source realizes either it or — for a feminine — its base,
     * and whichever realization is used can name the target language where it says so.
     * Deriving the expectation from the catalog keeps this green across ordinary
     * content edits while still catching join regressions (dropped feminine
     * fallback, duplicates, wrong seed order). Pinned totals would only ever
     * measure how recently someone bumped them.
     */
    @Test
    fun joinEmitsExactlyTheConceptsBothLanguagesRealize() {
        for (target in listOf("en", "eo", "es", "fr", "it", "sw", "uk")) {
            fun RawRealization?.resolvable(reader: String): Boolean {
                if (this == null) return false
                val marked = (listOf(text) + synonyms + variants).any { LanguageNames.hasLanguageMarker(it) }
                return !marked || catalog.languageName(reader, target) != null
            }
            val expected = catalog.areas.flatMap { area ->
                val sourceWords = area.realizations["de"].orEmpty()
                val targetWords = area.realizations[target].orEmpty()
                area.concepts.filter { concept ->
                    val prompt = sourceWords[concept.slug]
                        ?: concept.feminineOf?.let { base -> sourceWords[base] }
                    targetWords[concept.slug].resolvable(target) && prompt.resolvable("de")
                }.map { it.id }
            }
            assertEquals(expected, catalog.join("de", target).map { it.id }, "de→$target join")
        }
    }

    /** Catastrophe guard: a loose floor, deliberately NOT a pinned per-pair count. */
    @Test
    fun everyGermanPairJoinsSubstantialCoverage() {
        for (target in listOf("en", "eo", "es", "fr", "it", "sw", "uk")) {
            val size = catalog.join("de", target).size
            assertTrue(size > 300, "de→$target joined only $size cards")
        }
    }

    @Test
    fun availableTargetsFromGermanCarryConceptCounts() {
        val targets = catalog.availableTargets("de")
        assertEquals(listOf("en", "eo", "es", "fr", "it", "sw", "uk"), targets.map { it.code })
        // Agreement with the join, not magic numbers.
        assertEquals(targets.map { catalog.join("de", it.code).size }, targets.map { it.conceptCount })
        assertEquals("Kiswahili", targets.first { it.code == "sw" }.name)
    }

    @Test
    fun everyLanguagePairIsMutuallyAvailable() {
        for (source in catalog.languages.keys) {
            val codes = catalog.availableTargets(source).map { it.code }
            assertEquals((catalog.languages.keys - source).sorted(), codes.sorted())
        }
    }

    @Test
    fun teacherFeminineJoinsFromSwSourceViaBaseFallback() {
        val teacherF = catalog.join("sw", "uk").byId("teacher-f")
        assertTrue(teacherF.promptFeminineMarker)
        assertEquals("mwalimu", teacherF.source.text)
        assertEquals("вчителька", teacherF.target.text)
        assertEquals("teacher", teacherF.feminineOf)
    }

    @Test
    fun teacherFeminineSkippedForSwTargetButDistinctFromDeSource() {
        assertTrue(catalog.join("de", "sw").none { it.id == "teacher-f" })
        val fromDe = catalog.join("de", "uk").byId("teacher-f")
        assertFalse(fromDe.promptFeminineMarker)
        assertEquals("Lehrerin", fromDe.source.text)
    }

    @Test
    fun basicsGreetingsArePhrasesWithoutComponents() {
        val cards = catalog.join("de", "sw")
        for (id in listOf("hello", "good-day")) {
            val card = cards.byId(id)
            assertEquals(CardKind.Phrase, card.kind)
            assertEquals(emptyList(), card.components)
        }
    }

    @Test
    fun unifiedPotPhraseJoinsEveryTargetUnderOneSlug() {
        // Former variantOf twin: the uk realization now lives on the base slug.
        val id = "the-pot-is-still-on-the-stove"
        assertEquals("The pot is still on the stove.", catalog.join("de", "en").byId(id).target.text)
        assertEquals("Каструля ще стоїть на плиті.", catalog.join("de", "uk").byId(id).target.text)
        for (target in listOf("en", "eo", "es", "fr", "it", "uk")) {
            assertFalse(catalog.join("de", target).any { it.id == "the-big-pot-is-on-the-stove" })
        }
    }

    /**
     * A note reaches the reader who can read it and nobody else: Swahili annotates its rice
     * card in German alone, so a German reader gets it and an English one gets nothing —
     * German is neither their language nor the one they are studying.
     */
    @Test
    fun riceNoteSurfacesOnlyForGermanSources() {
        val id = "are-you-cooking-rice-today"
        val fromDe = catalog.join("de", "sw").byId(id)
        assertEquals("Reis = mpunga (geerntet) → mchele (roh) → wali (gekocht)!", fromDe.target.note)
        assertNull(catalog.join("en", "sw").byId(id).target.note)
    }

    /**
     * The fallback's payoff on real content: German annotates its own word, and a reader
     * whose language carries no note of its own is served that one rather than nothing.
     */
    @Test
    fun aGermanSelfNoteReachesEveryReader() {
        val note = "schwer heißt auch schwierig: eine schwere Aufgabe."
        assertEquals(note, catalog.join("en", "de").byId("heavy").target.note)
        assertEquals(note, catalog.join("uk", "de").byId("heavy").target.note)
    }

    @Test
    fun ukSynonymsRotateVariantsStaySilent() {
        val cards = catalog.join("de", "uk")
        // Synonyms join the recognition-prompt rotation; variants are grading-only.
        val boss = cards.byId("boss")
        assertEquals("шеф", boss.target.text)
        assertEquals(listOf("керівник"), boss.target.synonyms)
        val contract = cards.byId("contract")
        assertEquals("договір", contract.target.text)
        assertTrue(contract.target.synonyms.isEmpty())
        assertEquals(listOf("контракт"), contract.target.variants)
    }

    @Test
    fun fingerprintStableAcrossLoads() {
        val again = Catalog.load(FileCatalogSource(RealCatalog.root))
        assertEquals(catalog.fingerprint, again.fingerprint)
        assertEquals(16, catalog.fingerprint.length)
    }
}
