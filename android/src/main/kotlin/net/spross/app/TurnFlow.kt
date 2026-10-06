package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.model.Rating
import net.spross.app.ui.AnswerActions
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.CopyStep
import net.spross.kern.session.Question
import net.spross.kern.session.Reading
import net.spross.kern.session.SelfGrading
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnEffect
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.TurnIntent
import net.spross.kern.session.TurnMachine
import net.spross.kern.session.TurnState
import net.spross.kern.session.controls
import net.spross.kern.session.question
import net.spross.kern.session.reading

/**
 * One review turn as this platform holds it.
 *
 * Every rule is kern's [TurnMachine] — what an answer is worth, how long an accepted one
 * stands, which miss opens a write-out. What is left here is the platform's half: the text
 * standing in whichever field the turn owns, the beat that is armed, and the acts an
 * effect asks for. The screen reads this and hands taps back; it decides nothing.
 *
 * The same shape as [DrillFlow], and for the same reason: a run kept out of the
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
    /** Whether a screen reader is reading the screen ([DrillBeat]). */
    screenReaderOn: () -> Boolean = { false },
    /** Whether the meaning may be read aloud too ([TurnState.reading]). */
    private val saysMeaning: () -> Boolean = { false },
) : QuestionDriver {

    var state by mutableStateOf(start)
        private set

    /** The learner's answer text. Only ever one field is mounted, so it keeps its own. */
    var input by mutableStateOf("")
        private set

    /**
     * What a report filed from this turn carries as the learner's answer — kern's call
     * ([TurnState.answerForReport]): the word the catalog refused, which a miss has already
     * primed out of [input], else whatever stands in it.
     */
    val answerForReport: String get() = state.answerForReport(input)

    /** The write-out step's field, which opens empty however the step was reached. */
    var copyInput by mutableStateOf("")
        private set

    private val beat = DrillBeat(screenReaderOn)

    override val armedBeat: AdvanceBeat? get() = beat.armed

    override val beatToken: Int get() = beat.token

    override val awaitsConfirm: Boolean get() = beat.awaitsConfirm

    override val question: Question get() = state.question

    override val controls: AnswerControls get() = state.controls

    override val reading: Reading get() = state.reading(saysMeaning())

    /** Only ever one field is mounted: the write-out's while it stands, else the answer's. */
    override val fieldText: String get() = if (copyStep != null) copyInput else input

    val feedback: TurnFeedback get() = state.feedback

    val retryApproved: Boolean get() = state.retryApproved

    val copyStep: CopyStep? get() = state.copyStep

    /** The card carries the answer — a reveal, a miss, or the word being written out. */
    val answerRevealed: Boolean get() = state.answerRevealed

    /**
     * The word the card owes back is on screen and the turn waits on the learner — kern's
     * own fact (`TurnState.answerOut`). What the card's long press hangs on: a typo holds
     * on its correction without ever expanding the card, and that pause is exactly when a
     * learner has something to say about the word.
     */
    val answerOut: Boolean get() = state.answerOut

    /** The word was heard and could not be: it goes on screen for the rest of this turn. */
    val promptInText: Boolean get() = state.promptInText

    fun showPromptText() = dispatch(TurnIntent.ShowPromptText)

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
     * The produce card's ONE primary action, button and Enter alike: kern checks what stands
     * in the field, and reveals the answer when nothing does.
     */
    fun primary() = dispatch(TurnIntent.Submit(input))

    fun reveal() = dispatch(TurnIntent.Reveal)

    fun selfGrade(verdict: SelfGrading.Verdict) = dispatch(TurnIntent.SelfGrade(verdict))

    /** The tap that stands in for a beat: an amber hold's Weiter, and the a11y one. */
    fun confirm() = dispatch(TurnIntent.ConfirmPending)

    fun giveUp() = dispatch(TurnIntent.GiveUp)

    fun submitCopy() = dispatch(TurnIntent.CopySubmit(copyInput))

    fun skipCopy() = dispatch(TurnIntent.SkipCopy)

    override fun advanceElapsed() {
        beat.spend()
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
            else -> primary()
        }
    }

    override fun answerActions(stop: () -> Unit): AnswerActions {
        val writing = copyStep != null
        return AnswerActions(
            submit = { if (writing) submitCopy() else enter() },
            type = { if (writing) writeCopy(it) else type(it) },
            reveal = ::reveal,
            confirm = ::confirm,
            // why: giving up on a retype ends the card — that field already is the one
            // write-out the word gets, so nothing hands it a second.
            giveUp = { if (writing) skipCopy() else giveUp() },
            selfGrade = ::selfGrade,
            cantListen = ::showPromptText,
        )
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
                beat.cancel()
                onAnswer(effect.rating)
            }
            is TurnEffect.ArmAdvance -> beat.arm(effect.beat)
            TurnEffect.CancelAdvance -> beat.cancel()
            // why: the field the turn owns here is the answer field — a miss never hides
            // it, so the retype picks up where the slip started.
            is TurnEffect.PrimeField -> input = effect.text
            is TurnEffect.Tone -> onTone(effect.kind)
            TurnEffect.ReleaseFocus -> onReleaseFocus()
        }
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
    // The card asked by ear is typed in the SOURCE language, so kern grades it with that
    // language's own rules rather than the target's (`kern/docs/presentation.md`).
    val meanings = meaningNormalizer ?: return null
    val machine = TurnMachine(grader, words, meanings)
    return TurnFlow(
        machine = machine,
        start = machine.begin(
            card = card,
            role = role,
            prompt = ui.producePrompt,
            promptForm = ui.promptForm ?: return null,
            firstExposure = ui.firstExposure,
            arrived = ui.arrived,
            nowEpochMillis = System.currentTimeMillis(),
            answer = ui.produceAnswer,
        ),
        nowMillis = { System.currentTimeMillis() },
        onAnswer = { rating -> answerCurrent(rating) },
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        screenReaderOn = { pronouncer.readsScreenAloud },
        saysMeaning = { pronouncer.saysMeaning },
    )
}
