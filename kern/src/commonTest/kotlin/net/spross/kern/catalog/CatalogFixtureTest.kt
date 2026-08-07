package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.trainer.TrainerKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Join and parse over the synthetic [Fixture]; audio lives in [CatalogAudioFixtureTest]. */
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

    /** Optional and per-language: gamma authors one, alpha authors none at all. */
    @Test
    fun areaSubtitleReadsBesideTheTitle() {
        assertEquals("Alles dreht sich.", catalog.areaSubtitle("gamma", "de"))
        assertEquals("Усе обертається.", catalog.areaSubtitle("gamma", "uk"))
        assertEquals("Gamma", catalog.areaTitle("gamma", "de"))
        assertNull(catalog.areaSubtitle("gamma", "en")) // no gamma/en.json at all
        assertNull(catalog.areaSubtitle("alpha", "de"))
        assertNull(catalog.areaSubtitle("delta", "de"))
    }

    @Test
    fun misspelledAreaHeadingKeyIsRejected() {
        val broken = Fixture.files + mapOf(
            "gamma/sw.json" to """{ "title": "Gamma", "subtitel": "…", "words": {} }""",
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("subtitel" in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun areaEmojiIsLanguageNeutralAndNullForUnknownAreas() {
        assertEquals("🌀", catalog.areaEmoji("gamma"))
        assertNull(catalog.areaEmoji("delta"))
    }

    // -- drill frames ------------------------------------------------------------------

    @Test
    fun framesJoinSymmetricallyInBothDirections() {
        val forward = catalog.phraseTemplates("de", "sw")
        assertEquals(listOf("bus-arrives-at", "i-have-n-keys"), forward.map { it.id })
        assertEquals("Der Bus kommt um {slot} Uhr.", forward[0].sourceTemplate)
        assertEquals("Basi linakuja {slot}.", forward[0].targetTemplate)
        val reverse = catalog.phraseTemplates("sw", "de").first { it.id == "bus-arrives-at" }
        assertEquals("Basi linakuja {slot}.", reverse.sourceTemplate)
        assertEquals("Der Bus kommt um {slot} Uhr.", reverse.targetTemplate)
        assertEquals(TrainerKind.Clock, reverse.slotKind)
    }

    @Test
    fun frameRealizedInOnlyOneLanguageNeverJoins() {
        // learning-since-year is German-only.
        assertTrue(catalog.phraseTemplates("de", "sw").none { it.id == "learning-since-year" })
        assertTrue(catalog.phraseTemplates("de", "uk").none { it.id == "learning-since-year" })
    }

    /** count/masculineNumeral/notes belong to the ANSWER realization, never to the pair. */
    @Test
    fun answerSideCarriesAgreementAndNote() {
        val forward = catalog.phraseTemplates("de", "uk").first { it.id == "i-have-n-keys" }
        assertEquals("ключі", forward.countForms?.form(3))
        assertTrue(forward.masculineNumeral)
        assertEquals("Zahlwort-Kongruenz.", forward.note)
        // Swapping the pair swaps which realization those fields come from — and the uk
        // agreement follows uk into the PROMPT slot, where the frame still has to render it.
        val swapped = catalog.phraseTemplates("uk", "de").first { it.id == "i-have-n-keys" }
        assertNull(swapped.countForms)
        assertEquals("ключі", swapped.sourceCountForms?.form(3))
        assertFalse(swapped.masculineNumeral)
        assertNull(swapped.note)
        assertEquals(listOf("Ich habe {slot} Schluessel."), swapped.acceptedFrames)
    }

    /** Only the ANSWER side needs generated number words — a pack-less language still prompts. */
    @Test
    fun targetWithoutATrainerPackHasNoFrames() {
        assertTrue(catalog.phraseTemplates("de", "fr").isEmpty())
        assertEquals(
            listOf("Le bus arrive à {slot}."),
            catalog.phraseTemplates("fr", "sw").map { it.sourceTemplate },
        )
    }

    @Test
    fun absentDrillsFolderIsLegal() {
        val bare = Catalog.load(MapCatalogSource(Fixture.files - Fixture.drills.keys))
        assertTrue(bare.phraseTemplates("de", "sw").isEmpty())
        assertEquals(catalog.join("de", "sw").size, bare.join("de", "sw").size)
    }

    @Test
    fun malformedFrameFileNamesThePath() {
        val broken = Fixture.files + mapOf(
            "drills/uk.json" to """{ "frames": { "no-such-frame": { "text": "{slot}." } } }""",
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("drills/uk.json" in error.message.orEmpty(), "message: ${error.message}")
    }

    /**
     * `forms` is not a frame slot kind and must not quietly become one.
     * A number form has no phrase generator — a fraction or an ordinal needs the frame to
     * decline around it, and no agreement device runs that way (`docs/backlog.md`).
     * The parser is the outer seal; `PhraseTemplate`'s init is the inner one.
     */
    @Test
    fun formsIsNotAFrameSlotKind() {
        val broken = Fixture.files + mapOf(
            "drills/frames.json" to Fixture.drills.getValue("drills/frames.json")
                .replace("\"slot\": \"clock\"", "\"slot\": \"forms\""),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("unknown slot \"forms\"" in error.message.orEmpty(), "message: ${error.message}")
    }

    /** Frames ride the RAW source: editing one must not restamp a running box. */
    @Test
    fun frameEditsLeaveTheFingerprintAlone() {
        val edited = Fixture.files + mapOf(
            "drills/sw.json" to Fixture.drills.getValue("drills/sw.json").replace("Basi", "Gari"),
        )
        assertEquals(catalog.fingerprint, Catalog.load(MapCatalogSource(edited)).fingerprint)
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
