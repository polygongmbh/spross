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
            AutoAdvance.scheduleExplicit(&autoAdvance) { advance(correct: true, segment: segment) }
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

    /// Take a word typed out exactly right as the answer, without a "Prüfen"
    /// tap — same rule vocab review's produce field uses ("Finishing the word
    /// IS the answer"). Drills have no reveal-then-retype step (`.revealed`
    /// locks the field), so the guard only needs to keep clear of an in-flight
    /// typo pause.
    func approveWhenTyped() {
        guard typoCorrection == nil else { return }
        if case .revealed = feedback { return }
        autoAdvance?.cancel()
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, case .exact = grade(trimmed) else {
            if feedback == .correct { withAnimation { feedback = .neutral } }
            return
        }
        if feedback != .correct { DLSound.correct() }
        withAnimation { feedback = .correct }
        let segment: SessionOutcome = hintUsed ? .tough : .right
        AutoAdvance.scheduleLive(&autoAdvance) { advance(correct: true, segment: segment) }
    }

    /// Drill grading verdict (mirrors the vocab Match ladder).
    private enum Grade {
        case exact
        case typo(String)
        case wrong
    }

    /// Kern-graded against EVERY accepted variant, best result wins
    /// (exact > typo > wrong). Drills grade word by word — one slip per word,
    /// none inside a digit — so a sentence may fumble while no German number
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
        // Drills grade against one synthetic card, never the catalog join —
        // the other-word verdict cannot arise here.
        case .otherWord, .wrong:
            return .wrong
        }
    }

    /// The accepted variants wrapped as a synthetic card for Kern's evaluate.
    /// Strictness comes from the normalizer construction (articleLeniency
    /// false, one slip per word — TrainerHubView); the non-verb kind keeps the
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
