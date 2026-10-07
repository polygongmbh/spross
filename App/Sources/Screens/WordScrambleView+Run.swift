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

    // MARK: - What is on screen

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                if let task = current, let question = run.question, let controls = run.controls {
                    // ZStack so the outgoing and incoming word overlap during
                    // the flip; .id gives each position its identity.
                    ZStack {
                        QuestionCardView(question: question, voice: model.cardVoice,
                                         promptLabel: promptLabel(task.scrambled))
                            .id(question.key)
                            .transition(reduceMotion ? .opacity : .cardFlip)
                    }
                    typedControls(task, controls)
                }
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// Writing the word out is the answer, so every keystroke is offered to
    /// kern — the typed drills' rule, and the only thing this drill parameterizes
    /// about the shared controls beyond the voice its correction box speaks in.
    private func typedControls(_ task: WordScrambleTask, _ controls: AnswerControls) -> some View {
        AnswerArea(controls: controls,
                   text: $input,
                   placeholder: answerPlaceholder(task.language),
                   focus: $answerFocused,
                   correctionVoice: .init(
                       pronounce: { model.pronounceAction(for: $0, lang: task.language) },
                       isPlaying: { model.isPronouncing($0, lang: task.language) }),
                   actions: answerActions)
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

    /// An untouched run leaves nothing to report, and no record line either —
    /// this drill keeps no streak record. What it DOES file is the ladder:
    /// the next run passes each Sprosse the mask holds on one clean answer.
    var lastRunKey: String { DrillSuggestion.shared.lastRunKey(drill: .wordScramble, language: language) }

    func closing() -> DrillClose<WordScrambleRunState> {
        let closed = WordScrambleRun.shared.close(state: run)
        TrainerProgress.bookCleared(closed.clearedSprossen, for: storageKey)
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects)
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
