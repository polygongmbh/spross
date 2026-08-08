package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.CopyStep
import net.spross.kern.session.SelfGrading
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnEffect
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.TurnIntent
import net.spross.kern.session.TurnMachine
import net.spross.kern.session.TurnState

/**
 * One review turn as this platform holds it.
 *
 * Every rule is kern's [TurnMachine] — what an answer is worth, how long an accepted one
 * stands, which miss opens a write-out. What is left here is the platform's half: the text
 * standing in whichever field the turn owns, the beat that is armed, and the acts an
 * effect asks for. The screen reads this and hands taps back; it decides nothing.
 *
 * The same shape as [LetterDrillFlow], and for the same reason: a run kept out of the
 * composition is a run a test can drive without a device.
 */
class TurnFlow(
    private val machine: TurnMachine,
    start: TurnState,
    private val nowMillis: () -> Long,
    private val onAnswer: (Rating) -> Unit,
    /** The verdict's cue. What it feels or sounds like is the platform's. */
    private val onTone: (ToneKind) -> Unit = {},
    private val onReleaseFocus: () -> Unit = {},
    /**
     * Whether a screen reader is reading the screen. Where one is, a timed change is
     * hostile — it truncates the announcement and moves the page under the user — so no
     * beat is ever armed and [awaitsConfirm] puts an explicit button in its place.
     */
    private val screenReaderOn: () -> Boolean = { false },
) {

    var state by mutableStateOf(start)
        private set

    /** The learner's answer text. Only ever one field is mounted, so it keeps its own. */
    var input by mutableStateOf("")
        private set

    /** The write-out step's field, which opens empty however the step was reached. */
    var copyInput by mutableStateOf("")
        private set

    /** The beat kern armed and nobody has spent yet; null once it fired or was cancelled. */
    var beat by mutableStateOf<AdvanceTier?>(null)
        private set

    /**
     * Bumped by every arming. What a timer effect keys on: two beats in a row can be the
     * same tier, and a key that compares equal would never fire the second one.
     */
    var beatToken by mutableStateOf(0)
        private set

    /** The beat became a tap: render the explicit "Weiter", which books the same rating. */
    var awaitsConfirm by mutableStateOf(false)
        private set

    val feedback: TurnFeedback get() = state.feedback

    /** The blank-reveal path: nothing was produced and the three verdicts own the turn. */
    val selfGrading: Boolean
        get() = state.revealed && feedback == TurnFeedback.Neutral && state.copyStep == null

    val retryApproved: Boolean get() = state.retryApproved

    val copyStep: CopyStep? get() = state.copyStep

    /** The card carries the answer — a reveal, a miss, or the word being written out. */
    val answerRevealed: Boolean get() = state.answerRevealed

    val almost: TurnFeedback.Almost? get() = feedback as? TurnFeedback.Almost

    val otherWord get() = state.otherWord

    /**
     * The FIELD's own state, which parts ways with the card's on a finished retype: the
     * card holds its reveal open while the field turns right, and the two deliberately
     * say different things at that moment.
     */
    val fieldFeedback: TurnFeedback
        get() = if (retryApproved) TurnFeedback.Correct else feedback

    /**
     * The form a produce card says out loud once it has stopped asking, or null while it
     * is still asking. A slip's proper spelling is what the correction owes back; every
     * other pause owes the bare target word — never the article-carrying citation, which
     * is grammar decoration the audio never speaks.
     *
     * Role-gated: a recognition reveal would say the canonical word after a rotated
     * synonym was prompted, which is the one thing the matched-form lookup prevents.
     */
    val spokenReveal: String?
        get() {
            if (state.role != PresentationRole.Produce) return null
            val hold = almost
            if (!answerRevealed && hold == null) return null
            return hold?.takeIf { it.reason == AlmostReason.Typo }?.correctForm ?: state.card.target.text
        }

    /** A live keystroke in the answer field. */
    fun type(text: String) {
        input = text
        dispatch(TurnIntent.InputChanged(text))
    }

    /** A live keystroke in the write-out field — the word finishing IS the action there. */
    fun writeCopy(text: String) {
        copyInput = text
        dispatch(TurnIntent.InputChanged(text))
    }

    /**
     * The produce card's ONE primary action: an empty field asks to see the answer, a
     * typed one checks it. Kern's Submit is inert on blank text, so which of the two a
     * press is stays the platform's to decide.
     */
    fun primary() {
        if (input.isBlank()) reveal() else dispatch(TurnIntent.Submit(input))
    }

    fun reveal() = dispatch(TurnIntent.Reveal)

    fun selfGrade(verdict: SelfGrading.Verdict) = dispatch(TurnIntent.SelfGrade(verdict))

    /** The tap that stands in for a beat: an amber hold's Weiter, and the a11y one. */
    fun confirm() = dispatch(TurnIntent.ConfirmPending)

    fun giveUp() = dispatch(TurnIntent.GiveUp)

    fun submitCopy() = dispatch(TurnIntent.CopySubmit(copyInput))

    fun skipCopy() = dispatch(TurnIntent.SkipCopy)

    /** The armed beat elapsed; it is spent whether or not the turn had anything to book. */
    fun advanceElapsed() {
        beat = null
        dispatch(TurnIntent.AdvanceElapsed)
    }

    /**
     * Enter in the answer field. WHICH intent it is depends on what that field currently
     * stands for — kern grades each of the three differently, and only the platform knows
     * which one is mounted.
     */
    fun enter() {
        when {
            // The retype already stands: Enter skips the beat, it does not re-grade.
            retryApproved -> confirm()
            // A hardware keyboard still needs a way to give up without finishing it.
            feedback == TurnFeedback.Revealed -> giveUp()
            else -> dispatch(TurnIntent.Submit(input))
        }
    }

    private fun dispatch(intent: TurnIntent) {
        val hadCopyStep = state.copyStep != null
        val reduction = machine.reduce(state, intent, nowMillis())
        state = reduction.state
        // why: the write-out mounts a field of its own — kern names the step, the text in
        // it is ours, so it opens empty however the step was reached.
        if (!hadCopyStep && state.copyStep != null) copyInput = ""
        for (effect in reduction.effects) carryOut(effect)
    }

    private fun carryOut(effect: TurnEffect) {
        when (effect) {
            is TurnEffect.Answer -> {
                cancelBeat()
                onAnswer(effect.rating)
            }
            is TurnEffect.ArmAdvance -> arm(effect.tier)
            TurnEffect.CancelAdvance -> cancelBeat()
            // why: the field the turn owns here is the answer field — a miss never hides
            // it, so the retype picks up where the slip started.
            is TurnEffect.PrimeField -> input = effect.text
            is TurnEffect.Tone -> onTone(effect.kind)
            TurnEffect.ReleaseFocus -> onReleaseFocus()
        }
    }

    private fun arm(tier: AdvanceTier) {
        if (screenReaderOn()) {
            beat = null
            awaitsConfirm = true
            return
        }
        awaitsConfirm = false
        beat = tier
        beatToken += 1
    }

    private fun cancelBeat() {
        beat = null
        awaitsConfirm = false
    }
}

/**
 * The turn a card arrives with, or null before the box and the catalog have landed.
 *
 * The grader snapshots the join as the card goes up, so an answer is graded against the
 * box standing now rather than the one the session opened on.
 */
fun AppModel.newTurn(
    ui: SessionUi,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
): TurnFlow? {
    val card = ui.card ?: return null
    val role = ui.role ?: return null
    val grader = produceGrader ?: return null
    val words = normalizer ?: return null
    val machine = TurnMachine(grader, words)
    return TurnFlow(
        machine = machine,
        start = machine.begin(
            card = card,
            role = role,
            prompt = ui.producePrompt,
            // The form the prompt stands on: the rotated one on recognition, else the
            // source word — or, where the question is the sound, the form that plays.
            promptForm = when {
                role == PresentationRole.Recognize -> ui.promptForm ?: card.target.text
                ui.producePrompt == ProducePrompt.Sound -> card.target.text
                else -> card.source.text
            },
            firstExposure = ui.firstExposure,
            consolidated = ui.consolidated,
            nowEpochMillis = System.currentTimeMillis(),
        ),
        nowMillis = { System.currentTimeMillis() },
        onAnswer = { rating -> answerCurrent(rating) },
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}
