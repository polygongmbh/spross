package net.spross.app

import net.spross.app.ui.AnswerActions
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.Question
import net.spross.kern.session.Reading

/**
 * What a question screen is driven through, the review turn and every drill run alike:
 * kern's question, the controls under it and what it says aloud, the learner's text, the
 * armed beat, and the intents the answer area hands back. [TurnFlow] and [DrillFlow] carry it.
 *
 * Only what both drive the same way stands here. A review's write-out text and rating, a
 * drill's pause, Sprosse and close, and when each screen claims the keyboard stay with the
 * flow or screen that has them. The iOS twin is `QuestionDriving`.
 */
interface QuestionDriver {

    /** The question on screen; null while there is none to ask. */
    val question: Question?

    /** What stands under it — kern's call. */
    val controls: AnswerControls?

    /** What the question says aloud, and when. */
    val reading: Reading?

    /** The text in the field the slot asks for. */
    val fieldText: String

    /** The beat kern armed and nobody has spent yet; null once it fired or was canceled. */
    val armedBeat: AdvanceBeat?

    /** Bumped by every arming — what a timer effect keys on. */
    val beatToken: Int

    /** The beat became a tap: render the explicit Next, which books the same answer. */
    val awaitsConfirm: Boolean

    /** The armed beat elapsed; it is spent whether or not there was anything to book. */
    fun advanceElapsed()

    /**
     * Where each control under the card hands its tap — the intents put to kern.
     * [stop] is the way out, which the screen owns: what a close files is its own.
     */
    fun answerActions(stop: () -> Unit = {}): AnswerActions
}
