import SwiftUI
import SprossKern

/// The screen content of the word scramble, and what the shared run driver
/// (`DrillRunning`) needs to drive it: kern's `WordScrambleRun`, its intent
/// vocabulary, and a close that files the ladder the run climbed. State lives
/// on WordScrambleView; split out purely for file size, the way the letter
/// drill splits its own off.
///
/// Grading itself is `WordScrambleRun.grade`'s, against every form the card
/// authors — its synonyms and variants are real spellings of the same knowledge.
/// All this side owes is the STRICT drill normalizer, resolved when the run opens.
extension WordScrambleView: DrillRunning {

    // MARK: - What is on screen

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                DrillStreakLine(level: Text("trainer.sprosse \(Int(run.level).formatted())"),
                                streak: Int(run.streak), bestStreak: Int(run.bestStreak))
                if let task = current {
                    // ZStack so the outgoing and incoming word overlap during
                    // the flip; .id gives each position its identity.
                    ZStack {
                        DrillPromptCard(prompt: promptText(task.scrambled),
                                          promptLabel: promptLabel(task.scrambled),
                                          size: .word,
                                          answer: task.display,
                                          language: task.language,
                                          gloss: task.gloss,
                                          revealed: run.showsAnswer,
                                          pronounce: model.pronounceAction(for: task.display,
                                                                           lang: task.language),
                                          isPlaying: model.isPronouncing(task.display,
                                                                         lang: task.language))
                            .id(run.index)
                            .transition(reduceMotion ? .opacity : .cardFlip)
                    }
                    typedControls(task)
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
    private func typedControls(_ task: WordScrambleTask) -> some View {
        DrillAnswerControls(text: $input,
                            feedback: feedback,
                            placeholder: answerPlaceholder(task.language),
                            focus: $answerFocused,
                            // Tap-to-replay for the correction box — the form
                            // the slip owed, said in the drilled language.
                            correctionVoice: .init(
                                pronounce: { model.pronounceAction(for: $0, lang: task.language) },
                                isPlaying: { model.isPronouncing($0, lang: task.language) }),
                            onType: { typed() },
                            onSubmit: { submit() },
                            onConfirm: { confirm() },
                            onStop: run.offersFinish ? { closeRun() } : nil)
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

    var resultTitle: LocalizedStringKey { "trainer.drill.wordScramble" }

    func silence() { Pronouncer.shared.stop() }

    // MARK: - Close → back to the hub that opened it

    /// An untouched run leaves nothing to report, and no record line either —
    /// this drill keeps no streak record. What it DOES file is the ladder:
    /// the next run opens on the lowest Sprosse the mask does not hold, so a
    /// Sprosse climbed clean is never asked for twice.
    func closing() -> DrillClose<WordScrambleRunState> {
        let closed = WordScrambleRun.shared.close(state: run)
        TrainerProgress.bookCleared(closed.clearedSprossen, for: storageKey)
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects)
    }
}

#if DEBUG
extension WordScrambleView {

    func seedStreak(_ streak: Int) {
        run = run.doCopy(config: run.config, task: run.task, index: run.index,
                         level: run.level, bestLevel: run.bestLevel,
                         winsAtLevel: run.winsAtLevel,
                         clearedSprossen: run.clearedSprossen, blemished: run.blemished,
                         core: run.core.doCopy(done: Int32(streak + 6),
                                               streak: Int32(streak),
                                               bestStreak: Int32(max(streak, 12)),
                                               missRun: run.core.missRun,
                                               outcomes: run.core.outcomes,
                                               solved: run.core.solved),
                         feedback: run.feedback, finished: run.finished)
    }
}
#endif
