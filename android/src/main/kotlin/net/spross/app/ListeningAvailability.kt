package net.spross.app

import net.spross.kern.listen.ListeningPool

/**
 * The platform half of what listening can play, exactly as [letterReport] is the drill's.
 *
 * Kern sweeps the box and the catalog for itself; the only facts it cannot have are whether
 * this device can say anything in each of the two languages — TWO booleans here rather than
 * the drills' one, because a turn says the target word AND its meaning, and a profile
 * routinely has a voice for one side and not the other.
 *
 * Nothing is cached: a voice may be installed in Settings while the app sleeps, so this is
 * asked freshly on every foreground (`SprossActivity.onResume`).
 */
fun AppModel.listeningReport(): ListeningPool.Report? {
    val state = box ?: return null
    val cat = catalog ?: return null
    val stamp = state.joinStamp
    return ListeningPool.report(
        cat,
        state,
        stamp.source,
        stamp.target,
        hasTargetVoice = pronouncer.canSpeak(stamp.target),
        hasSourceVoice = pronouncer.canSpeak(stamp.source),
        seed = now(),
    )
}
