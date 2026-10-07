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
                                      revealed: cardReveal(task),
                                      hint: newWordHint(task))
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

    /// The word this question's language adds, the first time it is asked for
    /// — the numbers drill's first-sight hint, for a pattern instead of a
    /// length. Always a word in the language being LEARNED, which is what a
    /// card carrying only a date in digits cannot otherwise hand over.
    func newWordHint(_ task: DrillSnapshot) -> DrillHint? {
        task.newWord.map { .init(icon: "text.append", text: "dates.newWord \($0)") }
    }

    /// Hearing the question itself, wherever kern says it aloud
    /// (`DrillRunProgress.promptSaying`): a reversed run, whose prompt is the
    /// form in the language being learned. The tap here says what the autoplay
    /// says; a forward run's prompt is the learner's own language and draws none.
    func promptVoice(_ task: DrillSnapshot) -> CountryPromptCard.Voice? {
        guard let saying = run.promptSaying else { return nil }
        return .init(pronounce: model.pronounceAction(for: saying.form, lang: saying.lang),
                     isPlaying: model.isPronouncing(saying.form, lang: saying.lang))
    }

    /// The answer, once a miss opens the card (kern's `showsAnswer`) — with whatever kern
    /// hands over beside it: the neighboring form that teaches the people along
    /// with the country, or the other name a refused answer actually spelled
    /// (Juli is July).
    ///
    /// It is SPOKEN where this device can say it, and silently written where it
    /// cannot: Swahili has no iOS voice, and a drill that only worked out loud
    /// would not exist for half the pairs the catalog joins.
    private func cardReveal(_ task: DrillSnapshot) -> CountryPromptCard.Reveal? {
        guard task.showsAnswer else { return nil }
        return .init(otherWord: task.otherWord.map { ($0.word, $0.meanings.joined(separator: ", ")) },
                     word: task.display,
                     note: task.gloss,
                     language: task.answerLanguage,
                     pronounce: model.pronounceAction(for: task.display, lang: task.answerLanguage),
                     isPlaying: model.isPronouncing(task.display, lang: task.answerLanguage))
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

    private var typedControls: some View {
        let language = current.answerLanguage
        return TypedAnswerControls(text: $input,
                                   feedback: feedback,
                                   placeholder: answerPlaceholder(language, digits: current.digits),
                                   focus: $answerFocused,
                                   correctionVoice: .init(
                                       pronounce: { model.pronounceAction(for: $0, lang: language) },
                                       isPlaying: { model.isPronouncing($0, lang: language) }),
                                   keyboard: current.digits ? .numbersAndPunctuation : .default,
                                   onType: { typed() },
                                   onSubmit: { submit() },
                                   onConfirm: { confirm() },
                                   onStop: stopOffer)
    }

    /// The way out, on the second miss in a row — nil while the run is not
    /// offering one.
    // why: internal, not private — the choice grid offers the same way out.
    var stopOffer: (() -> Void)? {
        current.offersFinish ? { closeRun() } : nil
    }
}
