import Foundation
import WatchConnectivity
import SprossKern

/// Phone side of the watch sync ("snapshot down, events up"). The snapshot
/// is the Kern `WatchSnapshotBuilder` v2 JSON (both sides pre-resolved, the
/// watch stays pure Swift). Owns the WCSession; AppModel calls
/// `push(snapshotJSON:)` on save/finish and receives decoded answer events
/// on the main actor.
final class PhoneConnectivity: NSObject, WCSessionDelegate, @unchecked Sendable {

    /// Snapshots at/over this size go through transferFile instead of
    /// updateApplicationContext (context payloads are capped around 65 KB).
    private static let contextByteLimit = 60_000

    /// Set once from the main actor before `activate()`; called from WC
    /// callbacks with decoded events.
    var onAnswerEvents: (@Sendable ([WatchAnswerEvent]) -> Void)?

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Whether a snapshot pushed right now would reach a watch at all. Asked
    /// BEFORE one is built: building it ranks the whole box per entry, and a
    /// learner with no watch paired would pay that for nothing.
    var canDeliver: Bool {
        guard WCSession.isSupported() else { return false }
        let session = WCSession.default
        return session.activationState == .activated && session.isPaired
            && session.isWatchAppInstalled
    }

    /// Push the latest snapshot. `updateApplicationContext` replaces any
    /// pending one, so frequent pushes cost nothing extra.
    func push(snapshotJSON: String) {
        guard canDeliver else { return }
        let session = WCSession.default
        let data = Data(snapshotJSON.utf8)
        if data.count < Self.contextByteLimit {
            try? session.updateApplicationContext([WatchSyncKey.snapshot: data])
        } else {
            // why: oversized snapshots would be rejected by the context API;
            // a queued file transfer delivers them reliably instead.
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("watch-snapshot-\(UUID().uuidString).json")
            guard (try? data.write(to: url, options: .atomic)) != nil else { return }
            session.transferFile(url, metadata: [WatchSyncKey.snapshot: true])
        }
    }

    // MARK: - WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                 error: (any Error)?) {}

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        // why: after a watch switch the session must be re-activated
        // or transfers from the new watch never arrive.
        session.activate()
    }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        let events = WatchAnswerEvent.decode(userInfo: userInfo)
        guard !events.isEmpty else { return }
        onAnswerEvents?(events)
    }
}

// MARK: - AppModel integration

extension AppModel {

    /// UserDefaults key holding answers that arrived before a box was loaded.
    private static let pendingEventsKey = "spross-watch-pending-events"

    /// Wire the bridge to this model and activate the session (call once at launch).
    func startWatchBridge() {
        watchBridge.onAnswerEvents = { events in
            Task { @MainActor [weak self] in self?.applyWatchAnswers(events) }
        }
        watchBridge.activate()
    }

    /// Build + push the current box as a watch snapshot (no-op without a box, or
    /// with no watch to reach).
    ///
    /// The build is the one derived document that is neither cheap nor wanted on
    /// screen: it ranks every scheduled card against each of the sixty entries it
    /// ships. Off this actor, therefore — on it, the immediate save that ends a
    /// round held the frame that raises the summary (`docs/performance.md`).
    func pushWatchSnapshot() {
        guard watchBridge.canDeliver, let box else { return }
        // why: the builder shortens a verb to its stem on the option side, and only
        // languages.json knows which prefix that is (sw "ku"/"kw", en "to ").
        let citationPrefixes = (catalog?.languages ?? [:]).mapValues { $0.optionalVerbPrefixes }
        let now = Date().epochMillis
        Task.detached { [watchBridge] in
            let json = WatchSnapshotBuilder.shared.build(state: box,
                                                         nowEpochMillis: now,
                                                         citationPrefixes: citationPrefixes)
            watchBridge.push(snapshotJSON: json)
        }
    }

    /// Apply queued watch answers ON RECEIPT, oldest first, with `now` =
    /// each event's date (FSRS elapsed time stays honest); then book them
    /// into dailyStats, and save them with the widget and watch snapshots.
    /// Answers that arrive before a box is loaded are parked on disk and
    /// applied by the next call that finds one (`activate` drains them).
    func applyWatchAnswers(_ events: [WatchAnswerEvent]) {
        let defaults = UserDefaults.standard
        // why: the session activates at launch, ahead of the box, and WatchConnectivity
        // hands over its whole backlog then and never again — a dropped event is lost.
        let parked = (defaults.array(forKey: Self.pendingEventsKey) as? [[String: Any]]) ?? []
        let events = WatchAnswerEvent.decode(userInfo: [WatchAnswerEvent.Key.events: parked]) + events
        guard var state = box else {
            if !events.isEmpty {
                defaults.set(events.map(\.userInfoEntry), forKey: Self.pendingEventsKey)
            }
            return
        }
        guard !events.isEmpty else { return }
        if !parked.isEmpty { defaults.removeObject(forKey: Self.pendingEventsKey) }

        for event in events.sorted(by: { ($0.date, $0.cardId) < ($1.date, $1.cardId) }) {
            state = BoxEngine.shared.answer(state: state, cardId: event.cardId,
                                            rating: event.rating.kernRating,
                                            nowEpochMillis: event.date.epochMillis)
        }

        box = state
        // why: the save carries the watch snapshot with it — pushing a second one
        // here only built the same document twice.
        save(state, BoxChange.changed.saveScope)
        refreshStats()
    }
}

private extension WatchRating {
    var kernRating: Rating {
        switch self {
        case .again: return .again
        case .hard: return .hard
        case .good: return .good
        case .easy: return .easy
        }
    }
}
