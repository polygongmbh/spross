package net.spross.kern.session

import net.spross.kern.model.PresentationRole

/**
 * The three lines the FIRST round teaches itself with, one per moment the learner meets:
 * give the word a moment, answer honestly, write the one you missed.
 *
 * The same three the onboarding page said in advance — said again where each applies,
 * because a page read once before any card is not where a rule lands. They are copy and
 * nothing else: no step is added, no button waits on them, and a learner who ignores them
 * gets exactly the round everyone else does. The words are each app's string table's.
 */
enum class CoachLine {
    /** Give the word a moment — over a recognition prompt. */
    Recognize,

    /** Answer honestly — under the self-grade row, in place of its standing question. */
    Grade,

    /** Write the one you missed — under the write-out field. */
    Write,
}

/** Which [CoachLine] stands where, while the round still owes its coaching ([coaching]). */
object Coaching {

    /**
     * The line the prompt owes — the recognition this presentation is asking for.
     * Recognition only, and only while the answer is still hidden: produce says what it
     * wants with a field and a keyboard, and a revealed card is past the moment this describes.
     */
    fun promptLine(coaching: Boolean, role: PresentationRole?, revealed: Boolean): CoachLine? =
        CoachLine.Recognize.takeIf { coaching && role == PresentationRole.Recognize && !revealed }

    /**
     * The line under the answer controls: the write-out's while one stands, else the grade's,
     * which REPLACES the self-grade row's standing question rather than joining it —
     * two quiet lines in one slot is one more than anybody reads.
     * Null where the controls keep their own caption.
     */
    fun controlsLine(coaching: Boolean, writing: Boolean): CoachLine? = when {
        !coaching -> null
        writing -> CoachLine.Write
        else -> CoachLine.Grade
    }
}
