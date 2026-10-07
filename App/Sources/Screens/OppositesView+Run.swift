import SwiftUI
import SprossKern

/// The screen content of the opposites drill, and what the shared run driver
/// (`DrillRunning`) needs to drive it: kern's `OppositesRun`, its intent
/// vocabulary, and a close that files the ladder the run climbed. State lives
/// on OppositesView; split out for file size, the way the word scramble splits.
extension OppositesView: DrillRunning {

    // MARK: - What is on screen

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                if let task = current, let question = run.question, let controls = run.controls {
                    QuestionStage(key: question.key) {
                        QuestionCardView(question: question, voice: model.cardVoice)
                    }
                    typedControls(task, controls)
                }
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    private func typedControls(_ task: OppositesTask, _ controls: AnswerControls) -> some View {
        AnswerArea(controls: controls,
                   text: $input,
                   placeholder: answerPlaceholder(task.language),
                   focus: $answerFocused,
                   correctionVoice: .init(
                       pronounce: { model.pronounceAction(for: $0, lang: task.language) },
                       isPlaying: { model.isPronouncing($0, lang: task.language) }),
                   nextLocale: model.targetChromeLocale,
                   actions: answerActions)
    }

    // MARK: - The machine under this drill

    func reduce(_ run: OppositesRunState,
                _ intent: OppositesIntent) -> DrillStep<OppositesRunState> {
        let reduction = OppositesRun.shared.reduce(state: run, intent: intent, rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    func typedMove(_ text: String) -> OppositesIntent? {
        OppositesIntent.InputChanged(text: text)
    }

    func submitMove(_ text: String) -> OppositesIntent? {
        OppositesIntent.Submit(text: text)
    }

    var confirmMove: OppositesIntent { OppositesIntent.ConfirmPending.shared }

    var advanceMove: OppositesIntent { OppositesIntent.AdvanceElapsed.shared }

    var keepPracticingMove: OppositesIntent { OppositesIntent.KeepPracticing.shared }

    var resultTitle: LocalizedStringKey { "trainer.drill.opposites" }

    var voiceModel: AppModel? { model }

    // MARK: - Close → back to the hub that opened it

    var lastRunKey: String { DrillSuggestion.shared.lastRunKey(drill: .opposites, language: language) }

    func closing() -> DrillClose<OppositesRunState> {
        let closed = OppositesRun.shared.close(state: run)
        TrainerProgress.bookCleared(closed.clearedSprossen, for: storageKey)
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects)
    }
}

#if DEBUG
extension OppositesView {

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
