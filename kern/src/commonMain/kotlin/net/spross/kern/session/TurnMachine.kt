package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProduceAnswer
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.PromptForm
import net.spross.kern.model.Rating

/**
 * The produce/recognize turn as pure state plus one reducer — the machine both apps
 * used to re-derive, each drifting its own way (a pickable Easy here, no retype there).
 *
 * Same shape as [SessionRun]: immutable state, sealed intents, a reduction of state + effects,
 * and `nowEpochMillis` from the caller. Grading needs catalog context, so [grader] and
 * [normalizer] are constructed in; the reduction itself stays pure.
 *
 * TWO answer languages reach it, because a card asked by ear asks what the word MEANS:
 * [grader]/[normalizer] grade the target word with the whole join in view, and
 * [meaningNormalizer] — the SOURCE language's own, articles and all — grades the meaning.
 * The join serves both, answering a different question on each side: which concept a typed
 * TARGET word belongs to, and which meanings a prompted target form is printed with. It
 * still never NAMES the other concept on the meaning side — that would teach a word in the
 * language being learned, where the source side is the one the learner already has.
 *
 * What stays with the platform: the text field and its keyboard, focus order, animation,
 * sound PLAYBACK and haptics, and reading the accessibility flags. The RULES those serve —
 * which rating each branch earns, how long an accepted answer stands, that an explicit
 * button replaces a beat where no timer may run — are here.
 */
class TurnMachine(
    private val grader: CatalogAnswerGrader,
    private val normalizer: AnswerNormalizer,
    private val meaningNormalizer: AnswerNormalizer,
) {

    private val writeOut = TurnWriteOut(normalizer)

    private val grading = TurnGrading(grader, normalizer, meaningNormalizer)

    /** A card goes on screen: nothing answered, and the recall attempt starts now. */
    fun begin(
        card: Card,
        role: PresentationRole,
        prompt: ProducePrompt,
        promptForm: PromptForm,
        firstExposure: Boolean,
        arrived: Boolean,
        nowEpochMillis: Long,
        answer: ProduceAnswer = ProduceAnswer.Typed,
    ): TurnState = TurnState(
        card = card,
        role = role,
        prompt = prompt,
        answer = answer,
        promptForm = promptForm.text,
        promptTag = promptForm.tag,
        firstExposure = firstExposure,
        arrived = arrived,
        feedback = TurnFeedback.Neutral,
        revealed = false,
        pendingRating = null,
        otherWord = null,
        alsoMeans = grading.alsoMeans(card, role, prompt, promptForm.text),
        retryApproved = false,
        copyStep = null,
        promptInText = false,
        promptShownAtMillis = nowEpochMillis,
        recallMs = 0,
    )

    fun reduce(state: TurnState, intent: TurnIntent, nowEpochMillis: Long): TurnReduction {
        val copying = state.copyStep
        if (copying != null) return writeOut.reduce(state, copying, intent)
        return when (intent) {
            is TurnIntent.InputChanged -> typed(state, intent.text)
            is TurnIntent.Submit -> submit(state, intent.text, nowEpochMillis)
            TurnIntent.Reveal -> reveal(state, nowEpochMillis)
            is TurnIntent.SelfGrade -> selfGrade(state, intent.verdict)
            TurnIntent.ConfirmPending -> confirmPending(state)
            TurnIntent.AdvanceElapsed -> advanceElapsed(state)
            TurnIntent.GiveUp -> giveUp(state)
            TurnIntent.ShowPromptText -> showPromptText(state)
            is TurnIntent.CopySubmit, TurnIntent.SkipCopy -> unchanged(state)
        }
    }

    // MARK: - Typing

    private fun typed(state: TurnState, text: String): TurnReduction = when {
        // Recognition is a comprehension check and is never typed (README §3), so no schedule
        // is ever graded against a language it was not learned with. The write-out is the one
        // field it can carry, and that is reduced before this.
        state.role == PresentationRole.Recognize -> unchanged(state)
        state.answer == ProduceAnswer.Recalled -> unchanged(state)
        // The blank reveal handed the turn to the self-grade buttons; there is no field left.
        state.revealed -> unchanged(state)
        state.retypes -> approveRetry(state, text)
        // A miss by ear has no field: the reveal is the whole of it.
        state.feedback == TurnFeedback.Revealed -> unchanged(state)
        // An almost hold waits for its tap: the correction is the point of the pause.
        state.feedback is TurnFeedback.Almost -> unchanged(state)
        else -> approveTyped(state, text)
    }

    /**
     * Writing the word out exactly IS the answer, with no Check tap — so a word you know
     * never asks for a confirming one.
     *
     * EXACT only, where an explicit submit still forgives a typo: the typo budget would fire
     * a letter early and grade the word before it was finished, and a real slip has to pause
     * on its correction anyway.
     */
    private fun approveTyped(state: TurnState, text: String): TurnReduction {
        if (!grading.isExact(state, text)) {
            // why: backing out of a finished word takes the green with it, so typing past
            // the answer never books it.
            if (state.feedback != TurnFeedback.Correct) return unchanged(state)
            return TurnReduction(
                state.copy(feedback = TurnFeedback.Neutral),
                listOf(TurnEffect.CancelAdvance),
            )
        }
        val cue: List<TurnEffect> =
            if (state.feedback == TurnFeedback.Correct) emptyList() else listOf(TurnEffect.Tone(ToneKind.Correct))
        return TurnReduction(
            state.copy(feedback = TurnFeedback.Correct),
            cue + TurnEffect.ArmAdvance(AdvanceBeat.Live),
        )
    }

    /**
     * Finishing the retype after a miss IS the self-grade: reaching the exact answer with the
     * reveal in view is recalled-with-help, so it earns [RETRY_RATING] rather than the blind
     * Again a bare give-up would. The card keeps its reveal while the field turns right —
     * the two deliberately say different things at that moment.
     */
    private fun approveRetry(state: TurnState, text: String): TurnReduction {
        if (!grading.isExact(state, text)) {
            // why: backing out takes the parked rating with it, or it would fire on a word
            // that no longer stands written.
            if (!state.retryApproved) return unchanged(state)
            return TurnReduction(state.copy(retryApproved = false), listOf(TurnEffect.CancelAdvance))
        }
        if (state.retryApproved) return unchanged(state)
        return TurnReduction(
            state.copy(retryApproved = true),
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceBeat.Live)),
        )
    }

    // MARK: - Submitting

    /**
     * An explicit Check/Enter, graded once. A submit with nothing typed IS the ask to see the
     * answer ([reveal]) — the surface has ONE primary action, and its two triggers may not
     * disagree on what a press means. Inert unless the turn is still open: recognition is
     * never typed, a doubled Enter during the beat that follows an answer books nothing
     * extra, and a turn already handed to the self-grade buttons neither re-grades nor
     * re-reveals.
     */
    private fun submit(state: TurnState, text: String, nowEpochMillis: Long): TurnReduction {
        val trimmed = text.trim()
        if (state.role == PresentationRole.Recognize || state.revealed ||
            state.feedback != TurnFeedback.Neutral
        ) {
            return unchanged(state)
        }
        // A recalled turn has no field, so its one action can only ever ask for the answer.
        if (trimmed.isEmpty() || state.answer == ProduceAnswer.Recalled) return reveal(state, nowEpochMillis)
        val graded = grading.grade(state, trimmed)
        // why: a meaning borrowed from the concept next door is right and books as much, but
        // the word this card teaches has still not been said — so it holds on it (§3).
        if (graded.merged) {
            return holding(state, state.card.source.text, AlmostReason.Merged, graded.match.producedRating())
        }
        return when (val verdict = graded.match) {
            Match.Exact -> accepted(state)
            is Match.Typo -> holding(state, verdict.corrected, AlmostReason.Typo, verdict.producedRating())
            is Match.OtherWord -> missed(state, text, verdict)
            Match.Wrong -> missed(state, text, null)
        }
    }

    private fun accepted(state: TurnState): TurnReduction = TurnReduction(
        state.copy(feedback = TurnFeedback.Correct),
        listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceBeat.Explicit)),
    )

    /**
     * An accepted-but-not-clean answer pauses on what it owes back; no beat may take it away.
     * A slip sounds almost, a merged meaning (full credit) sounds correct.
     */
    private fun holding(
        state: TurnState,
        correctForm: String,
        reason: AlmostReason,
        rating: Rating?,
    ): TurnReduction = TurnReduction(
        state.copy(feedback = TurnFeedback.Almost(correctForm, reason), pendingRating = rating),
        listOf(
            TurnEffect.Tone(if (reason == AlmostReason.Typo) ToneKind.Almost else ToneKind.Correct),
            TurnEffect.ReleaseFocus,
        ),
    )

    /**
     * A miss reveals the answer and keeps the field open — the retype is the answer.
     * The refused word is kept whole ([TurnState.rejectedAnswer]): priming the field drops
     * it from the only place it stood, and it is what a report about this card is made of.
     */
    private fun missed(state: TurnState, text: String, other: Match.OtherWord?): TurnReduction = TurnReduction(
        state.copy(feedback = TurnFeedback.Revealed, otherWord = other, rejectedAnswer = text.trim()),
        listOf(TurnEffect.Tone(ToneKind.Wrong), TurnEffect.PrimeField(grading.primed(state, text))),
    )

    /**
     * The word goes on screen because the learner cannot listen: same question, same answer,
     * same rating — only the channel it arrives through moves, so nothing here touches the
     * grade. Idempotent, and inert on a card that was never asked by ear.
     */
    private fun showPromptText(state: TurnState): TurnReduction {
        if (state.prompt != ProducePrompt.Sound || state.promptInText) return unchanged(state)
        return TurnReduction(state.copy(promptInText = true), emptyList())
    }

    // MARK: - Reveal and self-grade

    /**
     * The recall attempt is prompt-on-screen until the learner asks to see the answer;
     * choosing a button afterwards is thumb travel and stays out of it. Asked once —
     * a second ask neither restarts nor re-closes the span, and a typed answer never
     * reaches it at all.
     */
    private fun reveal(state: TurnState, nowEpochMillis: Long): TurnReduction {
        if (state.revealed || state.feedback != TurnFeedback.Neutral) return unchanged(state)
        return TurnReduction(
            state.copy(revealed = true, recallMs = nowEpochMillis - state.promptShownAtMillis),
            listOf(TurnEffect.Tone(ToneKind.Reveal)),
        )
    }

    /** The three buttons only ever stand where nothing was produced. */
    private fun selfGrade(state: TurnState, verdict: SelfGrading.Verdict): TurnReduction {
        if (!state.revealed || state.feedback != TurnFeedback.Neutral) return unchanged(state)
        return rate(state, SelfGrading.rating(verdict, state.recallMs, state.promptChars))
    }

    // MARK: - Leaving the turn

    /**
     * The explicit tap that stands in for a beat books exactly what the beat would have —
     * an almost hold's parked rating, and otherwise whatever [advanceElapsed] was waiting to fire.
     * One rule, so the button a screen reader gets cannot grade differently from the timer.
     */
    private fun confirmPending(state: TurnState): TurnReduction {
        if (state.feedback is TurnFeedback.Almost) return rate(state, state.pendingRating ?: Rating.Hard)
        return advanceElapsed(state)
    }

    /** Only the state that armed the beat may book on it; a stray one books nothing. */
    private fun advanceElapsed(state: TurnState): TurnReduction = when {
        state.feedback == TurnFeedback.Correct -> rate(state, Match.Exact.producedRating() ?: Rating.Good)
        state.feedback == TurnFeedback.Revealed && state.retryApproved -> rate(state, RETRY_RATING)
        else -> unchanged(state)
    }

    /**
     * Giving up on an open retry is an honest Again, booked DIRECT: that field already was the
     * one write-out the word gets, so nothing may hand it a second one.
     */
    private fun giveUp(state: TurnState): TurnReduction {
        if (state.feedback != TurnFeedback.Revealed) return unchanged(state)
        return TurnReduction(state, listOf(TurnEffect.Answer(Rating.Again)))
    }

    /**
     * A rating leaves the turn — unless the word owes a write-out first
     * ([TurnWriteOut.wanted]), where it is HELD unchanged until the copy is done.
     */
    private fun rate(state: TurnState, rating: Rating): TurnReduction {
        if (state.copyStep == null && writeOut.wanted(state, rating)) {
            return TurnReduction(
                state.copy(
                    copyStep = CopyStep(pendingRating = rating, missed = false, written = false),
                    revealed = true,
                ),
                emptyList(),
            )
        }
        return TurnReduction(state, listOf(TurnEffect.Answer(rating)))
    }

    private fun unchanged(state: TurnState) = TurnReduction(state, emptyList())

    private companion object {
        /** Reaching the exact answer with the reveal in view is recalled-with-help. */
        val RETRY_RATING = Rating.Hard
    }
}
