import SwiftUI
import SprossKern

/// Screen content of a typed drill: the score line, the question card and the
/// typed answer under it. State lives on DrillRunView; split out purely for
/// file size.
///
/// One card serves both drills. A dates question simply carries no picture, so
/// the leading slot stays empty and the prompt — a name, or a dated line in the
/// prompt side's digits — stands where the country's name would.
extension DrillRunView {

    var drillContent: some View {
        let task = current
        return ScrollView {
            VStack(spacing: Theme.spacing.md) {
                DrillStreakLine(level: Text("trainer.sprosse \(task.level.formatted())"),
                                streak: task.streak, bestStreak: task.bestStreak,
                                announcesRecord: true)
                // ZStack so the outgoing and incoming question overlap during
                // the flip; .id gives each position its identity.
                ZStack {
                    CountryPromptCard(ask: task.ask,
                                      emoji: task.promptEmoji,
                                      emojiIsGiveaway: task.emojiIsGiveaway,
                                      text: task.promptText,
                                      // A picture is written in no language,
                                      // so a question that is one is tagged with none.
                                      language: task.promptText == nil ? nil : task.promptLanguage,
                                      promptVoice: promptVoice(task),
                                      revealed: cardReveal(task))
                        .id(task.index)
                        .transition(reduceMotion ? .opacity : .cardFlip)
                }
                typedControls
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// Hearing the question itself, where the question is a NAME in the language
    /// being learned — which is a REVERSED run, the one that asks in the target
    /// and grades in the learner's own.
    ///
    /// Forward runs stay mute: their prompt is the learner's own language, and
    /// every autoplay `read-aloud.md` describes says a form in the language being
    /// LEARNED. So a reversed run, which had nothing audible in it at all — its
    /// answer is the own-language side and deliberately does not speak — is
    /// exactly where the voice was missing.
    ///
    /// Saying it gives nothing away: the word is already written on the card.
    func promptVoice(_ task: DrillSnapshot) -> CountryPromptCard.Voice? {
        guard task.promptIsAName, reverse, let text = task.promptText else { return nil }
        return .init(pronounce: model.pronounceAction(for: text, lang: task.promptLanguage),
                     isPlaying: model.isPronouncing(text, lang: task.promptLanguage))
    }

    /// The answer, once the learner has stopped owing it — with whatever kern
    /// hands over beside it: the neighboring form that teaches the people along
    /// with the country, or the other name a refused answer actually spelled
    /// (Juli is July).
    ///
    /// It is SPOKEN where this device can say it, and silently written where it
    /// cannot: Swahili has no iOS voice, and a drill that only worked out loud
    /// would not exist for half the pairs the catalog joins.
    private func cardReveal(_ task: DrillSnapshot) -> CountryPromptCard.Reveal? {
        switch feedback {
        // why: a clean answer flips in ~1.2 s — opening the card for a beat
        // reads as a correction the learner did not earn.
        case .neutral, .correct:
            return nil
        case .almost, .revealed:
            return .init(otherWord: task.otherWord.map { ($0.word, $0.meanings.joined(separator: ", ")) },
                         word: task.display,
                         note: task.gloss,
                         language: task.answerLanguage,
                         pronounce: model.pronounceAction(for: task.display, lang: task.answerLanguage),
                         isPlaying: model.isPronouncing(task.display, lang: task.answerLanguage))
        }
    }

    // MARK: - The typed answer

    @ViewBuilder
    private var typedControls: some View {
        let language = current.answerLanguage
        VStack(spacing: Theme.spacing.md) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: answerPlaceholder(language),
                            focus: $answerFocused,
                            // Tap-to-replay for the correction box — the form
                            // the slip owed, said in the language it is owed in.
                            correctionVoice: .init(
                                pronounce: { model.pronounceAction(for: $0, lang: language) },
                                isPlaying: { model.isPronouncing($0, lang: language) })) {
                submit()
            }
            // why: writing the answer out is the answer — the review session's
            // rule, so a name you know never asks for a confirming tap.
            .onChange(of: input) { _, _ in typed() }
            switch feedback {
            case .neutral:
                // ONE primary action: an empty field reveals, a typed one checks.
                Button(action: checkOrReveal) {
                    Text(input.isBlankAnswer ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: input.isBlankAnswer)
            case .almost:
                // The amber hold: the box above spells the slip out, and this
                // waits for the tap that books it amber.
                nextButton(confirm).transition(.opacity)
            case .correct:
                // why: the timer never arms under a screen reader, so a clean
                // hit would otherwise have nothing to move on with.
                if screenReaderOn {
                    nextButton(confirm)
                }
            case .revealed:
                VStack(spacing: Theme.spacing.sm) {
                    nextButton(confirm)
                    if current.offersFinish { DrillStopOffer { closeRun() } }
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    private func nextButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }
}
