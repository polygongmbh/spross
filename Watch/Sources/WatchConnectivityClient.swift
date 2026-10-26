import Foundation
import WatchConnectivity

/// Watch side of the sync: receives snapshots (application context or file
/// transfer), holds answer events and hands them to the phone in one
/// `transferUserInfo` (guaranteed delivery, survives app termination) when
/// the app leaves the foreground or a round ends — the phone applies a batch
/// with one snapshot build, not one per tap.
final class WatchConnectivityClient: NSObject, WCSessionDelegate, @unchecked Sendable {

    /// Set once from the main actor before `activate()`; receives the raw
    /// JSON-encoded snapshot from any delivery path.
    var onSnapshotData: (@Sendable (Data) -> Void)?

    private static let pendingKey = "spross-watch-pending-events"
    private let lock = NSLock()

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Hold one answer event for the next `flush()`; kept on disk so a killed app loses none.
    func send(_ event: WatchAnswerEvent) {
        lock.withLock {
            let held = UserDefaults.standard.array(forKey: Self.pendingKey) as? [[String: Any]] ?? []
            UserDefaults.standard.set(held + [event.userInfoEntry], forKey: Self.pendingKey)
        }
    }

    /// Hand every held event to the phone as one batch.
    func flush() {
        guard WCSession.isSupported() else { return }
        lock.withLock {
            guard let held = UserDefaults.standard.array(forKey: Self.pendingKey) as? [[String: Any]],
                  !held.isEmpty else { return }
            WCSession.default.transferUserInfo([WatchAnswerEvent.Key.events: held])
            UserDefaults.standard.removeObject(forKey: Self.pendingKey)
        }
    }

    /// Drop the held events — they answered another language pair's cards.
    func discardPending() {
        lock.withLock { UserDefaults.standard.removeObject(forKey: Self.pendingKey) }
    }

    // MARK: - WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                 error: (any Error)?) {
        // why: a context sent while the watch app was dead is persisted by
        // the system — pick it up on every activation.
        flush() // why: answers held when a previous run was killed reach the phone.
        if let data = session.receivedApplicationContext[WatchSyncKey.snapshot] as? Data {
            onSnapshotData?(data)
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        if let data = applicationContext[WatchSyncKey.snapshot] as? Data {
            onSnapshotData?(data)
        }
    }

    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        // Oversized-snapshot fallback path (phone sends transferFile).
        if let data = try? Data(contentsOf: file.fileURL) {
            onSnapshotData?(data)
        }
    }
}
