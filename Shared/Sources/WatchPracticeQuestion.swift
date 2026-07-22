import Foundation

/// One generated multiple-choice question for the watch "Üben" practice mode.
/// Pure value type — no scheduling, no FSRS. `correctIndex` points into the
/// already-shuffled `options`.
struct WatchPracticeQuestion: Equatable {
    let promptCardID: String
    let promptEntry: WatchSnapshot.Entry
    let options: [String]
    let correctIndex: Int
}

/// Builds practice questions from the on-watch vocab. Both sides arrive
/// pre-resolved in the snapshot (no direction concept): the prompt is the
/// TARGET side, the matched answer the SOURCE meaning.
enum WatchPracticeGenerator {

    /// The prompt shown to the learner.
    static func promptText(for entry: WatchSnapshot.Entry) -> String {
        entry.targetText
    }

    /// The text the learner must MATCH (the known-language meaning).
    static func answerText(for entry: WatchSnapshot.Entry) -> String {
        entry.sourceText
    }

    /// A practice question, or `nil` when the deck has fewer than two distinct
    /// answers (caller shows the "learn on the iPhone first" fallback).
    /// Up to four options; distractors are deduped case-insensitively against
    /// the correct answer and each other. `avoiding` keeps the same prompt
    /// card from repeating back-to-back.
    static func makeQuestion(entries: [WatchSnapshot.Entry],
                             avoiding previousCardID: String?,
                             using rng: inout some RandomNumberGenerator) -> WatchPracticeQuestion? {
        guard entries.count >= 2 else { return nil }

        // why: don't repeat the previous prompt card — resample once (cheap,
        // matches the phone drill's single-resample behaviour).
        var prompt = entries.randomElement(using: &rng)!
        if prompt.cardId == previousCardID {
            prompt = entries.randomElement(using: &rng)!
        }

        let correct = answerText(for: prompt)
        let correctKey = correct.lowercased()

        // Distractors from every OTHER entry's answer text, unique and distinct
        // from the correct answer (case-insensitive).
        var seen: Set<String> = [correctKey]
        var pool: [String] = []
        for entry in entries where entry.cardId != prompt.cardId {
            let candidate = answerText(for: entry)
            let key = candidate.lowercased()
            if seen.insert(key).inserted { pool.append(candidate) }
        }
        pool.shuffle(using: &rng)

        var options = Array(pool.prefix(3)) + [correct]
        guard options.count >= 2 else { return nil }  // deck of identical answers
        options.shuffle(using: &rng)

        let correctIndex = options.firstIndex(of: correct)!
        return WatchPracticeQuestion(promptCardID: prompt.cardId, promptEntry: prompt,
                                     options: options, correctIndex: correctIndex)
    }
}
