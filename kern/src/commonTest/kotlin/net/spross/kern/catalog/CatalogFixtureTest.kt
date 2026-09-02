package net.spross.kern.catalog

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.trainer.SwahiliConcord
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
        assertEquals(
            listOf(
                "waiter", "waiter-f", "mouse", "the-mouse-sprints", "royal-f",
                "door", "im-learning", "i-speak-a-little", "how-do-you-say-this",
            ),
            ids,
        )
    }

    @Test
    fun nonFeminineConceptWithoutSourceRealizationSkipped() {
        // sw realizes cook but source uk does not — no prompt, no card.
        assertEquals(
            listOf("waiter", "mouse", "door", "im-learning", "i-speak-a-little", "how-do-you-say-this"),
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

    /** The reader's own language wins; the language being explained is what everyone else gets. */
    @Test
    fun notesPreferTheReaderAndFallBackToTheLanguageExplained() {
        val deSourced = catalog.join("de", "uk").byId("the-mouse-sprints")
        assertEquals("Nur im Fixture.", deSourced.target.note)
        val enSourced = catalog.join("en", "uk").byId("the-mouse-sprints")
        assertEquals("Лише у фікстурі.", enSourced.target.note)
    }

    /**
     * The fallback is the realization's OWN language and nothing else — a third language's
     * note is still a language the reader cannot read, so it stays hidden.
     */
    @Test
    fun aThirdLanguagesNoteNeverLeaks() {
        // uk `mouse` is annotated in sw alone: neither the en reader nor uk itself.
        assertNull(catalog.join("en", "uk").byId("mouse").target.note)
        assertEquals("Panya tu.", catalog.join("sw", "uk").byId("mouse").target.note)
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
        assertNull(catalog.areaSubtitle("gamma", "en")) // gamma/en.json authors none
        assertNull(catalog.areaSubtitle("alpha", "de"))
        assertNull(catalog.areaSubtitle("delta", "de"))
    }

    @Test
    fun misspelledAreaHeadingKeyIsRejected() {
        val broken = Fixture.files + mapOf(
            "areas/gamma/sw.json" to """{ "title": "Gamma", "subtitel": "…", "words": {} }""",
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("subtitel" in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun areaEmojiIsLanguageNeutralAndNullForUnknownAreas() {
        assertEquals("🌀", catalog.areaEmoji("gamma"))
        assertNull(catalog.areaEmoji("delta"))
    }

    // -- language names ----------------------------------------------------------------

    @Test
    fun languageNamesCarryTheAuthoredFormsAndFallBackToTheCitationForm() {
        val suaheli = catalog.languageName("de", "sw")!!
        assertEquals("Suaheli", suaheli.name)
        assertEquals("auf Suaheli", suaheli.inForm)
        assertEquals(listOf("Kisuaheli"), suaheli.variants)
        // speak/learn unauthored: German's object form IS the citation form.
        assertEquals("Suaheli", suaheli.form(LanguageMarker.Speak))
        assertEquals("Suaheli", suaheli.form(LanguageMarker.Learn))
        val german = catalog.languageName("uk", "de")!!
        assertEquals("німецькою", german.form(LanguageMarker.Speak))
        assertEquals("німецьку", german.form(LanguageMarker.Learn))
        // uk names суахілі without a speak form — the fallback is the name, not the adverbial.
        assertEquals("суахілі", catalog.languageName("uk", "sw")!!.form(LanguageMarker.Speak))
        assertEquals("мовою суахілі", catalog.languageName("uk", "sw")!!.inForm)
    }

    /** File presence is the registry, exactly as it is for an alphabet. */
    @Test
    fun aLanguageWithoutATableNamesNothingAndIsNamedAnyway() {
        assertNull(catalog.languageName("en", "de"))
        assertNull(catalog.languageName("sw", "pt")) // table present, entry absent
        assertEquals("Portugiesisch", catalog.languageName("de", "pt")?.name)
    }

    @Test
    fun languageNameEditsRestampTheFingerprint() {
        val edited = Fixture.files + mapOf(
            "language-names/de.json" to Fixture.names.getValue("language-names/de.json")
                .replace("Suaheli", "Swahili"),
        )
        assertTrue(Catalog.load(MapCatalogSource(edited)).fingerprint != catalog.fingerprint)
    }

    @Test
    fun anUndeclaredNamedLanguageFailsTheParse() {
        val broken = Fixture.files + mapOf(
            "language-names/sw.json" to Fixture.names.getValue("language-names/sw.json")
                .replace("\"uk\":", "\"xx\":"),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("undeclared language \"xx\"" in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun aLanguageNameWithoutAnInFormFailsTheParse() {
        val broken = Fixture.files + mapOf(
            "language-names/de.json" to Fixture.names.getValue("language-names/de.json")
                .replace("\"in\": \"auf Suaheli\", ", ""),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("missing \"in\"" in error.message.orEmpty(), "message: ${error.message}")
    }

    // -- language markers --------------------------------------------------------------

    /**
     * The whole rule in one pair: BOTH sides name the TARGET, each out of its own table.
     * The German prompt therefore says which language is being learned, and the Swahili
     * answer says the same thing about itself.
     */
    @Test
    fun bothSidesResolveTheMarkerAgainstTheirOwnNameForTheTarget() {
        val card = catalog.join("de", "sw").byId("im-learning")
        assertEquals("Ich lerne Suaheli.", card.source.text)
        assertEquals("Ninajifunza Kiswahili.", card.target.text)
        val reverse = catalog.join("sw", "de").byId("im-learning")
        assertEquals("Ninajifunza Kijerumani.", reverse.source.text)
        assertEquals("Ich lerne Deutsch.", reverse.target.text)
    }

    /** Each marker picks its own form, and an unauthored one falls back to the name. */
    @Test
    fun everyMarkerFormResolvesInTheSentenceThatAsksForIt() {
        val toUk = catalog.join("de", "uk")
        assertEquals("Я вчу українську.", toUk.byId("im-learning").target.text)
        assertEquals("Я трохи розмовляю українською.", toUk.byId("i-speak-a-little").target.text)
        assertEquals("Як це сказати українською?", toUk.byId("how-do-you-say-this").target.text)
        assertEquals("Wie sagt man das auf Ukrainisch?", toUk.byId("how-do-you-say-this").source.text)
        assertEquals(
            "Hii inasemwaje kwa Kiswahili?",
            catalog.join("de", "sw").byId("how-do-you-say-this").target.text,
        )
        // uk names суахілі without a speak form: the citation form stands in.
        assertEquals(
            "Я трохи розмовляю суахілі.",
            catalog.join("uk", "sw").byId("i-speak-a-little").source.text,
        )
    }

    /** No table entry for the target, no sentence: the same honest-out as a missing word. */
    @Test
    fun aSideThatCannotNameTheTargetDropsTheConcept() {
        // en authors no table at all, so it drops as a target (its own name)…
        assertTrue(catalog.join("de", "en").none { it.id == "im-learning" })
        // …and as a source (its name for the target).
        assertTrue(catalog.join("en", "sw").none { it.id == "im-learning" })
        // The unmarked concepts of the same area are untouched.
        assertEquals("mlango", catalog.join("de", "sw").byId("door").target.text)
    }

    @Test
    fun aSecondMarkerInOneStringFailsTheParse() {
        val error = loadWithGammaDeText("Ich lerne {language} auf {language}.")
        assertTrue("second language marker" in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun anUnknownMarkerFormFailsTheParse() {
        val error = loadWithGammaDeText("Ich lerne {language-of}.")
        assertTrue("unknown language marker" in error.message.orEmpty(), "message: ${error.message}")
    }

    /** Nothing re-capitalizes what a marker inserts, so a sentence may never open with one. */
    @Test
    fun aStringInitialMarkerFailsTheParse() {
        val error = loadWithGammaDeText("{language} lerne ich.")
        assertTrue("language marker opens" in error.message.orEmpty(), "message: ${error.message}")
    }

    private fun loadWithGammaDeText(text: String): CatalogFormatException {
        val broken = Fixture.files + mapOf(
            "areas/gamma/de.json" to Fixture.files.getValue("areas/gamma/de.json")
                .replace("Ich lerne {language}.", text),
        )
        return assertFailsWith { Catalog.load(MapCatalogSource(broken)) }
    }

    // -- drill frames ------------------------------------------------------------------

    @Test
    fun framesJoinSymmetricallyInBothDirections() {
        val forward = catalog.phraseTemplates("de", "sw")
        assertEquals(
            listOf("bus-arrives-at", "i-have-n-keys", "we-have-n-chairs", "im-learning-since"),
            forward.map { it.id },
        )
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

    /** The counted noun's class is the Swahili realization's, so it rides the answer side too. */
    @Test
    fun swahiliNounClassRoundTripsFromTheAnswerRealization() {
        val forward = catalog.phraseTemplates("de", "sw").first { it.id == "we-have-n-chairs" }
        assertEquals(SwahiliConcord.NounClass.KI_VI, forward.swahiliNounClass)
        // Swapped, Swahili is the prompt and German the typed answer: German has no noun
        // classes, so the field drops rather than following the frame.
        val swapped = catalog.phraseTemplates("sw", "de").first { it.id == "we-have-n-chairs" }
        assertNull(swapped.swahiliNounClass)
        // The N-class frames shipping beside it carry no class at all (SwahiliConcord).
        assertNull(catalog.phraseTemplates("de", "sw").first { it.id == "i-have-n-keys" }.swahiliNounClass)
    }

    /** A class the concord table does not carry would render plain — fail the build instead. */
    @Test
    fun anUnknownSwahiliNounClassFailsTheParse() {
        val broken = Fixture.files + mapOf(
            "phrases/sw.json" to Fixture.files.getValue("phrases/sw.json").replace("KI_VI", "M_MI"),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("unknown swahiliNounClass" in error.message.orEmpty(), "message: ${error.message}")
    }

    /** Only the ANSWER side needs generated number words — a pack-less language still prompts. */
    @Test
    fun targetWithoutATrainerPackHasNoFrames() {
        assertTrue(catalog.phraseTemplates("de", "pt").isEmpty())
        assertEquals(
            listOf("O autocarro chega às {slot}."),
            catalog.phraseTemplates("pt", "sw").map { it.sourceTemplate },
        )
    }

    /**
     * A frame resolves its marker exactly as a realization does, and BEFORE the template is
     * built — so `{slot}` filling never meets one. A side that cannot name the target loses
     * the frame for that pair and keeps the rest.
     */
    @Test
    fun frameMarkersResolvePerSideAndDropWhereTheTableCannotName() {
        val deSw = catalog.phraseTemplates("de", "sw").first { it.id == "im-learning-since" }
        assertEquals("Ich lerne seit {slot} Suaheli.", deSw.sourceTemplate)
        assertEquals("Ninajifunza Kiswahili tangu mwaka {slot}.", deSw.targetTemplate)
        val deUk = catalog.phraseTemplates("de", "uk").first { it.id == "im-learning-since" }
        assertEquals("Ich lerne seit {slot} Ukrainisch.", deUk.sourceTemplate)
        assertEquals("Я вчу українську з {slot}.", deUk.targetTemplate)
        // pt realizes the frame but names no language at all.
        assertTrue(catalog.phraseTemplates("pt", "sw").none { it.id == "im-learning-since" })
    }

    @Test
    fun aMalformedMarkerInAFrameFailsTheParse() {
        val broken = Fixture.files + mapOf(
            "phrases/sw.json" to Fixture.drills.getValue("phrases/sw.json")
                .replace("{language} tangu", "{language-of} tangu"),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("unknown language marker" in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun absentDrillsFolderIsLegal() {
        val bare = Catalog.load(MapCatalogSource(Fixture.files - Fixture.drills.keys))
        assertTrue(bare.phraseTemplates("de", "sw").isEmpty())
        assertEquals(catalog.join("de", "sw").size, bare.join("de", "sw").size)
    }

    /**
     * The prose beside the reference table is content, so it rides the drills file and is
     * picked by the reader — with an English fallback, unlike a card's note.
     */
    @Test
    fun numberNotesFollowTheReaderAndFallBackToEnglish() {
        assertEquals(listOf("Sechs, sieben und neun sind entlehnt."), catalog.numberNotes("sw", "de"))
        assertEquals(listOf("Six, seven and nine are loans."), catalog.numberNotes("sw", "en"))
        // uk authors English only: a reader without their own wording still gets the section.
        assertEquals(listOf("The numeral sets the form."), catalog.numberNotes("uk", "de"))
        // de authors none, and no other language's prose stands in for it.
        assertTrue(catalog.numberNotes("de", "de").isEmpty())
        assertTrue(catalog.numberNotes("xx", "de").isEmpty())
    }

    @Test
    fun blankNumberNoteFailsTheParse() {
        val broken = Fixture.files + mapOf(
            "phrases/uk.json" to Fixture.drills.getValue("phrases/uk.json")
                .replace("The numeral sets the form.", " "),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("numberNotes.en" in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun malformedFrameFileNamesThePath() {
        val broken = Fixture.files + mapOf(
            "phrases/uk.json" to """{ "frames": { "no-such-frame": { "text": "{slot}." } } }""",
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("phrases/uk.json" in error.message.orEmpty(), "message: ${error.message}")
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
            "phrases/frames.json" to Fixture.drills.getValue("phrases/frames.json")
                .replace("\"slot\": \"clock\"", "\"slot\": \"forms\""),
        )
        val error = assertFailsWith<CatalogFormatException> { Catalog.load(MapCatalogSource(broken)) }
        assertTrue("unknown slot \"forms\"" in error.message.orEmpty(), "message: ${error.message}")
    }

    /** Frames ride the RAW source: editing one must not restamp a running box. */
    @Test
    fun frameEditsLeaveTheFingerprintAlone() {
        val edited = Fixture.files + mapOf(
            "phrases/sw.json" to Fixture.drills.getValue("phrases/sw.json").replace("Basi", "Gari"),
        )
        assertEquals(catalog.fingerprint, Catalog.load(MapCatalogSource(edited)).fingerprint)
    }

    @Test
    fun fingerprintIsDeterministicAndContentSensitive() {
        assertEquals(Fixture.catalog().fingerprint, catalog.fingerprint)
        val mutated = Fixture.files.toMutableMap()
        mutated["areas/gamma/sw.json"] = mutated.getValue("areas/gamma/sw.json").replace("mlango", "mlango2")
        val other = Catalog.load(MapCatalogSource(mutated))
        assertTrue(other.fingerprint != catalog.fingerprint)
    }
}
