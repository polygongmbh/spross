package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.CountryName
import net.spross.kern.catalog.LanguageName
import net.spross.kern.catalog.NationalityName
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The atlas run: what a typed name earns, which beat it arms, what the ramp does with it,
 * and what a close leaves behind.
 *
 * The ladder, the draw and the task shapes are [CountryDrill]'s and pinned in
 * [CountryDrillTests]; what is asserted here is the run that steps through them — the half
 * both apps used to hold a copy of, which is where the two of them drifted apart.
 *
 * The atlas is one tier-1 country, so rung 1 has exactly one question and every assertion
 * below reads a task it can predict.
 */
class CountryDrillRunTest {

    private val content = CountryDrillContent(
        source = "de",
        target = "sw",
        countries = listOf(
            AtlasCountryEntry(
                slug = "germany",
                flag = "🇩🇪",
                tier = 1,
                languages = listOf("de"),
                source = CountryName(text = "Deutschland", nationality = NationalityName("Deutsche")),
                target = CountryName(text = "Ujerumani", nationality = NationalityName("Wajerumani")),
            ),
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

    private val swahili = LanguageInfo(code = "sw", name = "Kiswahili", englishName = "Swahili", flag = "🇹🇿")

    private val german = LanguageInfo(
        code = "de",
        name = "Deutsch",
        englishName = "German",
        flag = "🇩🇪",
        articles = listOf("der", "die", "das"),
    )

    private fun config(
        reverse: Boolean = false,
        fast: Boolean = false,
        graded: Boolean = true,
    ) = CountryDrillRunConfig(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = when {
            !graded -> null
            reverse -> AnswerNormalizer.drill(german)
            else -> AnswerNormalizer.drill(swahili)
        },
    )

    private fun open(
        reverse: Boolean = false,
        fast: Boolean = false,
        graded: Boolean = true,
        level: Int = 1,
    ) = CountryDrillRun.openAt(config(reverse, fast, graded), level, Random(7))

    private fun CountryDrillRunState.reduce(intent: CountryDrillIntent) =
        CountryDrillRun.reduce(this, intent, Random(7))

    /** The run stepped by one whole answer, the way the platform steps it. */
    private fun CountryDrillRunState.answered(text: String): CountryDrillRunState =
        reduce(CountryDrillIntent.Submit(text)).state.reduce(CountryDrillIntent.ConfirmPending).state

    private fun CountryDrillRunState.missed(): CountryDrillRunState =
        reduce(CountryDrillIntent.Reveal).state.reduce(CountryDrillIntent.ConfirmPending).state

    // MARK: - Where a run opens

    @Test
    fun aRunOpensOnTheFirstRungWithAQuestionStanding() {
        val run = CountryDrillRun.open(config(), Random(7))
        assertEquals(1, run.level)
        assertEquals(1, run.bestLevel)
        assertEquals(CountryTaskKind.CountryName, run.task.kind)
        assertEquals("Ujerumani", run.task.display)
        assertTrue(run.owesAnswer)
        assertFalse(run.showsAnswer)
        assertEquals(0, run.done)
    }

    /** The forced rung is for tests and screenshot drivers; kern clamps it to the ladder. */
    @Test
    fun aForcedRungIsClampedToTheLadder() {
        assertEquals(CountryDrill.MAX_LEVEL, open(level = 99).level)
        assertEquals(1, open(level = 0).level)
    }

    /** Which side prompts and which is owed is the run's, not a screen's. */
    @Test
    fun theDirectionSettlesWhichLanguageIsPromptedAndWhichIsOwed() {
        val forward = open()
        assertEquals("sw", forward.answerLanguage)
        assertEquals("de", forward.promptLanguage)
        assertEquals("Deutschland", forward.task.promptText)
        assertFalse(forward.task.emojiIsGiveaway, "a flag beside a known-language name gives nothing away")

        val reversed = open(reverse = true)
        assertEquals("de", reversed.answerLanguage)
        assertEquals("sw", reversed.promptLanguage)
        assertEquals("Deutschland", reversed.task.display)
        assertTrue(reversed.task.emojiIsGiveaway, "the flag would name the learner's own country")
    }

    // MARK: - What a typed name earns

    /**
     * Writing the name out IS the answer — the review loop's rule, which the letter drill
     * does not offer and this one does. Exact only: a typo budget would fire a letter early
     * and grade the name before it was finished.
     */
    @Test
    fun finishingTheNameArmsTheLiveBeatWithoutACheckTap() {
        val reduction = open().reduce(CountryDrillIntent.InputChanged("Ujerumani"))
        assertEquals(TurnFeedback.Correct, reduction.state.feedback)
        assertEquals(
            listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ArmAdvance(AdvanceTier.Live)),
            reduction.effects,
        )
    }

    /** A slip mid-word is not an answer yet: the live approve never fires on one. */
    @Test
    fun aSlipNeverApprovesLive() {
        val reduction = open().reduce(CountryDrillIntent.InputChanged("Ujerumami"))
        assertEquals(TurnFeedback.Neutral, reduction.state.feedback)
        assertEquals(listOf(DrillEffect.CancelAdvance), reduction.effects)
    }

    /** Typing PAST a finished name takes the green with it, so it is never booked. */
    @Test
    fun backingOutOfAFinishedNameWithdrawsTheApproval() {
        val approved = open().reduce(CountryDrillIntent.InputChanged("Ujerumani")).state
        val reduction = approved.reduce(CountryDrillIntent.InputChanged("Ujerumanix"))
        assertEquals(TurnFeedback.Neutral, reduction.state.feedback)
        assertEquals(listOf(DrillEffect.CancelAdvance), reduction.effects)
    }

    /** The cue sounds once per approval, not once per keystroke inside an approved name. */
    @Test
    fun aKeystrokeInsideAnApprovedNameDoesNotReChime() {
        val approved = open().reduce(CountryDrillIntent.InputChanged("Ujerumani")).state
        val again = approved.reduce(CountryDrillIntent.InputChanged("Ujerumani "))
        assertEquals(TurnFeedback.Correct, again.state.feedback)
        assertEquals(listOf(DrillEffect.ArmAdvance(AdvanceTier.Live)), again.effects)
    }

    @Test
    fun anExplicitCheckOnTheRightNameArmsTheLongerBeat() {
        val reduction = open().reduce(CountryDrillIntent.Submit("Ujerumani"))
        assertEquals(TurnFeedback.Correct, reduction.state.feedback)
        assertEquals(
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ArmAdvance(AdvanceTier.Explicit),
            ),
            reduction.effects,
        )
    }

    /**
     * A slip on an explicit check is the almost hold: it waits for a tap rather than a beat,
     * and gives the keyboard back so the button it waits for is not covered.
     */
    @Test
    fun aSlipOnACheckHoldsAlmostAndGivesTheKeyboardBack() {
        val reduction = open().reduce(CountryDrillIntent.Submit("Ujerumami"))
        val hold = assertIs<TurnFeedback.Almost>(reduction.state.feedback)
        assertEquals("Ujerumani", hold.correctForm)
        assertEquals(AlmostReason.Typo, hold.reason)
        assertEquals(
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ReleaseFocus,
            ),
            reduction.effects,
        )
        assertTrue(reduction.state.showsAnswer, "the slip's proper spelling is worth seeing whole")
    }

    /** A name that is not this one's is a miss, however close the ladder's other rows are. */
    @Test
    fun anotherCountrysNameIsAMissAndNotASlip() {
        val reduction = open().reduce(CountryDrillIntent.Submit("Uhispania"))
        assertEquals(TurnFeedback.Revealed, reduction.state.feedback)
        assertEquals(listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)), reduction.effects)
    }

    /**
     * The STRICT drill grader, on both platforms and in both directions: no article
     * leniency. The atlas authors the article INSIDE the name where a language wants one
     * ("die Schweiz") and the bare form beside it, so an article it did not author is one
     * the learner invented.
     */
    @Test
    fun anArticleTheAtlasDidNotAuthorIsNotForgiven() {
        val reversed = open(reverse = true)
        assertEquals(Match.Exact, CountryDrillRun.grade("Deutschland", reversed.task, reversed.config))
        assertEquals(Match.Wrong, CountryDrillRun.grade("das Deutschland", reversed.task, reversed.config))
    }

    /** No language info (a preview): a plain case- and punctuation-insensitive comparison. */
    @Test
    fun aPreviewWithNoLanguageInfoStillGradesPlainly() {
        val preview = open(graded = false)
        assertEquals(Match.Exact, CountryDrillRun.grade("  ujerumani!  ", preview.task, preview.config))
        assertEquals(Match.Wrong, CountryDrillRun.grade("Ujerumami", preview.task, preview.config))
    }

    /** An empty field reveals, and the card carries the answer rather than the field. */
    @Test
    fun revealingOpensTheCardAndBooksAMiss() {
        val revealed = open().reduce(CountryDrillIntent.Reveal)
        assertEquals(TurnFeedback.Revealed, revealed.state.feedback)
        assertEquals(listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)), revealed.effects)

        val booked = revealed.state.reduce(CountryDrillIntent.ConfirmPending).state
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
        assertEquals(0, booked.streak)
    }

    /** Blank text is not an answer, and a submitted blank must not book one. */
    @Test
    fun aBlankSubmitIsInert() {
        val run = open()
        val reduction = run.reduce(CountryDrillIntent.Submit("   "))
        assertEquals(run, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    @Test
    fun anElapsedBeatBooksOnlyACleanAnswer() {
        val held = open().reduce(CountryDrillIntent.Submit("Ujerumami")).state
        assertEquals(held, held.reduce(CountryDrillIntent.AdvanceElapsed).state)

        val clean = open().reduce(CountryDrillIntent.Submit("Ujerumani")).state
        assertEquals(1, clean.reduce(CountryDrillIntent.AdvanceElapsed).state.done)
    }

    // MARK: - The ramp

    /**
     * Three clean wins a rung, and the question moves on with the booking. Rung 5 has
     * questions to spare, so what carries the ladder here is the wins rather than the rung
     * running out of them.
     */
    @Test
    fun threeCleanWinsCarryTheRun() {
        var run = open(level = 5)
        repeat(CountryDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        assertEquals(6, run.level)
        assertEquals(6, run.bestLevel)
        assertEquals(3, run.done)
        assertEquals(3, run.streak)
        assertEquals(3, run.index)
        assertTrue(run.owesAnswer, "the next question is up, not the last one's verdict")
    }

    /** Fast is one clean win a rung — the price is paid before the run opens. */
    @Test
    fun fastSpendsOneWinARung() {
        val run = open(fast = true, level = 5)
        assertEquals(6, run.answered(run.task.display).level)
    }

    /** The almost hold is accepted, and moves the rung neither way. */
    @Test
    fun theAlmostHoldBanksNoWin() {
        val run = open().answered("Ujerumami")
        assertEquals(1, run.level)
        assertEquals(0, run.winsAtLevel)
        assertEquals(listOf(AnswerOutcome.Almost), run.outcomes)
        assertEquals(1, run.streak, "a slip is still an answer the learner got")
    }

    /** A miss drops the rung, and the rung the run REACHED is what it keeps. */
    @Test
    fun aMissDropsTheRungButNotTheOneTheRunReached() {
        var run = open(level = 5)
        repeat(CountryDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        assertEquals(6, run.level)
        run = run.missed()
        assertEquals(5, run.level)
        assertEquals(6, run.bestLevel)
    }

    // MARK: - Asking each question once

    /**
     * The atlas rung the fixture opens on holds ONE question, so answering it right leaves
     * the rung with nothing: the run climbs past it rather than asking the same country
     * again, and the rung it was carried to is a rung it stood on.
     */
    @Test
    fun aRungWithNothingLeftToAskIsClimbedPast() {
        val run = open().answered("Ujerumani")
        assertEquals(2, run.level)
        assertEquals(2, run.bestLevel)
        assertEquals(0, run.winsAtLevel, "the wins stay behind with the rung that earned them")
        assertFalse(
            run.task.kind == CountryTaskKind.CountryName && run.task.id == "germany",
            "the question just answered came round again",
        )
        assertFalse(run.finished)
    }

    /** A slip and a miss both leave the question in the pool — only a clean answer retires it. */
    @Test
    fun onlyACleanAnswerRetiresAQuestion() {
        val slipped = open().answered("Ujerumami")
        assertEquals(CountryTaskKind.CountryName, slipped.task.kind)
        assertEquals(1, slipped.level, "an almost neither climbs the rung nor empties it")

        val missed = open().missed()
        assertEquals(CountryTaskKind.CountryName, missed.task.kind)
        assertEquals(1, missed.level)
    }

    /**
     * A whole ladder answered out ends the run on its summary rather than asking anything
     * twice — the letter drill's "nothing left to ask", now the atlas rule too.
     */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        var run = open(level = CountryDrill.MAX_LEVEL)
        val asked = mutableSetOf<String>()
        while (!run.finished) {
            asked += "${run.task.kind}:${run.task.id}"
            run = run.answered(run.task.display)
        }
        assertEquals(
            CountryDrill.tasks(content, CountryDrill.MAX_LEVEL, reverse = false).size,
            asked.size,
            "every question was asked exactly once before the run ran out",
        )
        assertEquals(asked.size, run.done)
    }

    /** The way out belongs to the SECOND miss in a row, not to the first. */
    @Test
    fun theWayOutIsOfferedOnTheSecondMissInARow() {
        val first = open().reduce(CountryDrillIntent.Reveal).state
        assertFalse(first.offersFinish, "one miss is not yet a run worth leaving")

        val second = first.reduce(CountryDrillIntent.ConfirmPending).state
            .reduce(CountryDrillIntent.Reveal).state
        assertTrue(second.offersFinish)

        val recovered = second.reduce(CountryDrillIntent.ConfirmPending).state
            .answered("Ujerumani")
            .reduce(CountryDrillIntent.Reveal).state
        assertFalse(recovered.offersFinish, "any correct answer takes the offer away again")
    }

    // MARK: - Leaving

    @Test
    fun anUntouchedRunReportsNothingButStillNamesItsRung() {
        val closed = CountryDrillRun.close(open(), standingRecord = 0)
        assertNull(closed.summary)
        assertEquals(1, closed.bestLevel)
        assertTrue(closed.state.finished)
        assertEquals(listOf(DrillEffect.CancelAdvance, DrillEffect.Silence), closed.effects)
    }

    /** Closing may neither lose a pending answer nor upgrade it. */
    @Test
    fun aPendingAnswerBooksOnTheWayOutExactlyAsTheTapWould() {
        val clean = open().reduce(CountryDrillIntent.Submit("Ujerumani")).state
        val closedClean = CountryDrillRun.close(clean, standingRecord = 0)
        val summary = assertNotNull(closedClean.summary)
        assertEquals(1, summary.done)
        assertEquals(1, summary.bestStreak)
        assertTrue(summary.newRecord, "a first streak beats a standing record of none")

        val held = open().reduce(CountryDrillIntent.Submit("Ujerumami")).state
        val closedHeld = CountryDrillRun.close(held, standingRecord = 0)
        assertEquals(listOf(AnswerOutcome.Almost), closedHeld.state.outcomes)

        val revealed = open().reduce(CountryDrillIntent.Reveal).state
        assertNull(
            CountryDrillRun.close(revealed, standingRecord = 0).summary,
            "a revealed answer nobody confirmed books nothing",
        )
    }

    /** The record write is strictly greater, so a run that only equaled it claims nothing. */
    @Test
    fun aStandingRecordIsOnlyBeatenStrictly() {
        val run = open().answered("Ujerumani")
        assertFalse(assertNotNull(CountryDrillRun.close(run, standingRecord = 1).summary).newRecord)
        assertTrue(assertNotNull(CountryDrillRun.close(run, standingRecord = 0).summary).newRecord)
    }
}
