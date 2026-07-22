import Foundation
import WatchConnectivity
import WidgetKit
import DuoKern

/// Phone side of the watch sync ("snapshot down, events up", see
/// Shared/Sources/WatchSnapshot.swift). Owns the WCSession; AppModel calls
/// `push(snapshot:)` on save/finish and receives decoded answer events on
/// the main actor.
final class PhoneConnectivity: NSObject, WCSessionDelegate, @unchecked Sendable {

    /// Snapshots at/over this size go through transferFile instead of
    /// updateApplicationContext (context payloads are capped around 65 KB).
    private static let contextByteLimit = 60_000

    /// Set once from the main actor before `activate()`; called from WC
    /// callbacks with decoded, not-yet-deduplicated events.
    var onAnswerEvents: (@Sendable ([WatchAnswerEvent]) -> Void)?

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Push the latest snapshot. `updateApplicationContext` replaces any
    /// pending one, so frequent pushes cost nothing extra.
    func push(_ snapshot: WatchSnapshot) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired,
              session.isWatchAppInstalled else { return }
        guard let data = try? snapshot.encoded() else { return }
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

    /// UserDefaults key holding recently applied event UUIDs
    /// (transferUserInfo may deliver duplicates).
    private static let appliedEventIDsKey = "watch-applied-event-ids"
    private static let appliedEventIDsCap = 1000

    /// Wire the bridge to this model and activate the session (call once at launch).
    func startWatchBridge() {
        watchBridge.onAnswerEvents = { events in
            Task { @MainActor [weak self] in self?.applyWatchAnswers(events) }
        }
        watchBridge.activate()
    }

    /// Build + push the current box as a watch snapshot (no-op without a box).
    func pushWatchSnapshot() {
        guard let box else { return }
        watchBridge.push(WatchSnapshot.make(from: box, now: Date()))
    }

    /// Apply queued watch answers ON RECEIPT, oldest first, with `now:` =
    /// each event's date; then book them into dailyStats, persist, and
    /// refresh widgets + the watch snapshot.
    func applyWatchAnswers(_ events: [WatchAnswerEvent]) {
        guard var state = box else { return }
        let defaults = UserDefaults.standard
        var applied = defaults.stringArray(forKey: Self.appliedEventIDsKey) ?? []
        var appliedSet = Set(applied)

        let fresh = events
            .filter { !appliedSet.contains($0.id.uuidString) }
            .sorted { ($0.date, $0.cardID) < ($1.date, $1.cardID) }
        guard !fresh.isEmpty else { return }

        for event in fresh {
            state = BoxEngine.answer(state: state, cardID: event.cardID,
                                     rating: event.rating, now: event.date,
                                     calendar: calendar)
            applied.append(event.id.uuidString)
            appliedSet.insert(event.id.uuidString)
        }
        // why: watch reviews must reach dailyStats (streak/Fortschritt read
        // only dailyStats); endSession accumulates, so deltas are safe.
        let latest = fresh.map(\.date).max() ?? Date()
        state = BoxEngine.endSession(state: state, reviewsDone: fresh.count,
                                     now: latest, calendar: calendar)

        if applied.count > Self.appliedEventIDsCap {
            applied.removeFirst(applied.count - Self.appliedEventIDsCap)
        }
        defaults.set(applied, forKey: Self.appliedEventIDsKey)

        box = state
        persist(state, immediate: true)
        refreshStats()
        WidgetCenter.shared.reloadTimelines(ofKind: "SprossWordWidget")
        pushWatchSnapshot()
    }
}
