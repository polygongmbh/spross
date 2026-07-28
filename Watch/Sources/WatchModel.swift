import Foundation
import Observation
import WidgetKit
import WatchKit

/// Watch app state: holds the latest phone snapshot and drains it as ONE
/// graded multiple-choice session — due cards first, then review-ahead. Each
/// tap is scored from correctness + response time (`WatchGrading`) and queued
/// to the phone as an FSRS review. No watch-local FSRS — the phone reschedules;
/// an answered card simply leaves the local due list until the next snapshot.
@MainActor
@Observable
final class WatchModel {

    private(set) var snapshot: WatchSnapshot?

    // MARK: Session state (one graded multiple-choice loop)
    var sessionPresented = false
    private(set) var queue: [String] = []
    private(set) var currentID: String?
    private(set) var currentQuestion: WatchPracticeQuestion?
    /// The tapped option index; non-nil freezes the tiles into feedback state.
    private(set) var selectedIndex: Int?
    private(set) var streak = 0
    private(set) var answeredCount = 0
    private(set) var reviewTotal = 0

    /// When the current question became visible — the response-time clock.
    private var questionShownAt = Date()
    private var rng = SystemRandomNumberGenerator()
    private var autoAdvance: Task<Void, Never>?

    let connectivity = WatchConnectivityClient()
    let calendar = Calendar.current

    // MARK: - Launch

    func start() {
        #if DEBUG
        // UI-test hooks: `-uitest-snapshot` loads the bundled fixture instead
        // of stored/synced state; `-uitest-autostart` (or the legacy
        // `-uitest-practice`) opens the quiz; `-uitest-streak N` presets the
        // streak (screenshot verification without a paired phone — simctl
        // cannot tap).
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-uitest-snapshot") {
            snapshot = Self.loadFixture()
            if arguments.contains("-uitest-autostart") || arguments.contains("-uitest-practice") {
                startSession()
                if let i = arguments.firstIndex(of: "-uitest-streak"), i + 1 < arguments.count,
                   let n = Int(arguments[i + 1]) {
                    streak = n
                }
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
        if sessionPresented {
            // why: mid-session the queue must not resurrect cards the user just
            // answered (their events may not be applied phone-side yet).
            let answered = Set(incoming.answeredCardIDs)
            let present = Set(incoming.entries.map(\.cardId))
            queue = queue.filter { present.contains($0) && !answered.contains($0) }
            if let id = currentID, !present.contains(id) {
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

    /// Enough on-watch vocab to build a multiple-choice question AND something
    /// to answer (due now or reviewable ahead).
    var canStart: Bool {
        guard let snapshot, snapshot.entries.count >= 2 else { return false }
        return dueCount > 0 || !snapshot.reviewAheadEntries(now: Date()).isEmpty
    }

    // MARK: - Session

    func startSession() {
        guard let snapshot, snapshot.entries.count >= 2 else { return }
        let due = snapshot.dueEntries(now: Date()).map(\.cardId)
        let ahead = snapshot.reviewAheadEntries(now: Date()).map(\.cardId)
        queue = due + ahead
        // Title denominator tracks the due portion; a pure review-ahead session
        // (nothing due) falls back to the whole queue so it isn't "N/0".
        reviewTotal = due.isEmpty ? queue.count : due.count
        answeredCount = 0
        streak = 0
        currentID = queue.first
        makeQuestionForCurrent()
        sessionPresented = true
    }

    /// Score the tapped option from correctness + response time, queue the
    /// FSRS review to the phone, drop the card locally, then flip to the next
    /// question. A second tap while feedback shows is ignored.
    func choose(_ index: Int) {
        guard selectedIndex == nil, let question = currentQuestion,
              let id = currentID, var snap = snapshot else { return }
        selectedIndex = index
        let correct = index == question.correctIndex
        streak = correct ? streak + 1 : 0
        // why: a light tap on a wrong pick only — a gentle wake-up cue, not the
        // punishing `.failure`/`.retry`; correct picks stay haptic-free.
        if !correct { WKInterfaceDevice.current().play(.click) }

        let elapsedMs = Int(Date().timeIntervalSince(questionShownAt) * 1000)
        let optionChars = question.options.joined().count
        let rating = WatchGrading.rating(correct: correct, elapsedMs: elapsedMs,
                                         optionChars: optionChars)

        connectivity.send(WatchAnswerEvent(cardId: id, rating: rating, date: Date()))
        snap.answeredCardIDs.append(id)
        snapshot = snap
        WatchSnapshotStore.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
        answeredCount += 1

        // why: linger longer on a wrong pick so the green-highlighted correct
        // tile has time to register before the next question.
        let delay = correct ? 900 : 2000
        autoAdvance = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(delay))
            guard !Task.isCancelled else { return }
            self?.advance()
        }
    }

    func endSession() {
        autoAdvance?.cancel()
        sessionPresented = false
        currentID = nil
        currentQuestion = nil
        queue = []
    }

    private func advance() {
        autoAdvance?.cancel()
        if let id = currentID, let index = queue.firstIndex(of: id) {
            queue.remove(at: index)
        }
        currentID = queue.first
        makeQuestionForCurrent()
    }

    /// Build the question for the current card and (re)start the response clock.
    private func makeQuestionForCurrent() {
        selectedIndex = nil
        guard let entry = currentEntry else {
            currentQuestion = nil
            return
        }
        currentQuestion = WatchPracticeGenerator.makeQuestion(promptEntry: entry, using: &rng)
        questionShownAt = Date()
    }
}
