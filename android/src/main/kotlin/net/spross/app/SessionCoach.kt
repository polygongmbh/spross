package net.spross.app

import net.spross.kern.model.PresentationRole

/**
 * The three lines the FIRST round teaches itself with, one per moment the learner meets:
 * give the word a moment, answer honestly, write the one you missed.
 *
 * The same three the onboarding page said in advance — said again where each applies,
 * because a page read once before any card is not where a rule lands. It is copy and
 * nothing else: no step is added, no button waits on it, and a learner who ignores it gets
 * exactly the round everyone else does.
 *
 * It runs for the one round onboarding opened — all of it,
 * including whatever "Weiter üben" adds to that same run.
 * The flag behind it ([AppModel.coachPending]) is memory only,
 * so an app killed mid-round comes back without the lines;
 * spending it is [AppModel.finishSession]'s.
 */
object SessionCoach {

    /**
     * The line the prompt owes — the recognition this presentation is asking for.
     * Recognition only, and only while the answer is still hidden: produce says what it
     * wants with a field and a keyboard, and a revealed card is past the moment this
     * describes.
     */
    fun recognizeLine(chrome: Chrome, role: PresentationRole?, revealed: Boolean): String? =
        if (role == PresentationRole.Recognize && !revealed) chrome.sessionCoachRecognize else null
}
