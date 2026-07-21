package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.ExerciseUnits
import net.spross.kern.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogFixtureTest {
    private val catalog = Fixture.catalog()

    private fun List<Card>.byId(id: String): Card =
        firstOrNull { it.id == id } ?: throw AssertionError("card $id not joined: ${map { it.id }}")

    // -- feminine base-fallback ♀ ------------------------------------------------------

    @Test
    fun feminineFallsBackToBaseSourceRealizationWithMarker() {
        val card = catalog.join("sw", "uk").byId("alpha/waiter-f")
        assertTrue(card.promptFeminineMarker)
        assertEquals("mhudumu", card.source.text)
        assertEquals("офіціантка", card.target.text)
        assertEquals("alpha/waiter", card.feminineOf)
    }

    @Test
    fun feminineUsesOwnSourceRealizationWithoutMarker() {
        val card = catalog.join("de", "uk").byId("alpha/waiter-f")
        assertFalse(card.promptFeminineMarker)
        assertEquals("Kellnerin", card.source.text)
    }

    @Test
    fun feminineSkippedWhenBaseSourceRealizationAlsoMissing() {
        // beta has no sw file: neither royal-f nor base royal realize in the source.
        assertTrue(catalog.join("sw", "uk").none { it.id == "beta/royal-f" })
    }

    @Test
    fun feminineSkippedWhenTargetDoesNotRealizeIt() {
        assertTrue(catalog.join("de", "sw").none { it.id == "alpha/waiter-f" })
    }

    // -- variantOf ---------------------------------------------------------------------

    @Test
    fun variantTwinSkippedWhenBaseJoins() {
        val ids = catalog.join("de", "en").map { it.id }
        assertTrue("alpha/the-mouse-runs" in ids)
        assertFalse("alpha/the-mouse-sprints" in ids)
    }

    @Test
    fun variantTwinJoinsWhenBaseDoesNotJoin() {
        val ids = catalog.join("de", "uk").map { it.id }
        assertTrue("alpha/the-mouse-sprints" in ids)
        assertFalse("alpha/the-mouse-runs" in ids)
    }

    // -- coverage skips ----------------------------------------------------------------

    @Test
    fun sparseTargetCoverageSkipsConcepts() {
        val ids = catalog.join("de", "uk").map { it.id }
        assertEquals(
            listOf(
                "alpha/waiter", "alpha/waiter-f", "alpha/mouse",
                "alpha/the-mouse-sprints", "beta/royal-f", "gamma/door",
            ),
            ids,
        )
    }

    @Test
    fun nonFeminineConceptWithoutSourceRealizationSkipped() {
        // sw realizes cook but source uk does not — no prompt, no card.
        assertEquals(
            listOf("alpha/waiter", "alpha/mouse", "gamma/door"),
            catalog.join("uk", "sw").map { it.id },
        )
    }

    // -- parsing details ---------------------------------------------------------------

    @Test
    fun enToPrefixSurvivesParsing() {
        assertEquals(listOf("to "), catalog.languages.getValue("en").optionalVerbPrefixes)
        assertEquals("to cook", catalog.join("de", "en").byId("alpha/cook").target.text)
    }

    @Test
    fun seedIndexFlattensGroupsAreasConcepts() {
        val cards = catalog.join("de", "uk")
        assertEquals(0, cards.byId("alpha/waiter").seedIndex)
        assertEquals(1, cards.byId("alpha/waiter-f").seedIndex)
        assertEquals(3, cards.byId("alpha/mouse").seedIndex)
        assertEquals(6, cards.byId("alpha/the-mouse-sprints").seedIndex)
        assertEquals(8, cards.byId("beta/royal-f").seedIndex)
        assertEquals(10, cards.byId("gamma/door").seedIndex)
    }

    @Test
    fun sieDuVariantsLandInVariantsNotSynonyms() {
        val card = catalog.join("sw", "de").byId("alpha/the-mouse-runs")
        assertEquals("Sehen Sie die Maus?", card.target.text)
        assertEquals(listOf("Siehst du die Maus?"), card.target.variants)
        assertTrue(card.target.synonyms.isEmpty())
    }

    @Test
    fun notesSelectedBySourceLanguageOnly() {
        val deSourced = catalog.join("de", "uk").byId("alpha/the-mouse-sprints")
        assertEquals("Nur im Fixture.", deSourced.target.note)
        val enSourced = catalog.join("en", "uk").byId("alpha/the-mouse-sprints")
        assertNull(enSourced.target.note)
    }

    @Test
    fun componentsFilteredToTargetRealizedAndAreaQualified() {
        // uk realizes mouse but not cook: the joined twin keeps only the mouse component.
        val card = catalog.join("de", "uk").byId("alpha/the-mouse-sprints")
        assertEquals(listOf("alpha/mouse"), card.components)
        assertEquals(CardKind.Phrase, card.kind)
    }

    @Test
    fun greetingPhraseHasNoComponents() {
        assertEquals(emptyList(), catalog.join("de", "sw").byId("alpha/hello").components)
    }

    // -- unit expansion ----------------------------------------------------------------

    @Test
    fun phrasesNeverGetRecognizeUnits() {
        val units = ExerciseUnits.of(catalog.join("sw", "de").byId("alpha/the-mouse-runs"))
        assertEquals(listOf(Role.Produce), units.map { it.role })
        assertEquals("alpha/the-mouse-runs|produce", units.single().key)
    }

    @Test
    fun synonymsExpandToOwnRecognizeUnitsVariantsDoNot() {
        val units = ExerciseUnits.of(catalog.join("de", "uk").byId("alpha/mouse"))
        assertEquals(
            listOf(
                "alpha/mouse|produce",
                "alpha/mouse|recognize|миша",
                "alpha/mouse|recognize|мишеня",
            ),
            units.map { it.key },
        )
        assertEquals(listOf(0, 0, 1), units.map { it.formIndex })
    }

    @Test
    fun recognizeFormKeysAreNfcNormalizedAndCollapsed() {
        val units = ExerciseUnits.of(catalog.join("sw", "de").byId("gamma/door"))
        // Catalog text is decomposed u+combining-diaeresis; keys must be precomposed NFC.
        assertEquals("gamma/door|recognize|Tür", units[1].key)
        assertEquals("gamma/door|recognize|die Türe", units[2].key)
    }

    @Test
    fun unitOrderPinsSeedCardRoleForm() {
        val units = catalog.join("de", "uk").flatMap { ExerciseUnits.of(it) }
        val sorted = units.shuffled().sortedWith(ExerciseUnits.order)
        assertEquals(units.map { it.key }, sorted.map { it.key })
        // produce leads its card's recognize units; cards follow seed order.
        assertEquals(
            listOf(
                "alpha/waiter|produce", "alpha/waiter|recognize|офіціант",
                "alpha/waiter-f|produce", "alpha/waiter-f|recognize|офіціантка",
                "alpha/mouse|produce", "alpha/mouse|recognize|миша", "alpha/mouse|recognize|мишеня",
                "alpha/the-mouse-sprints|produce",
                "beta/royal-f|produce", "beta/royal-f|recognize|княгиня",
                "gamma/door|produce", "gamma/door|recognize|двері",
            ),
            sorted.map { it.key },
        )
    }

    // -- catalog surface ---------------------------------------------------------------

    @Test
    fun availableTargetsEnforcesFiftyConceptThreshold() {
        assertTrue(catalog.availableTargets("de").isEmpty())
    }

    @Test
    fun areaMetadataExposed() {
        assertEquals(listOf("alpha", "beta", "gamma"), catalog.areaNames)
        assertEquals("Альфа", catalog.areaTitle("alpha", "uk"))
        assertNull(catalog.areaTitle("beta", "sw"))
        assertEquals(listOf("start", "more"), catalog.groups.map { it.id })
    }

    @Test
    fun fingerprintIsDeterministicAndContentSensitive() {
        assertEquals(Fixture.catalog().fingerprint, catalog.fingerprint)
        val mutated = Fixture.files.toMutableMap()
        mutated["gamma/sw.json"] = mutated.getValue("gamma/sw.json").replace("mlango", "mlango2")
        val other = Catalog.load(MapCatalogSource(mutated))
        assertTrue(other.fingerprint != catalog.fingerprint)
    }
}
