package net.spross.app

import net.spross.kern.listen.ListeningPool

/**
 * Whether the listening card stands here, exactly as [letterReport] is the drill's.
 *
 * Kern sweeps the box and the catalog for itself; the only facts it cannot have are whether
 * this device can say anything in each of the two languages — TWO booleans here rather than
 * the drills' one, because a turn says the target word AND its meaning, and a profile
 * routinely has a voice for one side and not the other.
 *
 * The GATE, never the playlist: `ListeningPool.offered` stops at the first word it can say,
 * so this is cheap enough to ask on the caller's thread like the drill's own sweep. The deal
 * belongs to a run and is asked once, in `AppModel.startListening`.
 *
 * Nothing is cached: a voice may be installed in Settings while the app sleeps, so this is
 * asked freshly on every foreground (`SprossActivity.onResume`).
 */
fun AppModel.listeningOffer(): Boolean {
    val state = box ?: return false
    val cat = catalog ?: return false
    val stamp = state.joinStamp
    return ListeningPool.offered(
        cat,
        state,
        stamp.source,
        stamp.target,
        hasTargetVoice = pronouncer.canSpeak(stamp.target),
        hasSourceVoice = pronouncer.canSpeak(stamp.source),
    )
}
