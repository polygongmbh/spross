import SwiftUI
import SprossKern

/// Screen content of the letter drill: the four glyph tiles, the typed and
/// dictated formats, and the controls under them. Everything it shows is read
/// off `run` and every control dispatches an intent. State lives on
/// LetterDrillView; split out purely for file size.
extension LetterDrillView {

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                if let task = current, let question = run.question, let controls = run.controls {
                    // ZStack so the outgoing and incoming question overlap
                    // during the flip; .id gives each position its identity.
                    ZStack {
                        QuestionCardView(question: question, voice: cardVoice(question),
                                         replayFocus: $replayFocused)
                            .id(question.key)
                            .transition(reduceMotion ? .opacity : .cardFlip)
                    }
                    answerArea(task, controls)
                }
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        // why: the sibling drill's focus discipline, verbatim — a keyboard that
        // dismisses on a replay tap makes dictation unusable.
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// The question's sound plays out of the recording the drill's own player resolves
    /// (a letter's name, never a voice reading the bare glyph); every other saying is the model's.
    private func cardVoice(_ question: Question) -> CardVoice {
        let voice = model.cardVoice
        let prompt = question.prompt.saying
        return CardVoice(pronounce: { $0 == prompt ? replayAction : voice.pronounce($0) },
                         isPlaying: { $0 == prompt ? promptIsPlaying : voice.isPlaying($0) })
    }

    /// The speaker beside a form the drill hands back in the correction box. The dictated word only.
    ///
    /// Every other Sprosse answers with a bare GLYPH, and a glyph is not a form
    /// anything may be asked to say: the lookup never reaches the letter-name
    /// recording the card's own replay button plays, and a voice reads it "as
    /// anything from a spelling alphabet to a pause" (kern `LetterDrillTask`).
    /// Those reveals carry no speaker at all, and the replay above stays the one
    /// way to hear the question.
    func speaker(_ task: LetterDrillTask, _ form: String) -> (() -> Void)? {
        guard task.format == .dictation else { return nil }
        return model.pronounceAction(for: form, lang: task.language)
    }

    var streakLine: some View {
        DrillStreakLine(sprosse: Text("trainer.sprosse \(Int(run.sprosse).formatted())"),
                        answerStreak: Int(run.answerStreak))
    }

    // MARK: - The answer

    /// Four glyph tiles, typed or dictated: kern's controls say which, and every keystroke is
    /// offered to kern, so a finished answer approves itself.
    private func answerArea(_ task: LetterDrillTask, _ controls: AnswerControls) -> some View {
        AnswerArea(controls: controls,
                   text: $input,
                   placeholder: answerPlaceholder(task.language),
                   focus: $answerFocused,
                   correctionVoice: .init(
                       pronounce: { speaker(task, $0) },
                       isPlaying: { model.isPronouncing($0, lang: task.language) }),
                   actions: answerActions) { options, answer in
            choiceGrid(options, answer: answer)
        }
    }

    /// 2×2 of glyph tiles in Kern's shuffled order — both platforms render the
    /// same draw, so a seeded run is reproducible.
    private func choiceGrid(_ options: [String], answer: String) -> some View {
        // A prompt size rather than a ramp entry: a letterform is the thing
        // being READ here, so it is set at picture size the way an emoji face is — and a bare Cyrillic glyph read by a German engine is a
        // guess where "Buchstabe ч" is not.
        DrillChoiceGrid(options: options,
                        answer: answer,
                        chosen: run.chosen,
                        font: Theme.prompt.letter,
                        label: { glyph in
                            Text(verbatim: String(format: ChromeStrings.string("a11y.glyph.letter %@",
                                                                               locale: locale),
                                                  glyph))
                        },
                        pick: choose)
    }
}
