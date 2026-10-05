package net.spross.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.kern.box.BoxState
import net.spross.kern.store.BoxBackup
import net.spross.kern.store.BoxChange
import net.spross.kern.store.SaveScope
import net.spross.kern.store.StoredBox
import net.spross.kern.store.StoredBoxes

/**
 * The one way a change to the box goes to disk: the store writes it behind the caller,
 * with what [scope] carries ([BoxStore.save]).
 */
internal fun AppModel.save(state: BoxState, scope: SaveScope) = store.save(state, scope)

/**
 * Backgrounding (`SprossActivity.onStop`): every answer is already in the box, so this only
 * makes sure it reaches disk, snapshots and all, before the process can be taken away —
 * which is why it writes before returning rather than queuing.
 */
fun AppModel.saveNow() {
    val state = box ?: return
    store.saveNow(state, BoxChange.Leaving.saveScope)
}

/**
 * The backup file's text: [only], or every language, without what belongs to this
 * device ([BoxBackup]).
 */
fun AppModel.backupJson(only: String? = null): String = BoxBackup.encode(store.everyLanguage(), only)

/**
 * The languages an export would carry: a box the learner only ever opened is not one of
 * them, so the export neither offers it nor lands it empty on the other phone.
 */
fun AppModel.backupLanguages(): List<String> = BoxBackup.carried(store.everyLanguage())

/**
 * Writes the languages a backup restored, then re-opens the pair on screen from the
 * store, so the box drawn is the restored one and not the one it replaced.
 *
 * The pair it opens is the one the FILE names ([StoredBox.source]): the progress was
 * made under that known language, and re-reading it under this device's would leave
 * every own word written in the old one unpaired and untrained. A file from before the
 * store recorded it names none, and the device's own setting stands.
 *
 * On a first run there is no pair on screen yet: the file's last-studied language becomes
 * it, with [firstRunSource] standing in for a file that names no known language, and the
 * onboarding it was picked from gives way to Home.
 */
fun AppModel.restoreBoxes(imported: StoredBoxes, firstRunSource: String? = null) {
    val stamp = box?.joinStamp
    val target = stamp?.target ?: imported.lastStudied() ?: return
    val source = imported.boxes[target]?.source ?: stamp?.source ?: firstRunSource ?: return
    // why: a first run has no profile for the launch after this one to reopen.
    if (stamp == null) profile.set(source, target)
    viewModelScope.launch {
        withContext(Dispatchers.IO) { store.restore(imported) }
        activate(source, target, if (stamp == null) Screen.Home else Screen.Box())
    }
}
