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
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.CountryDrillRun
import net.spross.kern.trainer.CountryDrillRunConfig
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.LetterDrillRun
import net.spross.kern.trainer.LetterDrillRunConfig
import net.spross.kern.trainer.NumbersExercise
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.NumbersRun

/**
 * What the APP does with kern's drill runs — which intent each affordance sends, which acts
 * an effect asks for, and what the screen reads back. The rules themselves (the ramp, the
 * draw, the verdict ladder, when the way out is offered) belong to `:kern:jvmTest`; nothing
 * here re-tests them.
 *
 * The harness is the flows with the platform stripped out ([DrillPlatform]). The dates run
 * is [DateDrillWiringTest]'s, the two scrambles [ScrambleWiringTest]'s.
 */
class DrillWiringTest {

    // MARK: - The slot run

    private fun slots(platform: DrillPlatform, seed: Int = 7): NumbersFlow = NumbersFlow(
        start = NumbersRun.open(NumbersMode(NumbersExercise.Counting, "de"), 0, emptyMap(), Random(seed)),
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
        val platform = DrillPlatform()
        val flow = slots(platform)
        flow.type(flow.state.currentTask.accepted.first())
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceBeat.Live, flow.armedBeat)
    }

    /** A timed screen change under a screen reader truncates what it just announced. */
    @Test
    fun aScreenReaderIsOfferedATapInsteadOfATimedChange() {
        val platform = DrillPlatform().apply { screenReader = true }
        val flow = slots(platform)
        flow.type(flow.state.currentTask.accepted.first())
        assertNull(flow.armedBeat)
        assertTrue(flow.awaitsConfirm)
    }

    /** The next prompt must never render one frame carrying the last one's answer. */
    @Test
    fun theFieldClearsWithTheQuestion() {
        val platform = DrillPlatform()
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
        val platform = DrillPlatform()
        val flow = slots(platform)
        flow.type(flow.state.currentTask.accepted.first())
        val before = platform.silences
        flow.confirm()
        assertTrue(platform.silences > before, "the question that ended cut its own reading")
    }

    /** The "?" raises the table AND books the amber debt while the answer is still owed. */
    @Test
    fun lookingUpWhileTheAnswerIsOwedRaisesTheTableAndCostsTheSprosse() {
        val flow = slots(DrillPlatform())
        flow.lookUp()
        assertTrue(flow.showingReference)
        assertTrue(flow.state.hintUsed)
    }

    @Test
    fun closingAnUntouchedRunReportsNothingAndStoresNothing() {
        val flow = slots(DrillPlatform())
        val closed = flow.close(standingRecord = 0, standingProgress = emptyMap())
        assertNull(closed.summary)
        assertTrue(closed.progressBookings.isEmpty())

        val answered = slots(DrillPlatform())
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

    private fun letters(platform: DrillPlatform, seed: Int = 42, sprosse: Int = 1): LetterDrillFlow {
        val report = LetterDrillAvailability.Report(
            language = "uk",
            alphabet = alphabet,
            promptableRefs = alphabet.entries.map { it.ref },
            dictationCandidates = emptyList(),
            gapWords = emptyMap(),
            arrivedCards = 0,
        )
        val config = LetterDrillRunConfig(report, cards = emptyMap(), dictationGrader = null, cleared = emptySet())
        return LetterDrillFlow(
            start = LetterDrillRun.openAt(config, sprosse, Random(seed)),
            rng = Random(seed),
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )
    }

    @Test
    fun theRightTileArmsTheBeatAndTheWrongOneWaitsForATap() {
        val platform = DrillPlatform()
        val hit = letters(platform)
        hit.choose(assertNotNull(hit.state.task).display)
        assertEquals(AdvanceBeat.Explicit, hit.armedBeat)
        assertTrue(ToneKind.Correct in platform.tones)

        val missed = letters(DrillPlatform())
        val task = assertNotNull(missed.state.task)
        missed.choose(task.choices.orEmpty().first { it != task.display })
        assertNull(missed.armedBeat)
        assertEquals(TurnFeedback.Revealed, missed.state.feedback)
    }

    /** Typing the letter out IS the answer — no Check tap, the live beat armed. */
    @Test
    fun typingTheLetterArmsTheLiveBeatWithoutACheckTap() {
        val platform = DrillPlatform()
        val flow = letters(platform, sprosse = 6)
        flow.type(assertNotNull(flow.state.task).display)
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceBeat.Live, flow.armedBeat)
    }

    @Test
    fun aClosedLetterRunReportsItsFiguresAndNoRecord() {
        val flow = letters(DrillPlatform())
        flow.choose(assertNotNull(flow.state.task).display)
        val closed = flow.close()
        val summary = assertNotNull(closed.summary)
        assertEquals(1, summary.done)
        // The letter drill keeps no record store, so nothing it does can beat one.
        assertTrue(!summary.newRecord)
    }

    // MARK: - The atlas run

    private fun place(slug: String, flag: String, spoken: List<String>, known: String, learned: String) =
        AtlasCountryEntry(
            slug = slug,
            flag = flag,
            tier = 1,
            languages = spoken,
            source = CountryName(text = known, nationality = NationalityName("${known}er")),
            target = CountryName(text = learned, nationality = NationalityName("Wa$learned")),
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

    private fun countries(platform: DrillPlatform, reverse: Boolean = false, seed: Int = 5) =
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

    /** Writing the name out IS the answer — the review loop's rule, and every typed drill's. */
    @Test
    fun finishingTheNameArmsTheLiveBeatWithoutACheckTap() {
        val platform = DrillPlatform()
        val flow = countries(platform)
        flow.type(flow.state.task.display)
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceBeat.Live, flow.armedBeat)
    }

    /** Typing PAST a finished name takes the green with it, so it is never booked. */
    @Test
    fun backingOutOfAFinishedNameDropsTheBeat() {
        val flow = countries(DrillPlatform())
        flow.type(flow.state.task.display)
        flow.type(flow.state.task.display + "x")
        assertEquals(TurnFeedback.Neutral, flow.state.feedback)
        assertNull(flow.armedBeat)
    }

    @Test
    fun aClosedAtlasRunReportsItsFiguresAndTheSprosseItReached() {
        val untouched = countries(DrillPlatform()).close(standingRecord = 0)
        assertNull(untouched.summary)

        val flow = countries(DrillPlatform())
        flow.type(flow.state.task.display)
        val closed = flow.close(standingRecord = 0)
        val summary = assertNotNull(closed.summary)
        // The pending clean answer books on the way out, exactly as the tap would.
        assertEquals(1, summary.done)
        assertTrue(summary.newRecord, "a first streak beats a standing record of none")
        assertEquals(flow.state.bestSprosse, closed.bestSprosse)
    }
}
