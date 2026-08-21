package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating

/**
 * The produce/recognize turn as pure state plus one reducer — the machine both apps
 * used to re-derive, each drifting its own way (a pickable Easy here, no retype there).
 *
 * Same shape as [SessionRun]: immutable state, sealed intents, a reduction of state + effects,
 * and `nowEpochMillis` from the caller. Grading needs catalog context, so [grader] and
 * [normalizer] are constructed in; the reduction itself stays pure.
 *
 * What stays with the platform: the text field and its keyboard, focus order, animation,
 * sound PLAYBACK and haptics, and reading the accessibility flags. The RULES those serve —
 * which rating each branch earns, how long an accepted answer stands, that an explicit
 * button replaces a beat where no timer may run — are here.
 */
class TurnMachine(
    private val grader: CatalogAnswerGrader,
    private val normalizer: AnswerNormalizer,
) {

    private val writeOut = TurnWriteOut(normalizer)

    /** A card goes on screen: nothing answered, and the recall attempt starts now. */
    fun begin(
        card: Card,
        role: PresentationRole,
        prompt: ProducePrompt,
        promptForm: String,
        firstExposure: Boolean,
        consolidated: Boolean,
        nowEpochMillis: Long,
    ): TurnState = TurnState(
        card = card,
        role = role,
        prompt = prompt,
        promptForm = promptForm,
        firstExposure = firstExposure,
        consolidated = consolidated,
        feedback = TurnFeedback.Neutral,
        revealed = false,
        pendingRating = null,
        otherWord = null,
        retryApproved = false,
        copyStep = null,
        promptShownAtMillis = nowEpochMillis,
        recallMs = 0,
    )

    fun reduce(state: TurnState, intent: TurnIntent, nowEpochMillis: Long): TurnReduction {
        val copying = state.copyStep
        if (copying != null) return writeOut.reduce(state, copying, intent)
        return when (intent) {
            is TurnIntent.InputChanged -> typed(state, intent.text)
            is TurnIntent.Submit -> submit(state, intent.text)
            TurnIntent.Reveal -> reveal(state, nowEpochMillis)
            is TurnIntent.SelfGrade -> selfGrade(state, intent.verdict)
            TurnIntent.ConfirmPending -> confirmPending(state)
            TurnIntent.AdvanceElapsed -> advanceElapsed(state)
            TurnIntent.GiveUp -> giveUp(state)
            is TurnIntent.CopySubmit, TurnIntent.SkipCopy -> unchanged(state)
        }
    }

    // MARK: - Typing

    private fun typed(state: TurnState, text: String): TurnReduction = when {
        // Recognition is a comprehension check and is never typed (README §3), so no schedule
        // is ever graded against a language it was not learned with. The write-out is the one
        // field it can carry, and that is reduced before this.
        state.role == PresentationRole.Recognize -> unchanged(state)
        // The blank reveal handed the turn to the self-grade buttons; there is no field left.
        state.revealed -> unchanged(state)
        state.feedback == TurnFeedback.Revealed -> approveRetry(state, text)
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
        if (!isExact(state, text)) {
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
            cue + TurnEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    /**
     * Finishing the retype after a miss IS the self-grade: reaching the exact answer with the
     * reveal in view is recalled-with-help, so it earns [RETRY_RATING] rather than the blind
     * Again a bare give-up would. The card keeps its reveal while the field turns right —
     * the two deliberately say different things at that moment.
     */
    private fun approveRetry(state: TurnState, text: String): TurnReduction {
        if (!isExact(state, text)) {
            // why: backing out takes the parked rating with it, or it would fire on a word
            // that no longer stands written.
            if (!state.retryApproved) return unchanged(state)
            return TurnReduction(state.copy(retryApproved = false), listOf(TurnEffect.CancelAdvance))
        }
        if (state.retryApproved) return unchanged(state)
        return TurnReduction(
            state.copy(retryApproved = true),
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Live)),
        )
    }

    // MARK: - Submitting

    /**
     * An explicit Check/Enter, graded once. Inert unless the turn is still open and typed at
     * all: recognition is never graded from text, a doubled Enter during the beat that follows
     * an answer books nothing extra, and a turn already handed to the self-grade buttons is not
     * re-graded.
     */
    private fun submit(state: TurnState, text: String): TurnReduction {
        val trimmed = text.trim()
        if (state.role == PresentationRole.Recognize || state.revealed ||
            state.feedback != TurnFeedback.Neutral || trimmed.isEmpty()
        ) {
            return unchanged(state)
        }
        val graded = grader.grade(trimmed, gradingCard(state))
        return when {
            // why: the form that PLAYED wins over the heard rule below — a card listing its own
            // spoken text among its forms was still answered exactly, not merely nearby.
            graded == Match.Exact -> accepted(state)
            // why: only where the card was asked by ear. The narrowed answer set grades a
            // synonym Wrong, yet the reveal teaches those very forms — it simply was not what
            // played, which is Hard, never a miss. A literal Hard on purpose: no Match decided it.
            state.prompt == ProducePrompt.Sound && alsoAccepts(state.card, trimmed) ->
                holding(state, state.card.target.text, AlmostReason.Heard, Rating.Hard)
            graded is Match.Typo -> holding(state, graded.corrected, AlmostReason.Typo, graded.producedRating())
            graded is Match.OtherWord -> missed(state, text, graded)
            else -> missed(state, text, null)
        }
    }

    private fun accepted(state: TurnState): TurnReduction = TurnReduction(
        state.copy(feedback = TurnFeedback.Correct),
        listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Explicit)),
    )

    /** An accepted-but-not-clean answer pauses on what it owes back; no beat may take it away. */
    private fun holding(
        state: TurnState,
        correctForm: String,
        reason: AlmostReason,
        rating: Rating?,
    ): TurnReduction = TurnReduction(
        state.copy(feedback = TurnFeedback.Almost(correctForm, reason), pendingRating = rating),
        listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ReleaseFocus),
    )

    /** A miss reveals the answer and keeps the field open — the retype is the answer. */
    private fun missed(state: TurnState, text: String, other: Match.OtherWord?): TurnReduction = TurnReduction(
        state.copy(feedback = TurnFeedback.Revealed, otherWord = other),
        listOf(TurnEffect.Tone(ToneKind.Wrong), TurnEffect.PrimeField(primed(state, text))),
    )

    /**
     * Keep the leading WHOLE words that were already right and drop the wrong tail, so the
     * retype picks up where the slip started instead of from scratch. Nothing kept clears it.
     */
    private fun primed(state: TurnState, text: String): String {
        val count = normalizer.matchingPrefixWordCount(text, state.card.target.text)
        val kept = text.trim().split(WHITESPACE_RUN).filter { it.isNotEmpty() }
            .take(count)
            .joinToString(" ")
        return if (kept.isEmpty()) "" else "$kept "
    }

    private fun isExact(state: TurnState, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return grader.grade(trimmed, gradingCard(state)) == Match.Exact
    }

    /**
     * What the answer is graded AGAINST: a card asked by ear accepts only the form that played
     * ([spokenOnly]), because crediting a synonym would credit a word never heard.
     */
    private fun gradingCard(state: TurnState): Card =
        if (state.prompt == ProducePrompt.Sound) spokenOnly(state.card, state.card.target.text) else state.card

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

        val WHITESPACE_RUN = Regex("\\s+")
    }
}
