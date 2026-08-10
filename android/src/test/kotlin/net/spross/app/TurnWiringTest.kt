package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.SelfGrading
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.TurnMachine

/**
 * What the APP does with kern's turn — which intent each affordance sends, what the screen
 * reads back, and the acts an effect asks for. The rules themselves (what a rating is
 * worth, which miss opens a write-out, the recall clock) belong to `:kern:jvmTest`;
 * nothing here re-tests them.
 *
 * The harness is [TurnFlow] with the platform stripped out: record the tones, the focus
 * releases and the ratings booked, and read the beat off the flow instead of running one.
 */
class TurnWiringTest {

    private val sw = LanguageInfo(code = "sw", name = "Kiswahili", englishName = "Swahili", flag = "🇹🇿")

    private fun card(id: String, source: String, target: String, synonyms: List<String> = emptyList()) = Card(
        id = id,
        kind = CardKind.Noun,
        area = "test",
        emoji = null,
        seedIndex = 0,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = source),
        target = Realization(lang = "sw", text = target, synonyms = synonyms),
        promptFeminineMarker = false,
    )

    private val knife = card("knife", "Messer", "kisu")
    private val language = card("language", "Sprache", "lugha")
    private val car = card("car", "Auto", "gari", synonyms = listOf("motokaa"))

    /** The platform half, recorded: everything [TurnFlow] hands outside the turn. */
    private class Platform {
        val tones = mutableListOf<ToneKind>()
        val booked = mutableListOf<Rating>()
        var focusReleases = 0
        var screenReader = false
    }

    private fun turn(
        card: Card,
        role: PresentationRole = PresentationRole.Produce,
        prompt: ProducePrompt = ProducePrompt.Source,
        consolidated: Boolean = false,
        firstExposure: Boolean = false,
        platform: Platform = Platform(),
    ): Pair<TurnFlow, Platform> {
        val normalizer = AnswerNormalizer(sw)
        val machine = TurnMachine(CatalogAnswerGrader(normalizer, listOf(knife, language, car)), normalizer)
        val start = machine.begin(
            card = card,
            role = role,
            prompt = prompt,
            // The form the prompt stands on, as `newTurn` resolves it.
            promptForm = when {
                role == PresentationRole.Recognize -> card.target.text
                prompt == ProducePrompt.Sound -> card.target.text
                else -> card.source.text
            },
            firstExposure = firstExposure,
            consolidated = consolidated,
            nowEpochMillis = T0,
        )
        var now = T0
        val flow = TurnFlow(
            machine = machine,
            start = start,
            nowMillis = { now += TICK_MS; now },
            onAnswer = { platform.booked += it },
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            screenReaderOn = { platform.screenReader },
        )
        return flow to platform
    }

    /** Typing IS the answer: the field's own text goes in, the beat comes back out. */
    @Test
    fun aFinishedWordArmsTheBeatTheScreenWaitsOut() {
        val (flow, platform) = turn(knife)
        flow.type("kisu")

        assertEquals(TurnFeedback.Correct, flow.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceTier.Live, flow.beat)
        assertTrue(flow.beatToken > 0)

        flow.advanceElapsed()
        assertEquals(listOf(Rating.Good), platform.booked)
        assertNull(flow.beat)
    }

    /** Backing out takes the beat with it, so the timer that fires late books nothing. */
    @Test
    fun backingOutOfAFinishedWordDisarmsTheBeat() {
        val (flow, platform) = turn(knife)
        flow.type("kisu")
        flow.type("kis")

        assertNull(flow.beat)
        flow.advanceElapsed()
        assertTrue(platform.booked.isEmpty())
    }

    /**
     * The primary button is one button with two jobs, and only the platform knows which:
     * an empty field asks to see the answer, a typed one checks it.
     */
    @Test
    fun theOneProduceButtonRevealsWhileTheFieldIsEmptyAndChecksOnceItIsNot() {
        val (blank, blankPlatform) = turn(language)
        blank.primary()
        assertTrue(blank.selfGrading)
        assertEquals(listOf(ToneKind.Reveal), blankPlatform.tones)

        val (typed, _) = turn(language)
        typed.type("neno")
        typed.primary()
        assertEquals(TurnFeedback.Revealed, typed.feedback)
        assertFalse(typed.selfGrading)
    }

    /** A miss primes the FIELD with the words already right — the retype starts there. */
    @Test
    fun aMissLeavesTheFieldOpenAndPrimed() {
        val (flow, platform) = turn(language)
        flow.type("lugha ya")
        flow.primary()

        assertEquals(TurnFeedback.Revealed, flow.feedback)
        assertEquals("lugha ", flow.input)
        assertEquals(listOf(ToneKind.Wrong), platform.tones)
        assertTrue(platform.booked.isEmpty())
    }

    /**
     * Enter carries three meanings, and which one it is depends on the field the platform
     * has mounted: a finished retype skips its beat, an open one gives up, anything else
     * is a check.
     */
    @Test
    fun enterMeansWhicheverFieldIsStanding() {
        val (checking, _) = turn(knife)
        checking.type("kisu")
        checking.enter()
        assertEquals(TurnFeedback.Correct, checking.feedback)

        val (givingUp, upPlatform) = turn(language)
        givingUp.type("neno")
        givingUp.primary()
        givingUp.enter()
        assertEquals(listOf(Rating.Again), upPlatform.booked)

        val (retyping, retryPlatform) = turn(language)
        retyping.type("neno")
        retyping.primary()
        retyping.type("lugha")
        assertTrue(retyping.retryApproved)
        retyping.enter()
        assertEquals(listOf(Rating.Hard), retryPlatform.booked)
    }

    /**
     * Where a screen reader runs no timer may arm — a timed change truncates the
     * announcement and moves the page — so the beat becomes a button, and it has to book
     * exactly what the beat would have.
     */
    @Test
    fun underAScreenReaderTheBeatBecomesATapThatBooksTheSame() {
        val platform = Platform().apply { screenReader = true }
        val (flow, _) = turn(knife, platform = platform)
        flow.type("kisu")

        assertNull(flow.beat)
        assertTrue(flow.awaitsConfirm)
        flow.confirm()
        assertEquals(listOf(Rating.Good), platform.booked)
    }

    /** An amber hold hands the keyboard back: it would cover the button it waits for. */
    @Test
    fun aHoldThatWaitsForATapGivesTheKeyboardBack() {
        val (flow, platform) = turn(car, prompt = ProducePrompt.Sound)
        flow.type("motokaa")
        flow.primary()

        assertEquals(TurnFeedback.Almost("gari", AlmostReason.Heard), flow.feedback)
        assertEquals(1, platform.focusReleases)
        assertNull(flow.beat)
        flow.confirm()
        assertEquals(listOf(Rating.Hard), platform.booked)
    }

    /**
     * The write-out mounts a field of its own: kern names the step, the text in it is
     * ours, so it opens empty and takes its own keystrokes.
     */
    @Test
    fun theWriteOutStepOpensAnEmptyFieldOfItsOwn() {
        val (flow, platform) = turn(language, role = PresentationRole.Recognize, firstExposure = true)
        flow.reveal()
        flow.selfGrade(SelfGrading.Verdict.Unknown)

        val step = flow.copyStep
        assertTrue(step != null && !step.written)
        assertEquals("", flow.copyInput)
        assertTrue(platform.booked.isEmpty(), "the rating is held until the word is written")

        flow.writeCopy("lugha")
        assertEquals("lugha", flow.copyInput)
        assertTrue(flow.copyStep?.written == true)
        flow.advanceElapsed()
        assertEquals(listOf(Rating.Again), platform.booked, "the held rating, applied unchanged")
    }

    /** Leaving the step is always one tap away — a step you cannot leave is a trap. */
    @Test
    fun theWriteOutCanAlwaysBeLeft() {
        val (flow, platform) = turn(language, role = PresentationRole.Recognize, firstExposure = true)
        flow.reveal()
        flow.selfGrade(SelfGrading.Verdict.Unknown)
        flow.skipCopy()
        assertEquals(listOf(Rating.Again), platform.booked)
    }

    /**
     * Which form a produce card says out loud once it stops asking: a slip's proper
     * spelling, and otherwise the bare word — never while it is still asking, and never
     * on a recognition card, whose prompt may be a rotated synonym.
     */
    @Test
    fun theSpokenRevealIsTheFormTheCardOwesBack() {
        val (asking, _) = turn(knife)
        assertNull(asking.spokenReveal)

        val (slipped, _) = turn(knife)
        slipped.type("kisuu")
        slipped.primary()
        assertEquals("kisu", slipped.spokenReveal)

        val (missed, _) = turn(language)
        missed.type("neno")
        missed.primary()
        assertEquals("lugha", missed.spokenReveal)

        val (recognizing, _) = turn(knife, role = PresentationRole.Recognize)
        recognizing.reveal()
        assertNull(recognizing.spokenReveal)
    }

    private companion object {
        const val T0 = 1_700_000_000_000L

        /** Every read of the clock moves it, so a reveal measures a real span. */
        const val TICK_MS = 1_000L
    }
}
