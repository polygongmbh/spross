import Foundation
import Observation
import WatchKit
import DuoKern

/// Endless multiple-choice practice over the on-watch vocab. PURE PRACTICE:
/// deliberately holds none of `WatchModel`'s phone-sync dependencies
/// (connectivity / snapshot store / WidgetCenter), so it can never touch FSRS
/// or send an answer event. Built fresh per run from a snapshot, then dropped.
@MainActor
@Observable
final class WatchPracticeModel {

    private let cards: [WatchSnapshot.Card]
    let direction: Direction

    private(set) var question: WatchPracticeQuestion?
    /// The tapped option index; non-nil freezes the tiles into feedback state.
    private(set) var selectedIndex: Int?
    private(set) var streak = 0
    private(set) var bestStreak = 0
    private(set) var answeredCount = 0
    private(set) var correctCount = 0
    var showingSummary = false

    private var previousCardID: String?
    private var rng = SystemRandomNumberGenerator()
    private var autoAdvance: Task<Void, Never>?

    init(snapshot: WatchSnapshot) {
        self.cards = snapshot.cards
        self.direction = snapshot.direction
    }

    var hasEnoughVocab: Bool { cards.count >= 2 }
    var feedbackVisible: Bool { selectedIndex != nil }
    func isCorrect(_ index: Int) -> Bool { index == question?.correctIndex }

    func start() {
        question = WatchPracticeGenerator.makeQuestion(cards: cards, direction: direction,
                                                       avoiding: nil, using: &rng)
        previousCardID = question?.promptCardID
        selectedIndex = nil
    }

    /// Lock the tapped option, score it, and schedule the flip to the next
    /// question. A second tap while feedback shows is ignored.
    func choose(_ index: Int) {
        guard selectedIndex == nil, let question else { return }
        selectedIndex = index
        let correct = index == question.correctIndex
        answeredCount += 1
        if correct {
            correctCount += 1
            streak += 1
            bestStreak = max(bestStreak, streak)
        } else {
            streak = 0
        }
        // why: soft haptic only — never the punishing `.failure`/`.retry`.
        WKInterfaceDevice.current().play(correct ? .success : .click)
        autoAdvance = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(900))
            guard !Task.isCancelled else { return }
            advance()
        }
    }

    func advance() {
        autoAdvance?.cancel()
        question = WatchPracticeGenerator.makeQuestion(cards: cards, direction: direction,
                                                       avoiding: previousCardID, using: &rng)
        previousCardID = question?.promptCardID ?? previousCardID
        selectedIndex = nil
    }

    func end() {
        autoAdvance?.cancel()
    }
}
