import SwiftUI
import SprossKern

/// Grading of the atlas drill — and the run-through hooks that drive it. State
/// lives on CountryDrillView; split out purely for file size, the way both
/// sibling drills split theirs off.
///
/// Kern hands over every form it accepts (`CountryDrillTask.accepted`: each
/// spelling of each valid answer, and for the union kinds every language a
/// country speaks). This grades against that set through the STRICT drill
/// normalizer — no article leniency, one slip per word — so "die Schweiz" and
/// "Schweiz" both stand where the atlas authored both, and a neighboring
/// country's name never passes for this one.
extension CountryDrillView {

    /// "Aufdecken" on an empty field: the CARD carries the answer, and the
    /// question books as a miss. The field stays empty — it has nothing of the
    /// learner's to show, and typing the answer in for them would put the same
    /// word on screen twice.
    func reveal() {
        Pronouncer.shared.stop()
        DLSound.reveal()
        withAnimation { feedback = .revealed }
    }

    /// Take a name typed out exactly right as the answer, without a "Prüfen"
    /// tap: the field turns green with its checkmark and the card flips a beat
    /// later. The review session's rule (`SessionView.approveWhenTyped`) and
    /// the one a learner arrives here already knowing — writing the word out IS
    /// the answer.
    ///
    /// EXACT only, where Return still forgives a slip: the typo budget would
    /// fire a letter early and grade the name before it was finished, and a real
    /// slip has to pause on its correction anyway. Backing out of a finished
    /// name takes the green with it, so typing past the answer never books it.
    func approveWhenTyped(_ task: CountryDrillTask) {
        guard feedback == .neutral || feedback == .correct else { return }
        autoAdvance?.cancel()
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, case .clean = verdict(trimmed, task: task) else {
            if feedback == .correct { withAnimation { feedback = .neutral } }
            return
        }
        if feedback != .correct { DLSound.correct() }
        withAnimation { feedback = .correct }
        AutoAdvance.scheduleLive(&autoAdvance) { advance(correct: true, clean: true) }
    }

    func submit(_ task: CountryDrillTask) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        Pronouncer.shared.stop()
        switch verdict(trimmed, task: task) {
        case .clean:
            feedback = .correct
            DLSound.correct()
            AutoAdvance.scheduleExplicit(&autoAdvance) { advance(correct: true, clean: true) }
        case .typo(let corrected):
            // why: no auto-advance on a slip — the pause shows the proper
            // spelling, and "Weiter" then books it amber.
            feedback = .almost(correctForm: corrected, reason: .typo)
            DLSound.correct()
            typoCorrection = corrected
            // why: a pause that waits for a tap must not hold the keyboard —
            // it covers the button the pause is waiting for.
            answerFocused = false
        case .wrong:
            feedback = .revealed
            DLSound.wrong()
        }
    }

    /// What a typed answer earns; what the ramp does with it is `DrillProgression.step`.
    private enum Verdict {
        case clean
        case typo(String)
        case wrong
    }

    private func verdict(_ trimmed: String, task: CountryDrillTask) -> Verdict {
        guard let normalizer else {
            // Previews: plain case/punctuation-insensitive comparison.
            let typed = fallbackNormalized(trimmed)
            return task.accepted.contains { fallbackNormalized($0) == typed } ? .clean : .wrong
        }
        switch onEnum(of: normalizer.evaluate(input: trimmed, card: gradingCard(task))) {
        case .exact:
            return .clean
        case .typo(let typo):
            return .typo(typo.corrected)
        // Graded against one synthetic card, never the catalog join — the
        // other-word verdict cannot arise here.
        case .otherWord, .wrong:
            return .wrong
        }
    }

    /// The strict drill normalizer, built exactly as the slot drill's is: no
    /// article leniency (the atlas authors the article in the name and the bare
    /// form beside it), one slip per word.
    private var normalizer: AnswerNormalizer? {
        model.languageInfo(answerLanguage)
            .map { AnswerNormalizer(answerLanguage: $0, articleLeniency: false,
                                    maxTyposPerWord: KotlinInt(int: 1)) }
    }

    /// The accepted forms wrapped as a synthetic card for kern's evaluate — the
    /// non-verb kind keeps the verb-prefix option off, and an empty
    /// `baseAccepted` skips the feminine demotion.
    private func gradingCard(_ task: CountryDrillTask) -> Card {
        let side = Realization(lang: answerLanguage,
                               text: task.accepted.first ?? task.display,
                               synonyms: Array(task.accepted.dropFirst()),
                               variants: [],
                               grammar: [:],
                               note: nil)
        return Card(id: "atlas", kind: .noun, area: "atlas", emoji: nil,
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

#if DEBUG
/// Run-through hooks (UserDefaults launch arguments), in the shape both sibling
/// drills already use: they drive the screen so a screenshot run needs no thumb.
extension CountryDrillView {

    func uitestStart() {
        let defaults = UserDefaults.standard
        if let prefill = UITestAnswer.prefill { input = prefill }
        if let task = current { UITestAnswer.submitAfterBeat { submit(task) } }
        // `-uitest-streak N`: a run mid-streak, which a screenshot run has no
        // thumb to reach.
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 {
            streak = preset
            bestStreak = max(preset, 12)
            doneCount = preset + 6
        }
        // `-uitest-close 1`: leave the way the ✕ leaves, so the tile the run
        // drops on the page behind it can be photographed.
        if defaults.bool(forKey: "uitest-close") {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(400))
                closeRun()
            }
        }
    }
}
#endif
