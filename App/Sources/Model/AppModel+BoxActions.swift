import Foundation
import SprossKern

// What the Box screen does to the box: queue, suspend, forget, and reset.

extension AppModel {

    /// The shelf's queue control: queue exactly the cards the shelf's own count
    /// promised. One predicate answers both (`BoxBrowser.queueableCardIds`),
    /// so a control can never name a number the queue does not add.
    func queueArea(_ area: String) {
        guard let box else { return }
        let ids = BoxBrowser.shared.queueableCardIds(state: box, area: area)
        guard !ids.isEmpty else { return }
        mutate { $0 = BoxEngine.shared.queue(state: $0, cardIds: ids) }
    }

    func setSuspended(cardID: String, suspended: Bool) {
        mutate {
            $0 = BoxEngine.shared.setSuspended(state: $0, cardId: cardID, suspended: suspended,
                                               nowEpochMillis: Date().epochMillis)
        }
    }

    /// Drop ONE card's schedule, keeping the card and anything filed against it —
    /// the single-word answer to a reset (`BoxEngine.forget`).
    func forget(cardID: String) {
        mutate { $0 = BoxEngine.shared.forget(state: $0, cardId: cardID) }
    }

    /// Take a queued word back out of the queue by name — the opposite of `queueCard`,
    /// offered only where a single word was queued by name (`BoxCardRow.queue`). A no-op
    /// once a round has already brought the card in (`BoxEngine.unqueue`).
    func unqueue(cardID: String) {
        mutate { $0 = BoxEngine.shared.unqueue(state: $0, cardId: cardID) }
    }

    /// Take a whole shelf's queue back out at once — the opposite of `queueArea`,
    /// offered by the shelf's own control once queuing has emptied (`BoxEngine.unqueueArea`).
    func unqueueArea(_ area: String) {
        mutate { $0 = BoxEngine.shared.unqueueArea(state: $0, area: area) }
    }

    /// Destructive fresh start: every schedule and tally goes, the join, the
    /// user's config (budget) and their own words stay — which of those a reset
    /// keeps is the engine's ruling, not this layer's (`kern/docs/grading.md`).
    func resetBox() async {
        guard let old = box else { return }
        let fresh = BoxEngine.shared.reset(state: old)
        box = fresh
        do {
            try await store.saveNow(state: fresh, scope: BoxChange.changed.saveScope)
            refreshStats()
            pushWatchSnapshot()
        } catch {
            loadFailure = .resetFailed(reason: error.localizedDescription)
        }
    }
}
