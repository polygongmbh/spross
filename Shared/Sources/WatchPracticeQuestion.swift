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

/// Builds a question for a FIXED prompt card (the queued card being drained),
/// role-aware: the option side matches the phone-scheduled `nextRole`, so a
/// recognition tap grades the same direction the phone expects.
/// - recognize: prompt the target `promptForm`, match the SOURCE meaning.
/// - produce:   prompt the SOURCE meaning, match the TARGET word.
enum WatchPracticeGenerator {

    /// The text the learner must MATCH for this entry's role (the option side).
    static func answerText(for entry: WatchSnapshot.Entry) -> String {
        entry.isRecognize ? entry.sourceText : entry.targetText
    }

    /// A question, or `nil` when the pool holds no distinct distractor.
    /// Up to four options; distractors are deduped case-insensitively and
    /// ranked by SHAPE similarity to the answer (character length + number of
    /// space/hyphen parts) so option length can't give the answer away, then
    /// lightly shuffled within the closest handful.
    static func makeQuestion(promptEntry prompt: WatchSnapshot.Entry,
                             pool: [WatchSnapshot.Entry],
                             using rng: inout some RandomNumberGenerator) -> WatchPracticeQuestion? {
        let correct = answerText(for: prompt)
        let correctKey = correct.lowercased()

        // Distractor candidates from every OTHER entry's answer text (same
        // role side), unique and distinct from the correct answer.
        var seen: Set<String> = [correctKey]
        var candidates: [String] = []
        for entry in pool where entry.cardId != prompt.cardId {
            let candidate = answerText(for: entry)
            if seen.insert(candidate.lowercased()).inserted { candidates.append(candidate) }
        }
        guard !candidates.isEmpty else { return nil }  // deck of identical answers

        // why: a lone long / multi-part option is a visual tell — keep the
        // three distractors close to the answer's shape, then jitter within
        // the closest six so it isn't deterministic.
        var shortlist = candidates
            .sorted { shapeDistance($0, to: correct) < shapeDistance($1, to: correct) }
            .prefix(6)
            .map { $0 }
        shortlist.shuffle(using: &rng)

        var options = Array(shortlist.prefix(3)) + [correct]
        options.shuffle(using: &rng)
        let correctIndex = options.firstIndex(of: correct)!
        return WatchPracticeQuestion(promptCardID: prompt.cardId, promptEntry: prompt,
                                     options: options, correctIndex: correctIndex)
    }

    /// Shape distance: character-length gap plus a heavy penalty when the
    /// number of space/hyphen-separated parts differs (keeps multi-part words
    /// together so a compound isn't obvious among single words).
    static func shapeDistance(_ a: String, to b: String) -> Int {
        abs(a.count - b.count) + abs(partCount(a) - partCount(b)) * 6
    }

    private static func partCount(_ s: String) -> Int {
        s.split(whereSeparator: { $0 == " " || $0 == "-" }).count
    }
}
