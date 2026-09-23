package net.spross.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.kern.listen.ListeningPool

/**
 * Opens the playlist, dealing it here and nowhere else — the walk of the whole join with
 * two catalog lookups per card belongs to a run being opened, not to every glance at
 * Home. Nothing else may be talking into it: a word left sounding from the screen behind
 * would land in the middle of the run's first turn.
 */
fun AppModel.startListening() {
    val state = box ?: return
    val cat = catalog ?: return
    val stamp = state.joinStamp
    pronouncer.stop()
    // why: the voice table belongs to the synthesizer and is read here; the deal it feeds
    // walks the whole join asking the catalog for BOTH sides' audio — some sixteen
    // hundred lookups on a full profile — so it happens off this thread.
    val hasTarget = pronouncer.canSpeak(stamp.target)
    val hasSource = pronouncer.canSpeak(stamp.source)
    val dealtAt = now()
    viewModelScope.launch {
        val report = withContext(Dispatchers.Default) {
            ListeningPool.report(
                cat, state, stamp.source, stamp.target,
                hasTargetVoice = hasTarget, hasSourceVoice = hasSource,
                seed = dealtAt,
            )
        }
        // why: the screen turns only once there is a playlist, so a run never opens on
        // silence — the one case a box with words in it can still deal nothing.
        if (report.candidates.isEmpty()) return@launch
        listening.start(report.candidates)
        navigate(Screen.Listening)
    }
}

/** The ✕, Back, and the bedtime running out all arrive here. */
fun AppModel.closeListening() {
    listening.stop()
    navigate(Screen.Home)
}
