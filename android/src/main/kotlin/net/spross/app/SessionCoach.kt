package net.spross.app

import net.spross.kern.session.CoachLine

/**
 * The words for kern's [CoachLine]s — which line stands where is `Coaching`'s.
 *
 * It runs for the one round onboarding opened — all of it,
 * including whatever "Weiter üben" adds to that same run.
 * The flag behind it ([AppModel.coachActive]) is memory only,
 * so an app killed mid-round comes back without the lines;
 * spending it is [AppModel.finishSession]'s.
 */
object SessionCoach {

    fun line(chrome: Chrome, line: CoachLine): String = when (line) {
        CoachLine.Recognize -> chrome.sessionCoachRecognize
        CoachLine.Grade -> chrome.sessionCoachGrade
        CoachLine.Write -> chrome.sessionCoachWrite
    }
}
