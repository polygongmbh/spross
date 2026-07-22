import Foundation
import Observation
import WatchKit

/// Endless multiple-choice practice over the on-watch vocab. PURE PRACTICE:
/// deliberately holds none of `WatchModel`'s phone-sync dependencies
/// (connectivity / snapshot store / WidgetCenter), so it can never touch FSRS
/// or send an answer event. Built fresh per run from a snapshot, then dropped.
@MainActor
@Observable
final class WatchPracticeModel {

    private let entries: [WatchSnapshot.Entry]

    private(set) var question: WatchPracticeQuestion?
    /// The tapped option index; non-nil freezes the tiles into feedback state.
    private(set) var selectedIndex: Int?
    private(set) var streak = 0

    private var previousCardID: String?
    private var rng = SystemRandomNumberGenerator()
    private var autoAdvance: Task<Void, Never>?

    init(snapshot: WatchSnapshot) {
        self.entries = snapshot.entries
    }

    var hasEnoughVocab: Bool { entries.count >= 2 }

    func start() {
        question = WatchPracticeGenerator.makeQuestion(entries: entries,
                                                       avoiding: nil, using: &rng)
        previousCardID = question?.promptCardID
        selectedIndex = nil
        #if DEBUG
        // `-uitest-streak N` presets the streak for screenshot verification.
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-uitest-streak"), i + 1 < args.count,
           let n = Int(args[i + 1]) {
            streak = n
        }
        #endif
    }

    /// Lock the tapped option, score it, and schedule the flip to the next
    /// question. A second tap while feedback shows is ignored.
    func choose(_ index: Int) {
        guard selectedIndex == nil, let question else { return }
        selectedIndex = index
        let correct = index == question.correctIndex
        streak = correct ? streak + 1 : 0
        // why: a light tap on a wrong pick only — a gentle wake-up cue, not the
        // punishing `.failure`/`.retry`; correct picks stay haptic-free.
        if !correct { WKInterfaceDevice.current().play(.click) }
        // why: linger longer on a wrong pick so the revealed correct answer
        // has time to register before the next question.
        let delay = correct ? 900 : 2000
        autoAdvance = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(delay))
            guard !Task.isCancelled else { return }
            advance()
        }
    }

    func advance() {
        autoAdvance?.cancel()
        question = WatchPracticeGenerator.makeQuestion(entries: entries,
                                                       avoiding: previousCardID, using: &rng)
        previousCardID = question?.promptCardID ?? previousCardID
        selectedIndex = nil
    }

    func end() {
        autoAdvance?.cancel()
    }
}
