package net.spross.kern.session

import net.spross.kern.model.PresentationRole
import net.spross.kern.model.Rating

/**
 * The write-it-out step of a turn: which misses ask for one, and what happens inside it.
 *
 * A word you MISSED gets typed once with the answer in view before the turn ends — the bit
 * of encoding a reveal followed by a single tap never gives. It is encoding, never a grade:
 * the rating the self-grade already chose is held and applied unchanged (README §6).
 *
 * Split from [TurnMachine] because it is a phase of its own: while it is open it owns every
 * intent, and the rules it runs on (exact-only live approval, a forgiving Enter) are its own.
 */
internal class TurnWriteOut(private val normalizer: AnswerNormalizer) {

    /**
     * Whether this miss should ask for the word to be written out: production, or the first
     * exposure — the two moments where writing the word is more than copying it back off the
     * prompt. A first exposure is the word being TAUGHT, so it is written once as it is met;
     * a LATER recognition miss is the target having stood on screen since the first frame, and
     * transcribing it teaches nothing the reading did not — the next review asks properly,
     * which is production. Only Again ever asks, and never for a word that already sticks.
     */
    fun wanted(state: TurnState, rating: Rating): Boolean =
        rating == Rating.Again &&
            state.card.target.text.isNotEmpty() &&
            (state.role == PresentationRole.Produce || state.firstExposure) &&
            !state.consolidated

    fun reduce(state: TurnState, step: CopyStep, intent: TurnIntent): TurnReduction = when (intent) {
        is TurnIntent.InputChanged -> writing(state, step, intent.text)
        is TurnIntent.CopySubmit -> submitted(state, step, intent.text)
        // why: always reachable — a step you cannot leave is a trap, and the rating is
        // already decided, so leaving costs the schedule nothing.
        TurnIntent.SkipCopy -> applyPending(state, step, emptyList())
        TurnIntent.AdvanceElapsed -> if (step.written) applyPending(state, step, emptyList()) else unchanged(state)
        else -> unchanged(state)
    }

    /**
     * The word finishing IS the action — there is nothing to confirm when the answer is
     * already on screen. Deliberately EXACT, unlike the Enter path: the typo budget would
     * fire a letter early ("lugh" is one edit from "lugha") and snatch the card away mid-word.
     */
    private fun writing(state: TurnState, step: CopyStep, text: String): TurnReduction {
        val trimmed = text.trim()
        val written = trimmed.isNotEmpty() && normalizer.evaluate(trimmed, state.card) == Match.Exact
        if (!written) {
            // why: backing out of a finished word takes the green with it.
            if (!step.written) return unchanged(state)
            return TurnReduction(state.copy(copyStep = step.copy(written = false)), listOf(TurnEffect.CancelAdvance))
        }
        val cue: List<TurnEffect> = if (step.written) emptyList() else listOf(TurnEffect.Tone(ToneKind.Correct))
        return TurnReduction(
            state.copy(copyStep = step.copy(written = true, missed = false)),
            cue + TurnEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    /**
     * Enter forgives a slip where the live path does not — a slipped keystroke must not trap
     * you — while a genuinely different word points back at the card and the step stays open.
     */
    private fun submitted(state: TurnState, step: CopyStep, text: String): TurnReduction {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return unchanged(state)
        if (normalizer.evaluate(trimmed, state.card) == Match.Wrong) {
            return TurnReduction(
                state.copy(copyStep = step.copy(missed = true)),
                listOf(TurnEffect.Tone(ToneKind.Wrong)),
            )
        }
        return applyPending(state, step, listOf(TurnEffect.Tone(ToneKind.Correct)))
    }

    /**
     * The held rating, applied unchanged — and never through the turn's own rating path,
     * which would divert the same Again into a second write-out, forever.
     */
    private fun applyPending(state: TurnState, step: CopyStep, cues: List<TurnEffect>): TurnReduction =
        TurnReduction(state, cues + TurnEffect.Answer(step.pendingRating))

    private fun unchanged(state: TurnState) = TurnReduction(state, emptyList())
}
