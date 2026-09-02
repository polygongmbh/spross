import SwiftUI
import SprossKern

/// Screen content of the atlas drill: the score line, the question card and the
/// typed answer under it. State lives on CountryDrillView; split out purely for
/// file size.
extension CountryDrillView {

    var drillContent: some View {
        ScrollView {
            VStack(spacing: DL.Space.m) {
                DrillStreakLine(level: Text("trainer.sprosse \(Int(run.level).formatted())"),
                                streak: Int(run.streak), bestStreak: Int(run.bestStreak),
                                announcesRecord: true)
                // ZStack so the outgoing and incoming question overlap during
                // the flip; .id gives each position its identity.
                ZStack {
                    CountryPromptCard(ask: Self.ask(current.kind),
                                      emoji: current.promptEmoji,
                                      emojiIsGiveaway: current.emojiIsGiveaway,
                                      text: current.promptText,
                                      // A flag is written in no language,
                                      // so it is tagged with none.
                                      language: current.promptText == nil ? nil : promptLanguage,
                                      revealed: cardReveal(current))
                        .id(Int(run.index))
                        .transition(reduceMotion ? .opacity : .dlCardFlip)
                }
                typedControls
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// What the question asks — the kind names the rule, and this is the only
    /// place it turns into words.
    static func ask(_ kind: CountryTaskKind) -> LocalizedStringKey {
        switch kind {
        case .countryName: return "countries.ask.country"
        case .flagCountry: return "countries.ask.flag"
        case .languageName: return "countries.ask.language"
        case .nationality: return "countries.ask.nationality"
        case .spokenIn: return "countries.ask.spokenIn"
        case .spokenWhere: return "countries.ask.spokenWhere"
        }
    }

    /// The answer, once the learner has stopped owing it — with the neighboring
    /// form kern hands over beside it, so a question about the country teaches
    /// its people too.
    ///
    /// It is SPOKEN where this device can say it, and silently written where it
    /// cannot: Swahili has no iOS voice, and a drill that only worked out loud
    /// would not exist for half the pairs the catalog joins.
    private func cardReveal(_ task: CountryDrillTask) -> CountryPromptCard.Reveal? {
        switch feedback {
        // why: a clean answer flips in ~1.2 s — opening the card for a beat
        // reads as a correction the learner did not earn.
        case .neutral, .correct:
            return nil
        case .almost, .revealed:
            return .init(otherWord: run.otherWord.map { ($0.word, $0.meanings.joined(separator: ", ")) },
                         word: task.display,
                         note: task.gloss,
                         language: answerLanguage,
                         pronounce: model.pronounceAction(for: task.display, lang: answerLanguage),
                         isPlaying: model.isPronouncing(task.display, lang: answerLanguage))
        }
    }

    // MARK: - The typed answer

    @ViewBuilder
    private var typedControls: some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: answerPlaceholder(answerLanguage),
                            focus: $answerFocused,
                            // Tap-to-replay for the correction box — the form
                            // the slip owed, said in the language it is owed in.
                            correctionVoice: .init(
                                pronounce: { model.pronounceAction(for: $0, lang: answerLanguage) },
                                isPlaying: { model.isPronouncing($0, lang: answerLanguage) })) {
                submit()
            }
            // why: writing the name out is the answer — the review session's
            // rule, so a country you know never asks for a confirming tap.
            .onChange(of: input) { _, _ in typed() }
            switch feedback {
            case .neutral:
                // ONE primary action: an empty field reveals, a typed one checks.
                Button(action: checkOrReveal) {
                    Text(input.isBlankAnswer ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
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
                VStack(spacing: DL.Space.s) {
                    nextButton(confirm)
                    if run.offersFinish { DrillStopOffer { closeRun() } }
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    private func nextButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }
}
