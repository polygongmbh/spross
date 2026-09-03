package net.spross.app

import net.spross.kern.model.Language
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.DrillTally

/**
 * A TYPED endless drill — the atlas and the calendar — as its screen sees it.
 *
 * Their kern runs share no type: a country and a date are drawn, graded and laddered by
 * rules of their own, and kern keeps them apart. What the SCREEN does with either is one
 * thing — a card, a field, one primary action, a beat, a way out — so the run reaches it
 * through this face instead of through two screens written twice.
 *
 * Nothing here decides anything: [view] is the run as it stands right now, read fresh every
 * composition, and the rest is the same handful of taps kern already names.
 */
interface TypedDrill {

    /** The learner's answer text — kern owns what it means, the field is ours. */
    val input: String

    /** Kern has run out of questions: the screen hands the run back, once. */
    val ranOut: Boolean

    /** The beat waiting to elapse, or null where none is armed. */
    val armedBeat: AdvanceTier?

    /** Bumped by every arming — what a timer effect keys on. */
    val beatToken: Int

    /** The beat became a tap: render the explicit "Weiter", which books the same answer. */
    val awaitsConfirm: Boolean

    /**
     * The tile this question was answered off, or null while it is still owed — what the
     * grid marks ✓ and ✗ with once a tap has landed. Null the whole way up a drill whose
     * questions are all written.
     */
    val chosen: String?

    /** The question and the figures as they stand, in the words this learner reads. */
    fun view(chrome: Chrome): TypedDrillView

    /** A live keystroke: writing the answer out IS the answer, within kern's exact-only guard. */
    fun type(text: String)

    /**
     * An answer arriving whole rather than a letter at a time — a tapped tile, which the
     * screen offers only where the question came with [TypedDrillPrompt.choices].
     */
    fun choose(text: String)

    /** The ONE primary action: an empty field asks to see the answer, a typed one checks it. */
    fun primary()

    /** The tap that books whatever the feedback already said — and the beat's stand-in. */
    fun confirm()

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter()

    fun advanceElapsed()

    /** Leaving, from the corner or from "Fertig" — a pending answer books as the tap would. */
    fun close(standingRecord: Int): TypedDrillClose
}

/** The question on the card: what is asked, what stands there, and what the reveal grows. */
data class TypedDrillPrompt(
    /** What is being asked. Never names a language — the placeholder says which side is owed. */
    val ask: String,
    /** What stands on the card; null where a picture alone is the question. */
    val text: String?,
    /** What language [text] is written in — never shown; it tags the words for TalkBack. */
    val language: Language?,
    /** The canonical answer, for the reveal. */
    val display: String,
    /** The answer side's neighboring form, where the run hands one over. */
    val gloss: String? = null,
    /** The picture beside the words — a country's flag; a date carries none. */
    val emoji: String? = null,
    /** Whether showing [emoji] while the answer is owed would ANSWER the question. */
    val emojiIsGiveaway: Boolean = false,
    /**
     * The tiles this question is answered off, in kern's own shuffled order — null where it
     * is written instead, which is every Sprosse above the calendar's warm-up.
     */
    val choices: List<String>? = null,
    /**
     * Whether a DATE is owed rather than a reading — the calendar turned round. The keyboard
     * and the placeholder are the only things that follow from it.
     */
    val digits: Boolean = false,
)

/** One typed run as it stands: the question, the ladder under it, and the score line. */
data class TypedDrillView(
    /** Bumped per question — what the card's identity and an autoplay effect key on. */
    val index: Int,
    val level: Int,
    val streak: Int,
    val bestStreak: Int,
    val outcomes: List<AnswerOutcome>,
    val tally: DrillTally,
    val feedback: TurnFeedback,
    /** The card may open: the almost hold and the miss each put an answer worth seeing whole. */
    val showsAnswer: Boolean,
    /** The way out, offered under the button that goes on, on the second miss in a row. */
    val offersFinish: Boolean,
    /** What a refused answer actually NAMED — only beside a revealed miss. */
    val otherWord: Match.OtherWord?,
    /** The language an answer is owed in — the learner's own on a reversed run. */
    val answerLanguage: Language,
    val prompt: TypedDrillPrompt,
)

/** What a closed typed run owes the page that started it. */
data class TypedDrillClose(
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /** The Sprosse the run REACHED, not the one it ends on. */
    val bestLevel: Int,
)
