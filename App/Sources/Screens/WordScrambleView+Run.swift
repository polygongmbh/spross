import SwiftUI
import SprossKern

/// Screen content and RUN half of the word scramble: the card, the field, how an
/// event reaches kern and what a close leaves behind. State lives on
/// WordScrambleView; split out purely for file size, the way the letter drill
/// splits its own off.
///
/// Grading itself is `WordScrambleRun.grade`'s, against every form the card
/// authors — its synonyms and variants are real spellings of the same knowledge.
/// All this side owes is the STRICT drill normalizer, resolved when the run opens.
extension WordScrambleView {

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
                        TrainerPromptCard(prompt: promptText(task.scrambled),
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

    @ViewBuilder
    private func typedControls(_ task: WordScrambleTask) -> some View {
        VStack(spacing: Theme.spacing.md) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: answerPlaceholder(task.language),
                            focus: $answerFocused,
                            // Tap-to-replay for the correction box — the form
                            // the slip owed, said in the drilled language.
                            correctionVoice: .init(
                                pronounce: { model.pronounceAction(for: $0, lang: task.language) },
                                isPlaying: { model.isPronouncing($0, lang: task.language) })) {
                submit()
            }
            // why: writing the word out is the answer — the typed drills' rule,
            // so a spelling you know never asks for a confirming tap.
            .onChange(of: input) { _, _ in typed() }
            switch feedback {
            case .neutral:
                // ONE primary action: an empty field reveals, a typed one checks.
                Button(action: submit) {
                    Text(input.isBlankAnswer ? "common.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: input.isBlankAnswer)
            case .almost:
                // The amber hold: the box above spells the slip out, and this
                // waits for the tap that books it amber.
                nextButton.transition(.opacity)
            case .correct:
                // why: the timer never arms under a screen reader, so a clean
                // hit would otherwise have nothing to move on with.
                if screenReaderOn { nextButton }
            case .revealed:
                VStack(spacing: Theme.spacing.sm) {
                    nextButton
                    if run.offersFinish { DrillStopOffer { closeRun() } }
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// The one button that books whatever the feedback already said — which of
    /// the ladder's outcomes that is stays kern's.
    private var nextButton: some View {
        Button {
            dispatch(WordScrambleIntent.ConfirmPending.shared)
        } label: {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }

    // MARK: - Driving the run

    func dispatch(_ intent: WordScrambleIntent) {
        let reduction = WordScrambleRun.shared.reduce(state: run, intent: intent, rng: drillRandom)
        let moved = reduction.state.index != run.index
        if moved {
            // why: cleared in the SAME transaction as the question — the next
            // one must never render a frame carrying the last one's answer.
            input = ""
        }
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .cardFlip)
            : .easeOut(duration: 0.25)
        withAnimation(animation) { run = reduction.state }
        for effect in reduction.effects { apply(effect) }
        // Nothing left to ask: hand the run back, never sit on a blank card.
        if reduction.state.finished { closeRun() }
    }

    private func apply(_ effect: DrillEffect) {
        DrillEffects.apply(effect, advance: &autoAdvance,
                           onAdvance: { dispatch(WordScrambleIntent.AdvanceElapsed.shared) },
                           releaseFocus: { answerFocused = false },
                           silence: { Pronouncer.shared.stop() })
    }

    // MARK: - What the learner does

    /// "Finishing the word IS the answer" — every keystroke is offered to kern,
    /// which decides whether it approves, withdraws an approval, or ignores it.
    func typed() {
        dispatch(WordScrambleIntent.InputChanged(text: input))
    }

    /// The ONE primary action, button and Enter alike: kern checks what stands
    /// in the field, and reveals the spelling when nothing does.
    func submit() {
        dispatch(WordScrambleIntent.Submit(text: input))
    }

    // MARK: - Close → back to the hub that opened it

    /// X during a run: kern books a pending answer exactly as the tap would,
    /// then hands the figures back. An untouched run leaves nothing to report,
    /// and no record line — this drill keeps no record store.
    func closeRun() {
        let closed = WordScrambleRun.shared.close(state: run)
        run = closed.state
        for effect in closed.effects { apply(effect) }
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        answerFocused = false
        onFinish(DrillRunResult(summary, title: "trainer.skill.wordScramble"))
        dismiss()
    }
}
