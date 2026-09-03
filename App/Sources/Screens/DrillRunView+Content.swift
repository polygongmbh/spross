import SwiftUI
import SprossKern

/// Screen content of a typed drill: the score line, the question card and the
/// typed answer under it. State lives on DrillRunView; split out purely for
/// file size.
///
/// One card serves both drills. A dates question simply carries no picture, so
/// the leading slot stays empty and the prompt — a name, or a dated line in the
/// prompt side's digits — stands where the country's name would.
///
/// One question in the calendar is TAPPED rather than written — the warm-up
/// Sprosse's four names — and the grid that answers it is
/// DrillRunView+Choices.swift.
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
                answerControls
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// Hearing the question itself, on a REVERSED run — the one that asks in the
    /// language being learned and grades in the learner's own. A target-language
    /// word draws a speaker wherever the device can say it, drill or card alike,
    /// and this is the drill's half of that.
    ///
    /// It is also where the voice was missing: a reversed run's ANSWER is the
    /// own-language side and deliberately never autoplays, so before this the
    /// whole task was unhearable. Saying the prompt gives nothing away — the word
    /// is already written on the card.
    ///
    /// Forward runs are mute here for the ordinary reason: their prompt is the
    /// learner's own language, which nothing outside listening mode says. Their
    /// target-language form is the ANSWER, and that already speaks on reveal.
    func promptVoice(_ task: DrillSnapshot) -> CountryPromptCard.Voice? {
        guard reverse, let text = task.promptText else { return nil }
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

    // MARK: - The answer

    /// Written, or picked off kern's tiles where the question came with them.
    @ViewBuilder
    private var answerControls: some View {
        if let names = current.choices {
            choiceControls(names)
        } else {
            typedControls
        }
    }

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
                revealedControls
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// The way on after a miss, and — on the second in a row — the way out.
    var revealedControls: some View {
        VStack(spacing: Theme.spacing.sm) {
            nextButton(confirm)
            if current.offersFinish { DrillStopOffer { closeRun() } }
        }
    }

    // why: internal, not private — the choice grid puts the same button under
    // its own answers.
    func nextButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }
}
