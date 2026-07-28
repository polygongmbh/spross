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
        val card = catalog.join("sw", "uk").byId("waiter-f")
        assertTrue(card.promptFeminineMarker)
        assertEquals("mhudumu", card.source.text)
        assertEquals("офіціантка", card.target.text)
        assertEquals("waiter", card.feminineOf)
    }

    @Test
    fun feminineUsesOwnSourceRealizationWithoutMarker() {
        val card = catalog.join("de", "uk").byId("waiter-f")
        assertFalse(card.promptFeminineMarker)
        assertEquals("Kellnerin", card.source.text)
    }

    @Test
    fun feminineSkippedWhenBaseSourceRealizationAlsoMissing() {
        // beta has no sw file: neither royal-f nor base royal realize in the source.
        assertTrue(catalog.join("sw", "uk").none { it.id == "royal-f" })
    }

    @Test
    fun feminineSkippedWhenTargetDoesNotRealizeIt() {
        assertTrue(catalog.join("de", "sw").none { it.id == "waiter-f" })
    }

    // -- coverage skips ----------------------------------------------------------------

    @Test
    fun sparseTargetCoverageSkipsConcepts() {
        val ids = catalog.join("de", "uk").map { it.id }
        assertEquals(listOf("waiter", "waiter-f", "mouse", "the-mouse-sprints", "royal-f", "door"), ids)
    }

    @Test
    fun nonFeminineConceptWithoutSourceRealizationSkipped() {
        // sw realizes cook but source uk does not — no prompt, no card.
        assertEquals(
            listOf("waiter", "mouse", "door"),
            catalog.join("uk", "sw").map { it.id },
        )
    }

    // -- parsing details ---------------------------------------------------------------

    @Test
    fun enToPrefixSurvivesParsing() {
        assertEquals(listOf("to "), catalog.languages.getValue("en").optionalVerbPrefixes)
        assertEquals("to cook", catalog.join("de", "en").byId("cook").target.text)
    }

    @Test
    fun seedIndexFlattensGroupsAreasConcepts() {
        val cards = catalog.join("de", "uk")
        assertEquals(0, cards.byId("waiter").seedIndex)
        assertEquals(1, cards.byId("waiter-f").seedIndex)
        assertEquals(3, cards.byId("mouse").seedIndex)
        assertEquals(6, cards.byId("the-mouse-sprints").seedIndex)
        assertEquals(8, cards.byId("royal-f").seedIndex)
        assertEquals(10, cards.byId("door").seedIndex)
    }

    @Test
    fun sieDuVariantsLandInVariantsNotSynonyms() {
        val card = catalog.join("sw", "de").byId("the-mouse-runs")
        assertEquals("Sehen Sie die Maus?", card.target.text)
        assertEquals(listOf("Siehst du die Maus?"), card.target.variants)
        assertTrue(card.target.synonyms.isEmpty())
    }

    @Test
    fun notesSelectedBySourceLanguageOnly() {
        val deSourced = catalog.join("de", "uk").byId("the-mouse-sprints")
        assertEquals("Nur im Fixture.", deSourced.target.note)
        val enSourced = catalog.join("en", "uk").byId("the-mouse-sprints")
        assertNull(enSourced.target.note)
    }

    @Test
    fun componentsFilteredToTargetRealized() {
        // uk realizes mouse but not cook: the joined twin keeps only the mouse component.
        val card = catalog.join("de", "uk").byId("the-mouse-sprints")
        assertEquals(listOf("mouse"), card.components)
        assertEquals(CardKind.Phrase, card.kind)
    }

    @Test
    fun greetingPhraseHasNoComponents() {
        assertEquals(emptyList(), catalog.join("de", "sw").byId("hello").components)
    }

    // -- rotation forms ----------------------------------------------------------------

    @Test
    fun synonymsJoinTheRotationVariantsDoNot() {
        val mouse = catalog.join("de", "uk").byId("mouse")
        assertEquals("миша", mouse.target.text)
        assertEquals(listOf("мишеня"), mouse.target.synonyms)
        assertEquals(listOf("мишка"), mouse.target.variants) // grading/display only
    }

    // -- grammar -----------------------------------------------------------------------

    /**
     * Authored `grammar` reaches both sides of the card, per language and unmerged.
     * Pinned against the FIXTURE on purpose: the real catalog's grammar is content
     * an editor may legitimately change (a Swahili plural landing on a noun is not
     * a join regression), so the real-catalog side is guarded by lint on the values
     * instead — see CatalogLintTest.grammarValuesAreBareAndTrimmed.
     */
    @Test
    fun grammarRidesThroughPerSide() {
        val door = catalog.join("de", "uk").byId("door")
        assertEquals(mapOf("gender" to "die", "plural" to "-en"), door.source.grammar)
        assertEquals(mapOf("plural" to "only"), door.target.grammar)
    }

    /** A verb: absent `grammar` is the correct authoring, and lands as empty, never null. */
    @Test
    fun realizationWithoutGrammarGetsAnEmptyMap() {
        assertTrue(catalog.join("de", "sw").byId("cook").target.grammar.isEmpty())
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
    fun areaEmojiIsLanguageNeutralAndNullForUnknownAreas() {
        assertEquals("🌀", catalog.areaEmoji("gamma"))
        assertNull(catalog.areaEmoji("delta"))
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
