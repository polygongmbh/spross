import SwiftUI
import SprossKern

/// The screen content of the word scramble, and what the shared run driver
/// (`DrillRunning`) needs to drive it: kern's `WordScrambleRun`, its intent
/// vocabulary, and a close that files the ladder the run climbed. State lives
/// on WordScrambleView; split out purely for file size, the way the letter
/// drill splits its own off.
///
/// Grading itself is `WordScrambleRun.grade`'s, against every form the card
/// authors — its `teaches` and `accepts` are real spellings of the same knowledge.
/// All this side owes is the STRICT drill normalizer, resolved when the run opens.
extension WordScrambleView: DrillRunning {

    var appModel: AppModel? { model }

    // MARK: - What is on screen

    var drillContent: some View {
        questionPage { question in
            QuestionCardView(question: question, voice: model.cardVoice,
                             promptLabel: current.map { promptLabel($0.scrambled) })
        } area: { controls in
            if let task = current { typedControls(task, controls) }
        }
    }

    /// Writing the word out is the answer, so every keystroke is offered to
    /// kern — the typed drills' rule, and the only thing this drill parameterizes
    /// about the shared controls beyond the voice its correction box speaks in.
    private func typedControls(_ task: WordScrambleTask, _ controls: AnswerControls) -> some View {
        AnswerArea(driver: self, controls: controls,
                   placeholder: answerPlaceholder(task.language),
                   focus: $answerFocused,
                   correctionVoice: .init(
                       pronounce: { model.pronounceAction(for: $0, lang: task.language) },
                       isPlaying: { model.isPronouncing($0, lang: task.language) }),
                   nextLocale: model.targetChromeLocale)
    }

    // MARK: - The machine under this drill

    func reduce(_ run: WordScrambleRunState,
                _ intent: WordScrambleIntent) -> DrillStep<WordScrambleRunState> {
        let reduction = WordScrambleRun.shared.reduce(state: run, intent: intent, rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    func typedMove(_ text: String) -> WordScrambleIntent? {
        WordScrambleIntent.InputChanged(text: text)
    }

    func submitMove(_ text: String) -> WordScrambleIntent? {
        WordScrambleIntent.Submit(text: text)
    }

    var confirmMove: WordScrambleIntent { WordScrambleIntent.ConfirmPending.shared }

    var advanceMove: WordScrambleIntent { WordScrambleIntent.AdvanceElapsed.shared }

    var keepPracticingMove: WordScrambleIntent { WordScrambleIntent.KeepPracticing.shared }

    var resultTitle: LocalizedStringKey { "trainer.drill.wordScramble" }

    var voiceModel: AppModel? { model }

    // MARK: - Close → back to the hub that opened it


    func closing() -> DrillClose<WordScrambleRunState> {
        let closed = WordScrambleRun.shared.close(state: run)
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects,
                          bookings: closed.bookings(target: language))
    }
}

#if DEBUG
extension WordScrambleView {

    func seedAnswerStreak(_ answerStreak: Int) {
        run = run.doCopy(config: run.config, task: run.task, index: run.index,
                         sprosse: run.sprosse, bestSprosse: run.bestSprosse,
                         winsAtSprosse: run.winsAtSprosse,
                         clearedSprossen: run.clearedSprossen,
                         core: run.core.doCopy(done: Int32(answerStreak + 6),
                                               answerStreak: Int32(answerStreak),
                                               bestAnswerStreak: Int32(max(answerStreak, 12)),
                                               missRun: run.core.missRun,
                                               outcomes: run.core.outcomes,
                                               solved: run.core.solved,
                                               slipped: run.core.slipped,
                                               solvedClean: run.core.solvedClean,
                                               pacing: run.core.pacing),
                         feedback: run.feedback, finished: run.finished)
    }
}
#endif
