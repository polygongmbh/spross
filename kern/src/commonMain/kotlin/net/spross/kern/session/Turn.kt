package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating

/**
 * Where one answer stands inside a turn.
 *
 * Named by what it says about the ANSWER, never by the color a field wears for it:
 * the same four states drive a text field, a card face and a screen reader announcement.
 */
sealed interface TurnFeedback {
    /** Nothing decided yet — the turn is still open. */
    data object Neutral : TurnFeedback

    /** The word came back clean, and it stands in the learner's own text. */
    data object Correct : TurnFeedback

    /**
     * Accepted, but not cleanly.
     * [correctForm] is the form the card owes back, and the turn holds here until it has been seen —
     * which is why no beat is armed for it.
     */
    data class Almost(val correctForm: String, val reason: AlmostReason) : TurnFeedback

    /**
     * The word was NOT produced: the card carries the answer and the retry stays open.
     * The one state where the card and the field deliberately say different things
     * (see [TurnState.retryApproved]).
     */
    data object Revealed : TurnFeedback
}

/** Why an accepted answer is only [TurnFeedback.Almost]. */
enum class AlmostReason {
    /** A slip inside the typo budget; the proper spelling is what is owed back. */
    Typo,

    /** A form this very card accepts, just not the one that played (a sound prompt). */
    Heard,
}

/**
 * "Finishing the word IS the answer": the beat after a live-typed answer goes exact.
 * Short enough that moving on still reads as finishing the word rather than a separate beat after it.
 */
const val ADVANCE_LIVE_MS: Long = 450

/**
 * The beat after an explicit Check/Enter — long enough that the correctness cue
 * and the tap's own confirmation both land before the turn moves.
 */
const val ADVANCE_EXPLICIT_MS: Long = 1200

/**
 * How long an accepted answer stands before the turn moves on, by how it was confirmed.
 * One pair of numbers for every surface, so a new screen cannot mint a second timing.
 */
enum class AdvanceTier(val delayMs: Long) {
    Live(ADVANCE_LIVE_MS),
    Explicit(ADVANCE_EXPLICIT_MS),
}

/**
 * The write-it-out step: a word you MISSED gets typed once with the answer in view
 * before the turn ends — the bit of encoding a reveal plus a single tap never gives.
 *
 * It is encoding, never a grade. [pendingRating] was already chosen and is applied
 * unchanged, so self-grading still owns the schedule (README §6).
 */
data class CopyStep(
    /** Held, never changed. */
    val pendingRating: Rating,
    /** The submitted copy was a different word — point back at the card. */
    val missed: Boolean,
    /** The word stands written exactly; the [AdvanceTier.Live] beat is armed. */
    val written: Boolean,
)

/**
 * One turn, whole and immutable: the card under review, how it was asked,
 * and everything the answer has done to it so far.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the
 * focus, and hands text in through [TurnIntent]. What is in here is every rule that
 * decides what the text means and which rating it earns.
 */
data class TurnState(
    /** The card under review: what is graded, and what a write-out copies. */
    val card: Card,
    val role: PresentationRole,
    /** [ProducePrompt.Sound] moves the answer to the SOURCE side: the card asks what it means. */
    val prompt: ProducePrompt,
    /** The form the prompt stands on — the rotated target form on recognize. */
    val promptForm: String,
    /** `reviewCount == 0`: the word is being taught, so a miss is still written out. */
    val firstExposure: Boolean,
    /** A word that already sticks — consolidated, the one landed bar — is never slowed down by a write-out. */
    val consolidated: Boolean,
    val feedback: TurnFeedback,
    /** The learner asked to see the answer without producing it — the self-grade path. */
    val revealed: Boolean,
    /** What an [TurnFeedback.Almost] hold already earned, applied when it is confirmed. */
    val pendingRating: Rating?,
    /** Set when the typed answer is a word the catalog owns elsewhere; the reveal names it. */
    val otherWord: Match.OtherWord?,
    /** The retype after a miss has reached the word: the field is right while the card still reveals. */
    val retryApproved: Boolean,
    /** Non-null ⇒ the write-out owns the turn. */
    val copyStep: CopyStep?,
    /**
     * The sound prompt has been PUT IN WRITING: the word the learner could not listen to
     * stands as text instead, and nothing else about the turn moves — same answer, same
     * language, same rating. An escape hatch for a moment when listening is impossible,
     * never a mode: it lasts this turn, and the next card asked by ear asks by ear again.
     */
    val promptInText: Boolean,
    val promptShownAtMillis: Long,
    /** The recall attempt in ms; 0 means unmeasured and never earns Easy. */
    val recallMs: Long,
) {
    /**
     * The card carries the answer, because nothing was produced.
     * A clean answer and a typo's correction already stand in the learner's own text,
     * where revealing again would put the same word on screen twice.
     */
    val answerRevealed: Boolean
        get() = feedback == TurnFeedback.Revealed || revealed

    /**
     * What had to be READ before recall could start — the prompted form on recognize and
     * on a card asked by ear (the target word, heard or written), the source word on the
     * produce card that was revealed.
     */
    val promptChars: Int
        get() = if (role == PresentationRole.Recognize || prompt == ProducePrompt.Sound) {
            promptForm.length
        } else {
            card.source.text.length
        }

    /**
     * The language the ANSWER is written in — the source on a card asked by ear, which asks
     * what the word means, the target everywhere else. The one place a field's placeholder,
     * a screen reader tag and a keyboard hint read it from.
     */
    val answerLang: String
        get() = if (prompt == ProducePrompt.Sound) card.source.lang else card.target.lang

    /**
     * The form the turn owes back: the meaning on a card asked by ear, the target word
     * everywhere else. What a miss reveals and a retype is measured against.
     */
    val answerText: String
        get() = if (prompt == ProducePrompt.Sound) card.source.text else card.target.text
}

/** What the learner does to a turn. */
sealed class TurnIntent {
    /**
     * A live keystroke, in whichever field the turn currently owns:
     * the answer, the retype after a miss, or the write-out.
     */
    data class InputChanged(val text: String) : TurnIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : TurnIntent()

    /** "Aufdecken" — the ask that closes the recall attempt. */
    data object Reveal : TurnIntent()

    /** One of the three self-grade buttons; the clock and the prompt length refine it. */
    data class SelfGrade(val verdict: SelfGrading.Verdict) : TurnIntent()

    /**
     * The explicit tap that stands in for a beat: an [TurnFeedback.Almost] hold's "Weiter",
     * and the same button where a screen reader runs and no timer ever armed.
     */
    data object ConfirmPending : TurnIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : TurnIntent()

    /** Give up on an open retry. */
    data object GiveUp : TurnIntent()

    /** Enter in the write-out field. */
    data class CopySubmit(val text: String) : TurnIntent()

    /** Leave the write-out; the rating is already decided, so skipping costs nothing. */
    data object SkipCopy : TurnIntent()

    /** "Can't listen right now?" — put the sound prompt's word on screen as text. */
    data object ShowPromptText : TurnIntent()
}

/** The verdict a cue sounds for. What it sounds like, and whether it buzzes too, is the platform's. */
enum class ToneKind { Correct, Wrong, Reveal }

/** What a reduction asks the platform to do about the world outside the turn. */
sealed class TurnEffect {
    /** Terminal: hand the rating to the run ([SessionIntent.Answer]). The turn is over. */
    data class Answer(val rating: Rating) : TurnEffect()

    /**
     * Arm (or re-arm) the beat before the turn moves on.
     * Where a screen reader runs a timed change is hostile — it truncates the announcement and
     * moves the page under the user — so the platform skips the timer and renders the explicit
     * button instead, which books the same rating through [TurnIntent.ConfirmPending].
     */
    data class ArmAdvance(val tier: AdvanceTier) : TurnEffect()

    data object CancelAdvance : TurnEffect()

    /** Put [text] in the field the turn owns — the CONTENT is the rule, the field is not. */
    data class PrimeField(val text: String) : TurnEffect()

    /** Sound the verdict. */
    data class Tone(val kind: ToneKind) : TurnEffect()

    /** A pause that waits for a tap must give the keyboard back, or it covers the button. */
    data object ReleaseFocus : TurnEffect()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class TurnReduction(val state: TurnState, val effects: List<TurnEffect>)
