package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spot checks of the runtime join against the real catalog (coverage as of catalog v2.1). */
class RealCatalogJoinTest {
    private val catalog get() = RealCatalog.catalog

    private fun List<Card>.byId(id: String): Card =
        firstOrNull { it.id == id } ?: throw AssertionError("card $id not joined")

    @Test
    fun joinCountsMatchCoverageMatrix() {
        // 357 concepts total; sparse per-language coverage decides each target's count.
        assertEquals(347, catalog.join("de", "sw").size)
        assertEquals(351, catalog.join("de", "uk").size)
        assertEquals(352, catalog.join("de", "en").size)
    }

    @Test
    fun availableTargetsFromGermanCarryConceptCounts() {
        val targets = catalog.availableTargets("de")
        assertEquals(listOf("en", "sw", "uk"), targets.map { it.code })
        assertEquals(listOf(352, 347, 351), targets.map { it.conceptCount })
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
    fun pinnedFridgeCardCarriesGrammar() {
        val fridge = catalog.join("de", "sw").byId("kitchen/fridge")
        assertEquals(CardKind.Noun, fridge.kind)
        assertEquals("🧊", fridge.emoji)
        assertEquals("Kühlschrank", fridge.source.text)
        assertEquals(mapOf("gender" to "der", "plural" to "Kühlschränke"), fridge.source.grammar)
        assertEquals("friji", fridge.target.text)
        assertTrue(fridge.target.grammar.isEmpty())
    }

    @Test
    fun teacherFeminineJoinsFromSwSourceViaBaseFallback() {
        val teacherF = catalog.join("sw", "uk").byId("school/teacher-f")
        assertTrue(teacherF.promptFeminineMarker)
        assertEquals("mwalimu", teacherF.source.text)
        assertEquals("вчителька", teacherF.target.text)
        assertEquals("school/teacher", teacherF.feminineOf)
    }

    @Test
    fun teacherFeminineSkippedForSwTargetButDistinctFromDeSource() {
        assertTrue(catalog.join("de", "sw").none { it.id == "school/teacher-f" })
        val fromDe = catalog.join("de", "uk").byId("school/teacher-f")
        assertFalse(fromDe.promptFeminineMarker)
        assertEquals("Lehrerin", fromDe.source.text)
    }

    @Test
    fun basicsGreetingsArePhrasesWithoutComponents() {
        val cards = catalog.join("de", "sw")
        for (id in listOf("basics/hello", "basics/good-day")) {
            val card = cards.byId(id)
            assertEquals(CardKind.Phrase, card.kind)
            assertEquals(emptyList(), card.components)
        }
    }

    @Test
    fun unifiedPotPhraseJoinsEveryTargetUnderOneSlug() {
        // Former variantOf twin: the uk realization now lives on the base slug.
        val id = "kitchen/the-pot-is-still-on-the-stove"
        assertEquals("The pot is still on the stove.", catalog.join("de", "en").byId(id).target.text)
        assertEquals("Каструля ще стоїть на плиті.", catalog.join("de", "uk").byId(id).target.text)
        for (target in listOf("en", "uk")) {
            assertFalse(catalog.join("de", target).any { it.id == "kitchen/the-big-pot-is-on-the-stove" })
        }
    }

    @Test
    fun riceNoteSurfacesOnlyForGermanSources() {
        val id = "kitchen/are-you-cooking-rice-today"
        val fromDe = catalog.join("de", "sw").byId(id)
        assertEquals("Reis = mpunga (geerntet) → mchele (roh) → wali (gekocht)!", fromDe.target.note)
        assertNull(catalog.join("en", "sw").byId(id).target.note)
    }

    @Test
    fun ukSynonymsRotateVariantsStaySilent() {
        val cards = catalog.join("de", "uk")
        // Synonyms join the recognition-prompt rotation; variants are grading-only.
        val boss = cards.byId("work/boss")
        assertEquals("шеф", boss.target.text)
        assertEquals(listOf("керівник"), boss.target.synonyms)
        val contract = cards.byId("work/contract")
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
