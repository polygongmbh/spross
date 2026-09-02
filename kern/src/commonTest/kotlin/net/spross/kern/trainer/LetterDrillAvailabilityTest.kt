package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.AlphabetFixture
import net.spross.kern.catalog.AudioFixture
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.Fixture
import net.spross.kern.catalog.MapCatalogSource
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Realization

/**
 * The predicate ladder behind the letter drill: which rows this device can ASK, and which of
 * the learner's own words it can dictate.
 *
 * The `uk` alphabet is authored here rather than reused, because every Sprosse of the ladder is a
 * row shape: a letter with a bundled recording, a letter without one, a letter with no NAME to
 * speak, a row barred from the drill, a gap row whose catalog example can be heard, a gap row
 * that only has the `exampleText` escape hatch, a gap row with nothing at all, and a prose
 * rule. The catalog and audio manifests around it are the shipped fixtures, so the recording
 * lookups are the real ones.
 */
class LetterDrillAvailabilityTest {

    /** Row shapes, in file order — the order `promptableRefs` must come back in. */
    private val ukAlphabet = """
        { "entries": [
          { "glyph": "і", "upper": "І", "name": "і", "ipa": "i",
            "hints": { "en": "a letter with a bundled recording" } },
          { "glyph": "и", "upper": "И", "name": "и", "ipa": "ɪ",
            "hints": { "en": "a letter with no recording" } },
          { "glyph": "ю", "upper": "Ю", "ipa": "ju",
            "hints": { "en": "no name authored — nothing to speak" } },
          { "glyph": "ь", "name": "знак", "drill": false,
            "hints": { "en": "barred from the drill" } },
          { "glyph": "иш", "kind": "digraph", "ipa": "ɪʃ", "example": "mouse",
            "hints": { "en": "a catalog word gaps it, and that word has a recording" } },
          { "glyph": "дз", "kind": "digraph", "ipa": "dz", "exampleText": "дзеркало",
            "hints": { "en": "the escape hatch, which nothing can say" } },
          { "glyph": "щ", "kind": "digraph", "ipa": "ʃtʃ",
            "hints": { "en": "no example at all" } },
          { "glyph": "б д ж", "kind": "rule", "exampleText": "хліб",
            "context": { "en": "word-finally" },
            "hints": { "en": "sheet prose, never a question" } }
        ] }
    """.trimIndent()

    /** Nothing here can be said without a voice — the drill does not exist on such a device. */
    private val silentAlphabet = """
        { "entries": [
          { "glyph": "и", "upper": "И", "name": "и", "ipa": "ɪ", "hints": { "en": "no recording" } },
          { "glyph": "щ", "kind": "digraph", "ipa": "ʃtʃ", "hints": { "en": "no example" } }
        ] }
    """.trimIndent()

    private fun catalog(uk: String = ukAlphabet): Catalog = Catalog.load(
        MapCatalogSource(
            Fixture.files + AlphabetFixture.files + AudioFixture.files + ("alphabet/uk.json" to uk),
        ),
    )

    private fun card(id: String, text: String, seed: Int): Card = Card(
        id = id,
        kind = CardKind.Noun,
        area = "alpha",
        emoji = null,
        seedIndex = seed,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = "de-$id"),
        target = Realization(lang = "uk", text = text),
        promptFeminineMarker = false,
    )

    /** Five consolidated single words plus a phrase card, so the floor can be crossed exactly. */
    private val words = listOf(
        card("mouse", "миша", 1), // a bundled recording
        card("door", "двері", 2), // a bundled recording
        card("waiter", "офіціант", 3), // audible only where a voice stands
        card("royal-f", "княгиня", 4), // audible only where a voice stands
        card("hello", "привіт", 5), // audible only where a voice stands
        card("morning", "доброго ранку", 6), // two words: never a transcription task
    )

    /** Every card consolidated; `mouse` also carries the figures the dictation draw weighs. */
    private fun box(cards: List<Card> = words): BoxState {
        var state = Box.state(cards)
        for (word in cards) {
            val sched = Box.sched(
                word.id,
                stability = 12.0,
                dueMillis = Box.plusDays(Box.day1, 3.0),
                lastReviewMillis = Box.day1,
            )
            state = Box.inject(
                state,
                if (word.id == "mouse") {
                    sched.copy(memory = MemoryState(stability = 12.0, difficulty = 7.5), lapses = 2)
                } else {
                    sched
                },
            )
        }
        return state
    }

    private fun report(hasVoice: Boolean, uk: String = ukAlphabet, cards: List<Card> = words) =
        LetterDrillAvailability.report(catalog(uk), box(cards), "uk", hasVoice)

    // MARK: - No file, no drill

    @Test
    fun aLanguageWithNoAlphabetFileHasNoDrill() {
        val silent = LetterDrillAvailability.report(catalog(), Box.state(emptyList()), "en", hasVoice = true)
        assertNull(silent.alphabet)
        assertFalse(silent.drillAvailable)
        assertEquals(emptyList<String>(), silent.promptableRefs)
        assertFalse(LetterDrillAvailability.drillExists(catalog(), "en", hasVoice = true))
    }

    // MARK: - Which rows can be asked

    /**
     * A bundled letter recording asks the row even on a device with no voice; without one the
     * synthesizer is the only way to say it.
     */
    @Test
    fun aRecordingAsksALetterWhereNoVoiceCan() {
        assertEquals(listOf("і", "иш", "дз"), report(hasVoice = false).promptableRefs)
        assertEquals(listOf("і", "и", "иш", "дз"), report(hasVoice = true).promptableRefs)
    }

    /**
     * The NAME is what is spoken, so a row without one cannot be asked even where a voice
     * stands; a row barred from the drill and a prose rule are never asked at all.
     */
    @Test
    fun aRowWithNothingToSpeakIsNeverAsked() {
        val refs = report(hasVoice = true).promptableRefs
        assertFalse("ю" in refs, "a letter with no name")
        assertFalse("ь" in refs, "drill:false")
        assertFalse("б д ж" in refs, "a rule row")
        assertFalse("щ" in refs, "a gap row with no example at all")
    }

    /**
     * QUIRK, shared by both platforms and deliberately pinned: the `exampleText` fallback is not
     * audibility-filtered, so an escape-hatch row stays promptable on a device that cannot say
     * it — and the drill then shows a dead speaker.
     */
    @Test
    fun theExampleTextFallbackIsNotFilteredByWhatCanBeHeard() {
        val silent = report(hasVoice = false)
        val hatch = assertNotNull(silent.alphabet).entry("дз")!!
        assertEquals(
            listOf(LetterDrill.AlphabetExampleWord("дзеркало", null, false)),
            silent.examples(hatch),
        )
        assertTrue("дз" in silent.promptableRefs)

        // A row with no example and no hatch simply has nothing to gap.
        val barren = silent.alphabet!!.entry("щ")!!
        assertEquals(emptyList<LetterDrill.AlphabetExampleWord>(), silent.examples(barren))
    }

    /** A gap word carries its slug — and the learner's own words are flagged as theirs. */
    @Test
    fun aGapWordKeepsItsProvenanceAndSaysWhetherTheLearnerHoldsIt() {
        val held = report(hasVoice = false)
        val row = assertNotNull(held.alphabet).entry("иш")!!
        assertEquals(
            listOf(LetterDrill.AlphabetExampleWord("миша", "mouse", known = true)),
            held.examples(row),
        )
        // The same row on an empty box: the word stands, the learner just does not hold it.
        val stranger = LetterDrillAvailability.report(catalog(), Box.state(emptyList()), "uk", false)
        assertEquals(
            listOf(LetterDrill.AlphabetExampleWord("миша", "mouse", known = false)),
            stranger.examples(row),
        )
    }

    /** The chip predicate answers the same question, without walking the box. */
    @Test
    fun theChipPredicateAgreesWithTheReport() {
        assertTrue(LetterDrillAvailability.drillExists(catalog(), "uk", hasVoice = false))
        assertFalse(LetterDrillAvailability.drillExists(catalog(silentAlphabet), "uk", hasVoice = false))
        assertTrue(LetterDrillAvailability.drillExists(catalog(silentAlphabet), "uk", hasVoice = true))
        assertFalse(report(hasVoice = false, uk = silentAlphabet).drillAvailable)
        assertTrue(report(hasVoice = true, uk = silentAlphabet).drillAvailable)
    }

    // MARK: - The dictation pool

    /** A transcription task is ONE word, and a word nothing on this device can play is not one. */
    @Test
    fun theDictationPoolIsSingleAudibleWordsTheLearnerAlreadyHolds() {
        val silent = report(hasVoice = false)
        assertEquals(listOf("mouse", "door"), silent.dictationCandidates.map { it.card.id })
        assertFalse(silent.dictationAvailable)
        assertEquals(LetterDrill.MAX_LEVEL_WITHOUT_DICTATION, silent.maxLevel)

        val voiced = report(hasVoice = true)
        assertEquals(
            listOf("mouse", "door", "waiter", "royal-f", "hello"),
            voiced.dictationCandidates.map { it.card.id },
        )
        assertFalse("morning" in voiced.dictationCandidates.map { it.card.id }, "a phrase card is two words")
        assertEquals(6, voiced.consolidatedCards, "the whole vocabulary paces the entry Sprosse")
    }

    /** The floor is `>=`: one word short and the ramp stops one Sprosse below dictation. */
    @Test
    fun theFloorIsExactlyFiveCandidates() {
        val enough = report(hasVoice = true)
        assertEquals(LetterDrillAvailability.DICTATION_FLOOR, enough.dictationCandidates.size)
        assertTrue(enough.dictationAvailable)
        assertEquals(LetterDrill.MAX_LEVEL_WITH_DICTATION, enough.maxLevel)

        val short = report(hasVoice = true, cards = words.filter { it.id != "hello" })
        assertEquals(LetterDrillAvailability.DICTATION_FLOOR - 1, short.dictationCandidates.size)
        assertFalse(short.dictationAvailable)
        assertEquals(LetterDrill.MAX_LEVEL_WITHOUT_DICTATION, short.maxLevel)
    }

    /** The two figures the draw weighs are READ from the schedule, never re-derived. */
    @Test
    fun aCandidateCarriesItsScheduleFigures() {
        val voiced = report(hasVoice = true)
        val mouse = voiced.dictationCandidates.first { it.card.id == "mouse" }
        assertEquals(7.5, mouse.difficulty, 0.0001)
        assertEquals(2, mouse.lapses)
        val door = voiced.dictationCandidates.first { it.card.id == "door" }
        assertEquals(5.0, door.difficulty, 0.0001)
        assertEquals(0, door.lapses)
    }
}
