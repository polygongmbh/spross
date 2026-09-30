import Foundation
import SprossKern

// Saving the box: the one save path, the flush on leaving, and the two changes that ride on it.

extension AppModel {

    /// Scene went to background: every answer is already in the box, so this only makes
    /// sure it reaches disk, snapshots and all, before the app can be suspended.
    func saveNow() {
        guard let box else { return }
        pushWatchSnapshot()
        // why: the main actor cannot wait on the store's actor here; the write starts
        // at once and runs inside the time a backgrounded scene is given.
        Task { [store] in try? await store.saveNow(state: box, scope: BoxChange.leaving.saveScope) }
    }

    /// The one way a change to the box goes to disk: the store writes it behind the caller,
    /// with what `scope` carries (`BoxStore.save`), and the watch gets its snapshot alongside.
    func save(_ state: BoxState, _ scope: SaveScope) {
        if scope.writesSnapshots { pushWatchSnapshot() }
        Task { [store] in await store.save(state: state, scope: scope) }
    }

    /// Apply a change nothing derived reads, and let it ride out with the next save.
    ///
    /// The counterpart to `mutate`, for the change that moves no card, no schedule and no
    /// tally: there is nothing for `refreshStats` to take again, and nothing for the watch
    /// or the widget to be told: it is written with the box alone.
    func stamp(_ change: (BoxState) -> BoxState) {
        guard let state = box else { return }
        let next = change(state)
        box = next
        save(next, BoxChange.stamped.saveScope)
    }

    /// Apply a change to the box, save it with the snapshots, refresh statistics.
    func mutate(_ change: (inout BoxState) -> Void) {
        guard var state = box else { return }
        change(&state)
        box = state
        save(state, BoxChange.changed.saveScope)
        refreshStats()
    }
}
