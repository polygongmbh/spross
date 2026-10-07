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
/// Sprosse's four names.
extension DrillRunView {

    var drillContent: some View {
        let task = current
        return ScrollView {
            VStack(spacing: Theme.spacing.md) {
                // ZStack so the outgoing and incoming question overlap during
                // the flip; .id gives each position its identity.
                ZStack {
                    QuestionCardView(question: task.question, voice: model.cardVoice)
                        .id(task.question.key)
                        .transition(reduceMotion ? .opacity : .cardFlip)
                }
                answerControls
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    // MARK: - The answer

    /// Written, or picked off kern's tiles where the question came with them — the calendar's
    /// warm-up Sprosse. A calendar name is prose: it is set as prose, and a screen reader saying
    /// it needs no help, where a bare glyph would.
    @ViewBuilder
    private var answerControls: some View {
        if let controls = run.controls {
            let language = current.answerLanguage
            AnswerArea(controls: controls,
                       text: $input,
                       placeholder: answerPlaceholder(language, digits: current.digits),
                       focus: $answerFocused,
                       correctionVoice: .init(
                           pronounce: { model.pronounceAction(for: $0, lang: language) },
                           isPlaying: { model.isPronouncing($0, lang: language) }),
                       nextLocale: model.targetChromeLocale,
                       actions: answerActions) { options, answer in
                DrillChoiceGrid(options: options, answer: answer, chosen: chosen,
                                font: Theme.typography.headline, pick: choose)
            }
        }
    }
}
