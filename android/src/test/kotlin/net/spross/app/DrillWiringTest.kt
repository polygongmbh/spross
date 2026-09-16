package net.spross.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.CountryName
import net.spross.kern.catalog.LanguageName
import net.spross.kern.catalog.NationalityName
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.CountryDrillRun
import net.spross.kern.trainer.CountryDrillRunConfig
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.LetterDrillRun
import net.spross.kern.trainer.LetterDrillRunConfig
import net.spross.kern.trainer.ScrambleTokenizer
import net.spross.kern.trainer.SentenceScrambleAvailability
import net.spross.kern.trainer.SentenceScrambleRun
import net.spross.kern.trainer.SentenceScrambleRunConfig
import net.spross.kern.trainer.NumbersExercise
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.NumbersRun
import net.spross.kern.trainer.WordScrambleAvailability
import net.spross.kern.trainer.WordScrambleRun
import net.spross.kern.trainer.WordScrambleRunConfig

/**
 * What the APP does with kern's drill runs — which intent each affordance sends, which acts
 * an effect asks for, and what the screen reads back. The rules themselves (the ramp, the
 * draw, the verdict ladder, when the way out is offered) belong to `:kern:jvmTest`; nothing
 * here re-tests them.
 *
 * The harness is the flows with the platform stripped out: record the tones, the focus
 * releases and the silences, and read the beat off the flow instead of running one.
 */
class DrillWiringTest {

    /** The platform half, recorded: everything a flow hands outside the run. */
    private class Platform {
        val tones = mutableListOf<ToneKind>()
        var focusReleases = 0
        var silences = 0
        var screenReader = false
    }

    // MARK: - The slot run

    private fun slots(platform: Platform, seed: Int = 7): NumbersFlow = NumbersFlow(
        start = NumbersRun.open(NumbersMode(NumbersExercise.Counting, "de"), Random(seed)),
        // A run with no language info grades plainly — enough to drive the wiring.
        normalizer = null,
        rng = Random(seed),
        onTone = { platform.tones += it },
        onReleaseFocus = { platform.focusReleases += 1 },
        onSilence = { platform.silences += 1 },
        screenReaderOn = { platform.screenReader },
    )

    @Test
    fun finishingTheWordArmsTheBeatAndSoundsTheCue() {
        val platform = Platform()
        val flow = slots(platform)
        flow.type(flow.state.currentTask.accepted.first())
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceTier.Live, flow.armedBeat)
    }

    /** A timed screen change under a screen reader truncates what it just announced. */
    @Test
    fun aScreenReaderIsOfferedATapInsteadOfATimedChange() {
        val platform = Platform().apply { screenReader = true }
        val flow = slots(platform)
        flow.type(flow.state.currentTask.accepted.first())
        assertNull(flow.armedBeat)
        assertTrue(flow.awaitsConfirm)
    }

    /** The next prompt must never render one frame carrying the last one's answer. */
    @Test
    fun theFieldClearsWithTheQuestion() {
        val platform = Platform()
        val flow = slots(platform)
        val first = flow.state.index
        flow.type(flow.state.currentTask.accepted.first())
        flow.confirm()
        assertEquals(first + 1, flow.state.index)
        assertEquals("", flow.input)
    }

    /** D5: a clip may never follow the learner onto the next question. */
    @Test
    fun bookingAQuestionSilencesWhateverIsSounding() {
        val platform = Platform()
        val flow = slots(platform)
        flow.type(flow.state.currentTask.accepted.first())
        val before = platform.silences
        flow.confirm()
        assertTrue(platform.silences > before, "the question that ended cut its own reading")
    }

    /** The "?" raises the table AND books the amber debt while the answer is still owed. */
    @Test
    fun lookingUpWhileTheAnswerIsOwedRaisesTheTableAndCostsTheSprosse() {
        val flow = slots(Platform())
        flow.lookUp()
        assertTrue(flow.showingReference)
        assertTrue(flow.state.hintUsed)
    }

    @Test
    fun closingAnUntouchedRunReportsNothingAndStoresNothing() {
        val flow = slots(Platform())
        val closed = flow.close(standingRecord = 0, standingProgress = emptyMap())
        assertNull(closed.summary)
        assertTrue(closed.progressBookings.isEmpty())

        val answered = slots(Platform())
        answered.type(answered.state.currentTask.accepted.first())
        answered.confirm()
        val result = answered.close(standingRecord = 0, standingProgress = emptyMap())
        assertEquals(1, assertNotNull(result.summary).done)
    }

    // MARK: - The letter run

    private fun letter(glyph: String, name: String) = AlphabetEntry(
        ref = glyph,
        glyph = glyph,
        upper = glyph.uppercase(),
        kind = AlphabetKind.Letter,
        name = name,
        ipa = glyph,
        exampleSlug = null,
        exampleText = null,
        hints = emptyMap(),
        context = emptyMap(),
        drill = true,
        mine = true,
        section = null,
        confusableLook = emptyList(),
        confusableSound = emptyList(),
    )

    private val alphabet = Alphabet(
        language = "uk",
        sections = emptyList(),
        entries = listOf(
            letter("а", "а"), letter("б", "бе"), letter("в", "ве"),
            letter("г", "ге"), letter("д", "де"), letter("е", "е"),
        ),
    )

    private fun letters(platform: Platform, seed: Int = 42): LetterDrillFlow {
        val report = LetterDrillAvailability.Report(
            language = "uk",
            alphabet = alphabet,
            promptableRefs = alphabet.entries.map { it.ref },
            dictationCandidates = emptyList(),
            gapWords = emptyMap(),
            growingCards = 0,
        )
        val config = LetterDrillRunConfig(report, cards = emptyMap(), dictationGrader = null)
        return LetterDrillFlow(
            start = LetterDrillRun.open(config, Random(seed)),
            rng = Random(seed),
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )
    }

    @Test
    fun theRightTileArmsTheBeatAndTheWrongOneWaitsForATap() {
        val platform = Platform()
        val hit = letters(platform)
        hit.choose(assertNotNull(hit.state.task).display)
        assertEquals(AdvanceTier.Explicit, hit.armedBeat)
        assertTrue(ToneKind.Correct in platform.tones)

        val missed = letters(Platform())
        val task = assertNotNull(missed.state.task)
        missed.choose(task.choices.orEmpty().first { it != task.display })
        assertNull(missed.armedBeat)
        assertEquals(TurnFeedback.Revealed, missed.state.feedback)
    }

    @Test
    fun aClosedLetterRunReportsItsFiguresAndNoRecord() {
        val flow = letters(Platform())
        flow.choose(assertNotNull(flow.state.task).display)
        val closed = flow.close()
        val summary = assertNotNull(closed.summary)
        assertEquals(1, summary.done)
        // The letter drill keeps no record store, so nothing it does can beat one.
        assertTrue(!summary.newRecord)
    }

    // MARK: - The atlas run

    private fun place(slug: String, flag: String, spoken: List<String>, known: String, learnt: String) =
        AtlasCountryEntry(
            slug = slug,
            flag = flag,
            tier = 1,
            languages = spoken,
            source = CountryName(text = known, nationality = NationalityName("${known}er")),
            target = CountryName(text = learnt, nationality = NationalityName("Wa$learnt")),
        )

    private val atlas = CountryDrillContent(
        source = "de",
        target = "sw",
        countries = listOf(
            place("germany", "🇩🇪", listOf("de"), "Deutschland", "Ujerumani"),
            place("tanzania", "🇹🇿", listOf("sw"), "Tansania", "Tanzania"),
        ),
        languages = listOf(
            AtlasLanguageEntry(
                code = "de",
                tier = 1,
                source = LanguageName("Deutsch", "auf Deutsch"),
                target = LanguageName("Kijerumani", "kwa Kijerumani"),
            ),
            AtlasLanguageEntry(
                code = "sw",
                tier = 1,
                source = LanguageName("Suaheli", "auf Suaheli"),
                target = LanguageName("Kiswahili", "kwa Kiswahili"),
            ),
        ),
    )

    private fun countries(platform: Platform, reverse: Boolean = false, seed: Int = 5) =
        CountryDrillFlow(
            start = CountryDrillRun.open(
                CountryDrillRunConfig(
                    content = atlas,
                    reverse = reverse,
                    fast = false,
                    // A run with no language info grades plainly — enough to drive the wiring.
                    normalizer = null,
                ),
                Random(seed),
            ),
            rng = Random(seed),
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )

    /**
     * Writing the name out IS the answer — the review loop's rule, which the letter drill
     * does not offer and this one does.
     */
    @Test
    fun finishingTheNameArmsTheLiveBeatWithoutACheckTap() {
        val platform = Platform()
        val flow = countries(platform)
        flow.type(flow.state.task.display)
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceTier.Live, flow.armedBeat)
    }

    /** Typing PAST a finished name takes the green with it, so it is never booked. */
    @Test
    fun backingOutOfAFinishedNameDropsTheBeat() {
        val flow = countries(Platform())
        flow.type(flow.state.task.display)
        flow.type(flow.state.task.display + "x")
        assertEquals(TurnFeedback.Neutral, flow.state.feedback)
        assertNull(flow.armedBeat)
    }

    /** The way out belongs to the SECOND miss in a row, not to the first. */
    @Test
    fun theWayOutIsOfferedOnTheSecondMissInARow() {
        val flow = countries(Platform())
        flow.primary()
        assertEquals(TurnFeedback.Revealed, flow.state.feedback)
        assertTrue(!flow.state.offersFinish, "one miss is not yet a run worth leaving")
        flow.confirm()
        flow.primary()
        assertTrue(flow.state.offersFinish)
    }

    @Test
    fun aClosedAtlasRunReportsItsFiguresAndTheSprosseItReached() {
        val untouched = countries(Platform()).close(standingRecord = 0)
        assertNull(untouched.summary)

        val flow = countries(Platform())
        flow.type(flow.state.task.display)
        val closed = flow.close(standingRecord = 0)
        val summary = assertNotNull(closed.summary)
        // The pending clean answer books on the way out, exactly as the tap would.
        assertEquals(1, summary.done)
        assertTrue(summary.newRecord, "a first streak beats a standing record of none")
        assertEquals(flow.state.bestLevel, closed.bestLevel)
    }

    // MARK: - The word scramble

    private fun grownWord(id: String, text: String) = Card(
        id = id,
        kind = CardKind.Noun,
        area = "test",
        emoji = null,
        seedIndex = 0,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = "das $id"),
        target = Realization(lang = "sw", text = text),
        promptFeminineMarker = false,
    )

    private fun scramble(platform: Platform, seed: Int = 11): WordScrambleFlow {
        val report = WordScrambleAvailability.Report(
            listOf("chumba", "kitabu", "mlango", "dirisha", "meza")
                .mapIndexed { index, word ->
                    WordScrambleAvailability.Spelling(grownWord("word$index", word), listOf(word))
                },
        )
        return WordScrambleFlow(
            // A run with no language info grades plainly — enough to drive the wiring.
            start = WordScrambleRun.open(WordScrambleRunConfig(report, normalizer = null), Random(seed)),
            rng = Random(seed),
            clearedKey = TrainerStore.wordScrambleKey("sw"),
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )
    }

    /** Writing the word out IS the answer — the typed drills' rule, on mixed letters. */
    @Test
    fun finishingTheSpellingArmsTheBeatWithoutACheckTap() {
        val platform = Platform()
        val flow = scramble(platform)
        flow.type(assertNotNull(flow.state.task).display)
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceTier.Live, flow.armedBeat)
    }

    @Test
    fun aClosedWordScrambleReportsItsFiguresAndNoRecord() {
        assertNull(scramble(Platform()).close().summary)

        val flow = scramble(Platform())
        flow.type(assertNotNull(flow.state.task).display)
        val summary = assertNotNull(flow.close().summary)
        // The pending clean answer books on the way out, exactly as the tap would.
        assertEquals(1, summary.done)
        // This drill keeps no record store, so nothing it does can beat one.
        assertTrue(!summary.newRecord)
    }

    // MARK: - The sentence scramble

    private fun phrase(id: String, text: String) = SentenceScrambleAvailability.Phrase(
        card = grownWord(id, text).copy(kind = CardKind.Phrase),
        atoms = ScrambleTokenizer.atoms(text),
    )

    private fun sentences(platform: Platform, seed: Int = 3): SentenceScrambleFlow {
        val report = SentenceScrambleAvailability.Report(
            listOf(
                phrase("greet", "habari za asubuhi"),
                phrase("thanks", "asante sana rafiki"),
                phrase("ask", "unaitwa nani leo"),
            ),
        )
        return SentenceScrambleFlow(
            start = SentenceScrambleRun.open(SentenceScrambleRunConfig(report), Random(seed)),
            rng = Random(seed),
            clearedKey = TrainerStore.sentenceScrambleKey("sw"),
            onTone = { platform.tones += it },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )
    }

    /** The LAST word placed is the answer: there is no check tap to send. */
    @Test
    fun committingTheLastAtomGradesTheArrangement() {
        val platform = Platform()
        val flow = sentences(platform)
        val task = assertNotNull(flow.state.task)
        task.canonical.forEach { atom ->
            flow.place(task.shuffled.indexOfFirst { it.id == atom.id })
        }
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceTier.Explicit, flow.armedBeat)
    }

    /** A slip of the finger costs a tap rather than the question. */
    @Test
    fun anAtomGoesBackWhileTheOrderIsStillOwed() {
        val flow = sentences(Platform())
        flow.place(0)
        assertEquals(1, flow.state.placed.size)
        flow.take(0)
        assertTrue(flow.state.placed.isEmpty())
        assertTrue(!flow.state.isPlaced(0), "the chip is back in the bank")
    }
}
