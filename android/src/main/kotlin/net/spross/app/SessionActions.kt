package net.spross.app

import net.spross.kern.model.Rating
import net.spross.kern.session.SessionIntent

fun AppModel.startSession() = begin(SessionIntent.Start)

/**
 * The done card's extra round: kern composes the mixing round itself — everything due,
 * packed vocab within the budget, then pull-aheads — and no-ops when that is empty.
 */
fun AppModel.startExtraSession() = begin(SessionIntent.StartExtra)

/**
 * The session card's short round: the day's own round taken short — its due work
 * alone, a round's worth of it — and a no-op when that is empty.
 */
fun AppModel.startShortSession() = begin(SessionIntent.StartShort)

private fun AppModel.begin(intent: SessionIntent) {
    val started = dispatch(intent) ?: return
    // A round that came back empty never took the learner anywhere, and leaves no run
    // behind for the next tap to inherit.
    if (started.currentCardId == null) {
        dropRun()
        return
    }
    navigate(Screen.Session)
}

fun AppModel.answerCurrent(rating: Rating) {
    sessionRun ?: return
    // why: the card is leaving — a word still sounding must not follow the learner
    // onto the next one, the same cut iOS makes in resetCardState().
    pronouncer.stop()
    dispatch(SessionIntent.Answer(rating))
}

/**
 * Take the card on screen out of the round: suspend it and step past with no rating
 * at all. Never a grade — the learner is saying it should not be ASKED, not that
 * they failed it ([SessionIntent.SuspendCurrent]).
 */
fun AppModel.suspendCurrentCard() {
    sessionRun ?: return
    pronouncer.stop()
    dispatch(SessionIntent.SuspendCurrent)
}

fun AppModel.continueEndless() {
    sessionRun ?: return
    dispatch(SessionIntent.ContinueEndless)
}
