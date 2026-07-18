import Foundation
import DuoKern

/// One generated multiple-choice question for the watch "Üben" practice mode.
/// Pure value type — no scheduling, no FSRS. `correctIndex` points into the
/// already-shuffled `options`.
struct WatchPracticeQuestion: Equatable {
    let promptCardID: String
    let promptCard: WatchSnapshot.Card
    let options: [String]
    let correctIndex: Int
}

/// Builds practice questions from the on-watch vocab (`WatchSnapshot.cards`).
/// Direction semantics mirror `WatchReviewView`: `.deToTarget` prompts the
/// German side and answers with the translation; `.targetToDe` is the reverse.
enum WatchPracticeGenerator {

    /// The prompt shown to the learner (opposite side of the answer).
    static func promptText(for card: WatchSnapshot.Card, direction: Direction) -> String {
        direction == .targetToDe ? card.translation : card.german
    }

    /// The text the learner must MATCH. The German side is compared/deduped
    /// on the bare noun (the article is decorative), so two same-noun cards
    /// never both appear as options.
    static func answerText(for card: WatchSnapshot.Card, direction: Direction) -> String {
        direction == .targetToDe ? card.german : card.translation
    }

    /// A practice question, or `nil` when the deck has fewer than two distinct
    /// answers (caller shows the "learn on the iPhone first" fallback).
    /// Up to four options; distractors are deduped case-insensitively against
    /// the correct answer and each other. `avoiding` keeps the same prompt
    /// card from repeating back-to-back.
    static func makeQuestion(cards: [WatchSnapshot.Card],
                             direction: Direction,
                             avoiding previousCardID: String?,
                             using rng: inout some RandomNumberGenerator) -> WatchPracticeQuestion? {
        guard cards.count >= 2 else { return nil }

        // why: don't repeat the previous prompt card — resample once (cheap,
        // matches the phone drill's single-resample behaviour).
        var prompt = cards.randomElement(using: &rng)!
        if prompt.id == previousCardID {
            prompt = cards.randomElement(using: &rng)!
        }

        let correct = answerText(for: prompt, direction: direction)
        let correctKey = correct.lowercased()

        // Distractors from every OTHER card's answer text, unique and distinct
        // from the correct answer (case-insensitive).
        var seen: Set<String> = [correctKey]
        var pool: [String] = []
        for card in cards where card.id != prompt.id {
            let candidate = answerText(for: card, direction: direction)
            let key = candidate.lowercased()
            if seen.insert(key).inserted { pool.append(candidate) }
        }
        pool.shuffle(using: &rng)

        var options = Array(pool.prefix(3)) + [correct]
        guard options.count >= 2 else { return nil }  // deck of identical answers
        options.shuffle(using: &rng)

        let correctIndex = options.firstIndex(of: correct)!
        return WatchPracticeQuestion(promptCardID: prompt.id, promptCard: prompt,
                                     options: options, correctIndex: correctIndex)
    }
}
