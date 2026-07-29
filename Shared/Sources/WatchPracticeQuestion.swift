import Foundation

/// One generated multiple-choice question for the watch practice loop.
/// Pure value type — no scheduling, no FSRS. `correctIndex` points into the
/// already-shuffled `options`. The view renders `promptEntry` role-aware
/// (see `WatchPracticeGenerator`).
struct WatchPracticeQuestion: Equatable {
    let promptCardID: String
    let promptEntry: WatchSnapshot.Entry
    let options: [String]
    let correctIndex: Int
}

/// Assembles the question for a FIXED prompt card (the queued card being
/// drained). WHICH words may stand next to the answer is decided on the phone
/// (kern `MultipleChoice`, shipped per entry as `distractors`) — the watch only
/// picks three of them and shuffles, so every tile sits on one side by
/// construction:
/// - recognize: prompt the target `promptForm`, match the SOURCE meaning.
/// - produce:   prompt the SOURCE meaning, match the TARGET word.
enum WatchPracticeGenerator {

    /// Tiles per question: the answer plus three distractors.
    static let optionCount = 4

    /// The text the learner must MATCH for this entry's role (the option side),
    /// in the form the phone offers it: `optionForm` where a word would otherwise
    /// be told apart from its company by a class marker rather than by meaning.
    static func answerText(for entry: WatchSnapshot.Entry) -> String {
        entry.optionForm ?? (entry.isRecognize ? entry.sourceText : entry.targetText)
    }

    /// A question, or `nil` when the phone shipped no distractor for the entry
    /// (a lone card, or a pre-v3 snapshot).
    static func makeQuestion(promptEntry prompt: WatchSnapshot.Entry,
                             using rng: inout some RandomNumberGenerator) -> WatchPracticeQuestion? {
        var shortlist = prompt.distractors ?? []
        guard !shortlist.isEmpty else { return nil }
        // why: the phone ranks a handful by shape — jitter within it so the
        // same card doesn't always draw the same three tiles.
        shortlist.shuffle(using: &rng)

        let correct = answerText(for: prompt)
        var options = Array(shortlist.prefix(optionCount - 1)) + [correct]
        options.shuffle(using: &rng)
        let correctIndex = options.firstIndex(of: correct)!
        return WatchPracticeQuestion(promptCardID: prompt.cardId, promptEntry: prompt,
                                     options: options, correctIndex: correctIndex)
    }
}
