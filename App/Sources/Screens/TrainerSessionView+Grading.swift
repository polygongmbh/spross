import SwiftUI
import SprossKern

/// Typed-answer grading of TrainerSessionView (run streak only, no FSRS).
/// Routed through Kern's AnswerNormalizer so drills get the same
/// Damerau-Levenshtein typo tolerance as vocab reviews. State lives on
/// TrainerSessionView; split out purely for file size.
extension TrainerSessionView {

    func submit() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        switch grade(trimmed) {
        case .exact:
            feedback = .correct
            DLSound.correct()
            // A hint-assisted answer stays amber (no level progress).
            let segment: SessionOutcome = hintUsed ? .tough : .right
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(1200))
                guard !Task.isCancelled else { return }
                advance(correct: true, segment: segment)
            }
        case .typo(let corrected):
            // why: no auto-advance on a typo — the pause shows the proper
            // spelling; "Weiter" then books it amber (no level progress).
            feedback = .correct
            DLSound.correct()
            typoCorrection = corrected
        case .wrong:
            feedback = .revealed(correctAnswer: current.display)
            DLSound.wrong()
        }
    }

    /// Drill grading verdict (mirrors the vocab Match ladder).
    private enum Grade {
        case exact
        case typo(String)
        case wrong
    }

    /// Kern-graded against EVERY accepted variant, best result wins
    /// (exact > typo > wrong) — the same Damerau-Levenshtein typo budget as
    /// vocab reviews. The budget only ever ACCEPTS a near-miss of an accepted
    /// form; it never converts one number into another (distinct number words
    /// sit ≥ 2 edits apart where the budget is 1, short words get budget 0).
    private func grade(_ trimmed: String) -> Grade {
        guard let normalizer else {
            // Previews: plain case/punctuation-insensitive comparison.
            let typed = fallbackNormalized(trimmed)
            return current.accepted.contains { fallbackNormalized($0) == typed }
                ? .exact : .wrong
        }
        switch onEnum(of: normalizer.evaluate(input: trimmed, card: gradingCard())) {
        case .exact:
            return .exact
        case .typo(let typo):
            return .typo(typo.corrected)
        case .wrong:
            return .wrong
        }
    }

    /// The accepted variants wrapped as a synthetic card for Kern's evaluate:
    /// a non-verb kind and empty grammar keep the article and verb-prefix
    /// options OFF — drills get exactly the typo budget, nothing more.
    private func gradingCard() -> Card {
        let accepted = current.accepted
        let side = Realization(lang: language,
                               text: accepted.first ?? current.display,
                               synonyms: Array(accepted.dropFirst()),
                               variants: [],
                               grammar: [:],
                               note: nil)
        return Card(id: "drill", kind: .noun, area: "drill", emoji: nil,
                    seedIndex: 0, components: [], feminineOf: nil,
                    source: side, target: side, promptFeminineMarker: false)
    }

    private func fallbackNormalized(_ raw: String) -> String {
        raw.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}
