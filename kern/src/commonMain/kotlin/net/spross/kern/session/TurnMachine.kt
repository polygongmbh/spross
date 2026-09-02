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
        promptInText = false,
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
        val graded = grade(state, trimmed)
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
        val count = answerNormalizer(state).matchingPrefixWordCount(text, state.answerText)
        val kept = text.trim().split(WHITESPACE_RUN).filter { it.isNotEmpty() }
            .take(count)
            .joinToString(" ")
        return if (kept.isEmpty()) "" else "$kept "
    }

    /**
     * Live green is THIS card's answer and no other: a borrowed meaning is right, but it
     * holds on the word the card teaches ([submit]), and a beat armed here would carry the
     * turn away before that word was ever seen.
     */
    private fun isExact(state: TurnState, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val graded = grade(state, trimmed)
        return graded.match == Match.Exact && !graded.merged
    }

    /**
     * The verdict [text] earns, in the language THIS turn asks its answer in.
     *
     * A card asked by ear owes the MEANING ([meaningSide]) — writing back the word that
     * played proves the ear worked and nothing else — so it is graded by the source
     * language's normalizer, whose articles and typo budget are the ones the learner is
     * writing under. Every other turn owes the target word, with the whole join in view.
     *
     * The meaning side reads the join too, from the other end: the form that played may be
     * printed by more than one concept, because the target language merges what the source
     * splits (sw `kuacha` is verlassen AND aufhören), and every meaning it carries is a
     * right answer to what it means. The prompted card leads — its own verdict wins where
     * it has one — and a borrowed one comes back [Graded.merged], for [submit] to hold on.
     */
    private fun grade(state: TurnState, text: String): Graded {
        if (state.prompt != ProducePrompt.Sound) return Graded(grader.grade(text, state.card))
        val own = meaningNormalizer.evaluate(text, meaningSide(state.card))
        if (own == Match.Exact) return Graded(own)
        val shared = grader.conceptsSharing(state.promptForm, state.card)
            .map { meaningNormalizer.evaluate(text, meaningSide(it)) }
        shared.firstOrNull { it == Match.Exact }?.let { return Graded(it, merged = true) }
        // A slip is forgiven against the word it was aiming at, and the prompted card owns
        // its own slips first — the same order the normalizer keeps a card's text ahead of
        // its variants in.
        if (own != Match.Wrong) return Graded(own)
        shared.firstOrNull { it is Match.Typo }?.let { return Graded(it, merged = true) }
        return Graded(own)
    }

    /** A verdict, plus whether it was earned on a meaning this card does not itself teach. */
    private data class Graded(val match: Match, val merged: Boolean = false)

    private fun answerNormalizer(state: TurnState): AnswerNormalizer =
        if (state.prompt == ProducePrompt.Sound) meaningNormalizer else normalizer

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

        val WHITESPACE_RUN = Regex("\\s+")
    }
}
