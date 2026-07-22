import Foundation
import Observation
import WidgetKit

/// Watch app state: holds the latest phone snapshot, drains its due list in
/// a micro-review loop, and queues answer events back to the phone.
/// No watch-local FSRS — the phone reschedules; a rated card simply leaves
/// the local due list until the next snapshot arrives.
@MainActor
@Observable
final class WatchModel {

    private(set) var snapshot: WatchSnapshot?

    // MARK: Review-loop state
    var reviewPresented = false
    /// "Üben" practice sheet (pure-local multiple choice; no FSRS).
    var practicePresented = false
    private(set) var queue: [String] = []
    private(set) var currentID: String?
    var revealed = false
    private(set) var answeredCount = 0
    private(set) var reviewTotal = 0

    let connectivity = WatchConnectivityClient()
    let calendar = Calendar.current

    // MARK: - Launch

    func start() {
        #if DEBUG
        // UI-test hooks: `-uitest-snapshot` loads the bundled fixture instead
        // of stored/synced state; `-uitest-autostart` opens the review loop,
        // `-uitest-reveal` also flips the first card; `-uitest-practice` opens
        // the Üben practice sheet (screenshot verification without a paired
        // phone — simctl cannot tap).
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-uitest-snapshot") {
            snapshot = Self.loadFixture()
            if arguments.contains("-uitest-autostart") {
                startReview()
                revealed = arguments.contains("-uitest-reveal")
            }
            if arguments.contains("-uitest-practice") {
                practicePresented = true
            }
            return
        }
        #endif
        snapshot = WatchSnapshotStore.load()
        connectivity.onSnapshotData = { data in
            Task { @MainActor [weak self] in self?.receiveSnapshot(data) }
        }
        connectivity.activate()
    }

    static func loadFixture() -> WatchSnapshot? {
        guard let url = Bundle.main.url(forResource: "WatchFixture", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? WatchSnapshot.decode(data)
    }

    /// Fresh snapshot from the phone: replaces local state (the phone is the
    /// source of truth and already folded in applied events). Stale or
    /// out-of-order deliveries are dropped.
    func receiveSnapshot(_ data: Data) {
        guard let incoming = try? WatchSnapshot.decode(data) else { return }
        if let current = snapshot, current.generated > incoming.generated { return }
        snapshot = incoming
        WatchSnapshotStore.save(incoming)
        WidgetCenter.shared.reloadAllTimelines()
        if reviewPresented {
            // why: mid-review the visible queue must not resurrect cards the
            // user just rated (their events may not be applied phone-side yet).
            let due = Set(incoming.dueEntries(now: Date()).map(\.cardId))
            queue = queue.filter { due.contains($0) }
            if let id = currentID, incoming.entry(id: id) == nil {
                advance()
            }
        }
    }

    // MARK: - Derived

    var dueCount: Int {
        snapshot?.dueEntries(now: Date()).count ?? 0
    }

    var tomorrowDueCount: Int {
        snapshot?.tomorrowDueCount(now: Date(), calendar: calendar) ?? 0
    }

    var currentEntry: WatchSnapshot.Entry? {
        currentID.flatMap { snapshot?.entry(id: $0) }
    }

    // MARK: - Practice ("Üben")

    /// Enough on-watch vocab to build a multiple-choice question.
    var canPractice: Bool { (snapshot?.entries.count ?? 0) >= 2 }

    /// A fresh pure-local practice run over the current snapshot, or nil when
    /// there isn't enough vocab yet.
    func makePracticeModel() -> WatchPracticeModel? {
        guard let snapshot, snapshot.entries.count >= 2 else { return nil }
        return WatchPracticeModel(snapshot: snapshot)
    }

    // MARK: - Review loop

    func startReview() {
        guard let snapshot else { return }
        queue = snapshot.dueEntries(now: Date()).map(\.cardId)
        reviewTotal = queue.count
        answeredCount = 0
        revealed = false
        currentID = queue.first
        reviewPresented = true
    }

    /// Self-grade the current card (rating raw 1–4): queue the event to the
    /// phone, mark it answered locally (drops out of the due list), persist,
    /// advance. Both roles are graded this way — the watch never types.
    func rate(_ rating: Int) {
        guard let id = currentID, var snap = snapshot else { return }
        connectivity.send(WatchAnswerEvent(cardId: id, rating: rating, date: Date()))
        snap.answeredCardIDs.append(id)
        snapshot = snap
        WatchSnapshotStore.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
        answeredCount += 1
        advance()
    }

    func reveal() {
        revealed = true
    }

    func endReview() {
        reviewPresented = false
        currentID = nil
        queue = []
    }

    private func advance() {
        revealed = false
        if let id = currentID, let index = queue.firstIndex(of: id) {
            queue.remove(at: index)
        }
        currentID = queue.first
    }
}
