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
                if let task = current, let question = run.question {
                    // ZStack so the outgoing and incoming question overlap
                    // during the flip; .id gives each position its identity.
                    ZStack {
                        QuestionCardView(question: question, voice: cardVoice(question),
                                         replayFocus: $replayFocused)
                            .id(question.key)
                            .transition(reduceMotion ? .opacity : .cardFlip)
                    }
                    switch task.format {
                    case .choiceEasy, .choiceConfusable:
                        choiceGrid(task)
                        choiceControls
                    case .typed, .dictation:
                        typedControls(task)
                    }
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

    // MARK: - Multiple choice

    /// 2×2 of glyph tiles in Kern's shuffled order — both platforms render the
    /// same draw, so a seeded run is reproducible. The grid is
    /// `DrillChoiceGrid`, shared with the calendar's warm-up Sprosse.
    private func choiceGrid(_ task: LetterDrillTask) -> some View {
        // A prompt size rather than a ramp entry: a letterform is the thing
        // being READ here, so it is set at picture size the way an emoji face is — and a bare Cyrillic glyph read by a German engine is a
        // guess where "Buchstabe ч" is not.
        DrillChoiceGrid(options: task.choices ?? [],
                        answer: task.display,
                        chosen: run.chosen,
                        font: Theme.prompt.letter,
                        label: { glyph in
                            Text(verbatim: String(format: ChromeStrings.string("a11y.glyph.letter %@",
                                                                               locale: locale),
                                                  glyph))
                        },
                        pick: choose)
    }

    /// A miss always waits for a tap, and on the second in a row offers the way
    /// out under it, as the typed formats do; a clean hit waits only where a
    /// timed screen change would talk over the announcement it just made.
    private var choiceControls: some View {
        AnswerVerdict(feedback: feedback, onConfirm: { confirm() },
                             onStop: run.offersFinish ? { closeRun() } : nil)
    }

    // MARK: - Typed and dictated

    /// Every keystroke is offered to kern: a finished answer approves itself.
    private func typedControls(_ task: LetterDrillTask) -> some View {
        TypedAnswerControls(text: $input,
                            feedback: feedback,
                            placeholder: answerPlaceholder(task.language),
                            focus: $answerFocused,
                            correctionVoice: .init(
                                pronounce: { speaker(task, $0) },
                                isPlaying: { model.isPronouncing($0, lang: task.language) }),
                            onType: { typed() },
                            onSubmit: { submit() },
                            onConfirm: { confirm() },
                            onStop: run.offersFinish ? { closeRun() } : nil)
    }
}
