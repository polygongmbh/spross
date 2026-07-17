import Foundation
import WatchConnectivity

/// Watch side of the sync: receives snapshots (application context or file
/// transfer), queues answer events to the phone via `transferUserInfo`
/// (guaranteed delivery, survives app termination).
final class WatchConnectivityClient: NSObject, WCSessionDelegate, @unchecked Sendable {

    /// Set once from the main actor before `activate()`; receives the raw
    /// JSON-encoded snapshot from any delivery path.
    var onSnapshotData: (@Sendable (Data) -> Void)?

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Queue one answer event to the phone.
    func send(_ event: WatchAnswerEvent) {
        guard WCSession.isSupported() else { return }
        WCSession.default.transferUserInfo(WatchAnswerEvent.userInfo(events: [event]))
    }

    // MARK: - WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                 error: (any Error)?) {
        // why: a context sent while the watch app was dead is persisted by
        // the system — pick it up on every activation.
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
