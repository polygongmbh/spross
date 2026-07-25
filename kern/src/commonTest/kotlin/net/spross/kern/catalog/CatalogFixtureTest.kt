package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
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

    // -- rotation forms ----------------------------------------------------------------

    @Test
    fun synonymsJoinTheRotationVariantsDoNot() {
        val mouse = catalog.join("de", "uk").byId("alpha/mouse")
        assertEquals("миша", mouse.target.text)
        assertEquals(listOf("мишеня"), mouse.target.synonyms)
        assertEquals(listOf("мишка"), mouse.target.variants) // grading/display only
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
