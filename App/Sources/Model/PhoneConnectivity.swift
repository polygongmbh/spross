import Foundation
import WatchConnectivity
import WidgetKit
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
    func push(snapshotJSON: String) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired,
              session.isWatchAppInstalled else { return }
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

    /// UserDefaults key holding recently applied event UUIDs
    /// (transferUserInfo may deliver duplicates).
    private static let appliedEventIDsKey = "spross-watch-applied-event-ids"
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
        let json = WatchSnapshotBuilder.shared.build(state: box,
                                                     nowEpochMillis: Date().epochMillis)
        watchBridge.push(snapshotJSON: Self.stampTarget(box.joinStamp.target, onto: json))
    }

    /// Kern's builder JSON is profile-agnostic; stamp the TARGET language so
    /// the watch can label its one mirrored box. Falls back to the raw JSON
    /// if the round-trip ever fails (the watch decodes `target` leniently).
    static func stampTarget(_ target: String, onto json: String) -> String {
        guard var snapshot = try? WatchSnapshot.decode(Data(json.utf8)) else { return json }
        snapshot.target = target
        guard let data = try? snapshot.encoded() else { return json }
        return String(decoding: data, as: UTF8.self)
    }

    /// Apply queued watch answers ON RECEIPT, oldest first, with `now` =
    /// each event's date (FSRS elapsed time stays honest); then book them
    /// into dailyStats, persist, and refresh widgets + the watch snapshot.
    func applyWatchAnswers(_ events: [WatchAnswerEvent]) {
        guard var state = box else { return }
        let defaults = UserDefaults.standard
        var applied = defaults.stringArray(forKey: Self.appliedEventIDsKey) ?? []
        var appliedSet = Set(applied)

        let fresh = events
            .filter { !appliedSet.contains($0.id.uuidString) }
            .sorted { ($0.date, $0.cardId) < ($1.date, $1.cardId) }
        guard !fresh.isEmpty else { return }

        var appliedCount: Int32 = 0
        for event in fresh {
            // why: stale ids (profile/catalog drift) are a defined engine
            // no-op — the event is still marked seen so it never re-queues.
            if let rating = Rating(value: event.rating) {
                let outcome = BoxEngine.shared.answer(state: state, cardId: event.cardId,
                                                      rating: rating,
                                                      nowEpochMillis: event.date.epochMillis,
                                                      tzId: currentTzId())
                state = outcome.state
                if outcome.status == .applied { appliedCount += 1 }
            }
            applied.append(event.id.uuidString)
            appliedSet.insert(event.id.uuidString)
        }
        // why: watch reviews must reach dailyStats (streak/Fortschritt read
        // only dailyStats); endSession accumulates, so deltas are safe.
        if appliedCount > 0 {
            let latest = fresh.map(\.date).max() ?? Date()
            state = BoxEngine.shared.endSession(state: state, reviewsDone: appliedCount,
                                                nowEpochMillis: latest.epochMillis,
                                                tzId: currentTzId())
        }

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
