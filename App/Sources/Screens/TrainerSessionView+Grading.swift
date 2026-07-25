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
    /// (exact > typo > wrong). Drills cap the typo budget at 1 and digit
    /// forms grade exact-only, so a slip is forgiven but no German number
    /// can ever pass for another (kern's pairwise guard proves it; sw/uk
    /// carry gated near-twin pairs — see docs/backlog.md).
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

    /// The accepted variants wrapped as a synthetic card for Kern's evaluate.
    /// Strictness comes from the normalizer construction (articleLeniency
    /// false, budget cap 1 — TrainerHubView); the non-verb kind keeps the
    /// verb-prefix option off and empty baseAccepted skips feminine demotion.
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
                    baseAccepted: [], source: side, target: side,
                    promptFeminineMarker: false, promptAmbiguous: false)
    }

    private func fallbackNormalized(_ raw: String) -> String {
        raw.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}
