import SwiftUI
import SprossKern

/// Screen content of the dates drill: the score line, the question card and the
/// typed answer under it. State lives on DateDrillView; split out purely for
/// file size.
///
/// The card is the atlas drill's — a dates question simply carries no picture,
/// so the leading slot stays empty and the prompt (a name, or a dated line in
/// the prompt side's digits) stands where the country's name would.
extension DateDrillView {

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
                                      text: current.promptText,
                                      language: promptLanguage,
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
    /// place it turns into words. The three assembled kinds share one sentence:
    /// what changes between them is on the card, not in the ask.
    static func ask(_ kind: DateTaskKind) -> LocalizedStringKey {
        switch kind {
        case .weekday: return "dates.ask.weekday"
        case .month: return "dates.ask.month"
        case .dayOfMonth: return "dates.ask.day"
        case .dayAndMonth, .fullDate, .fullDateWithYear: return "dates.ask.date"
        }
    }

    /// The answer, once the learner has stopped owing it — with the other name
    /// a refused answer actually spelled beside it (Juli is July), which is the
    /// one correction this drill exists to make.
    ///
    /// It is SPOKEN where this device can say it, and silently written where it
    /// cannot — the atlas rule, for the atlas reason.
    private func cardReveal(_ task: DateDrillTask) -> CountryPromptCard.Reveal? {
        switch feedback {
        // why: a clean answer flips in ~1.2 s — opening the card for a beat
        // reads as a correction the learner did not earn.
        case .neutral, .correct:
            return nil
        case .almost, .revealed:
            return .init(otherWord: run.otherWord.map { ($0.word, $0.meanings.joined(separator: ", ")) },
                         word: task.display,
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
            // why: writing the reading out is the answer — the review session's
            // rule, so a date you know never asks for a confirming tap.
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
