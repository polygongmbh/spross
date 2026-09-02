import SwiftUI
import SprossKern

/// Screen content of the letter drill: the four glyph tiles, the typed and
/// dictated stages, and the controls under them. Everything it shows is read
/// off `run` and every control dispatches an intent. State lives on
/// LetterDrillView; split out purely for file size.
extension LetterDrillView {

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                streakLine
                if let task = current {
                    // ZStack so the outgoing and incoming question overlap
                    // during the flip; .id gives each position its identity.
                    ZStack {
                        HearPromptCard(question: question(for: task),
                                       language: task.language,
                                       gapText: task.gapText,
                                       revealed: cardReveal(task),
                                       replay: replayAction,
                                       isPlaying: promptIsPlaying,
                                       replayFocus: $replayFocused)
                            .id(run.index)
                            .transition(reduceMotion ? .opacity : .dlCardFlip)
                    }
                    switch task.stage {
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

    /// What the question asks: a letter by its name, a grapheme missing from a
    /// heard word, or a whole word to transcribe.
    func question(for task: LetterDrillTask) -> LocalizedStringKey {
        if task.stage == .dictation { return "letters.ask.dictation" }
        return task.gapText == nil ? "letters.ask.hear" : "letters.ask.spell"
    }

    /// The answer, once the learner has stopped owing it. A gap question closes
    /// its blank with the word Kern already handed over (`gloss`); a dictation
    /// grows the transcription with its meaning below.
    ///
    /// WHETHER the card opens is kern's `showsAnswer`: unlike the slot drill
    /// both amber holds reveal too, because a slip and a heard-instead each
    /// leave a spelling worth seeing whole.
    ///
    /// A letter-name question on a choice Sprosse is the one case that skips it:
    /// the tiles below already mark the answer, so a second glyph — spoken by
    /// a lookup that never resolves to the letter-name recording the big
    /// speaker played — would only repeat it, off-key.
    private func cardReveal(_ task: LetterDrillTask) -> HearPromptCard.Reveal? {
        guard run.showsAnswer,
              !(task.gapText == nil && (task.stage == .choiceEasy || task.stage == .choiceConfusable)),
              let word = task.gapText == nil ? task.display : task.gloss else { return nil }
        return .init(word: word,
                     // why: the meaning is a REVEAL, never a cue — and a gap
                     // question's gloss IS the word, so it would repeat it.
                     note: task.gapText == nil ? task.gloss : nil,
                     pronounce: speaker(task, word),
                     isPlaying: model.isPronouncing(word, lang: task.language))
    }

    /// The speaker beside a form the drill hands back — the revealed answer,
    /// the correction box. The dictated word only.
    ///
    /// Every other Sprosse answers with a bare GLYPH, and a glyph is not a form
    /// anything may be asked to say: the lookup never reaches the letter-name
    /// recording the card's own replay button plays, and a voice reads it "as
    /// anything from a spelling alphabet to a pause" (kern `LetterDrillTask`).
    /// Those reveals carry no speaker at all, and the replay above stays the one
    /// way to hear the question.
    func speaker(_ task: LetterDrillTask, _ form: String) -> (() -> Void)? {
        guard task.stage == .dictation else { return nil }
        return model.pronounceAction(for: form, lang: task.language)
    }

    private var streakLine: some View {
        DrillStreakLine(level: Text("trainer.sprosse \(Int(run.level).formatted())"),
                        streak: Int(run.streak), bestStreak: Int(run.bestStreak))
    }

    // MARK: - Multiple choice

    /// 2×2 of glyph tiles in Kern's shuffled order — both platforms render the
    /// same draw, so a seeded run is reproducible.
    private func choiceGrid(_ task: LetterDrillTask) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: Theme.spacing.md),
                            GridItem(.flexible(), spacing: Theme.spacing.md)],
                  spacing: Theme.spacing.md) {
            ForEach(task.choices ?? [], id: \.self) { glyph in
                tile(glyph, answer: task.display)
            }
        }
        .animation(.easeOut(duration: 0.2), value: run.chosen)
    }

    private func tile(_ glyph: String, answer: String) -> some View {
        let answered = run.chosen != nil
        let isAnswer = glyph == answer
        let isChosen = glyph == run.chosen
        return Button {
            choose(glyph)
        } label: {
            Text(verbatim: glyph)
                .font(.system(size: 44, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                .frame(maxWidth: .infinity, minHeight: Theme.reserve.tile)
                .padding(Theme.spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                        .fill(tileFill(answered: answered, isAnswer: isAnswer, isChosen: isChosen))
                )
                // why: correctness is never color alone — the mark carries it
                // for anyone who cannot tell the two tints apart.
                .overlay(alignment: .topTrailing) {
                    tileMark(answered: answered, isAnswer: isAnswer, isChosen: isChosen)
                }
        }
        .buttonStyle(TrainerChipButtonStyle())
        .disabled(answered)
        // why: a bare Cyrillic glyph read by a German engine is a guess —
        // "Buchstabe ч" is not.
        .accessibilityLabel(Text(verbatim: String(format: DLChrome.string("a11y.glyph.letter %@",
                                                                         locale: locale),
                                                  glyph)))
        .accessibilityValue(answered && isAnswer ? Text("a11y.verdict.correct") : Text(verbatim: ""))
    }

    private func tileFill(answered: Bool, isAnswer: Bool, isChosen: Bool) -> Color {
        guard answered else { return Theme.colors.surfaceTint }
        if isAnswer { return Theme.colors.success.opacity(0.22) }
        return isChosen ? Theme.colors.wrong.opacity(0.22) : Theme.colors.surfaceTint
    }

    @ViewBuilder
    private func tileMark(answered: Bool, isAnswer: Bool, isChosen: Bool) -> some View {
        if answered, isAnswer {
            mark("checkmark.circle.fill", tint: Theme.colors.success)
        } else if answered, isChosen {
            mark("xmark.circle.fill", tint: Theme.colors.wrong)
        }
    }

    private func mark(_ symbol: String, tint: Color) -> some View {
        Image(systemName: symbol)
            .font(.title3)
            .foregroundStyle(tint)
            .padding(Theme.spacing.sm)
            .accessibilityHidden(true)
    }

    /// A miss always waits for a tap; a clean hit waits only where a timed
    /// screen change would talk over the announcement it just made.
    @ViewBuilder
    private var choiceControls: some View {
        switch feedback {
        case .neutral:
            EmptyView()
        case .correct:
            if screenReaderOn { nextButton }
        // why: tiles grade exact-only (no typo budget), so this cannot arise
        // here — it books like any other accepted answer if it ever does.
        case .almost:
            nextButton
        case .revealed:
            nextButton
        }
    }

    // MARK: - Typed and dictated

    @ViewBuilder
    private func typedControls(_ task: LetterDrillTask) -> some View {
        VStack(spacing: Theme.spacing.md) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: answerPlaceholder(task.language),
                            focus: $answerFocused,
                            // Tap-to-replay for the correction box — the form
                            // the slip owed, said in the drilled language.
                            correctionVoice: .init(
                                pronounce: { speaker(task, $0) },
                                isPlaying: { model.isPronouncing($0, lang: task.language) })) {
                submit()
            }
            switch feedback {
            case .neutral:
                // ONE primary action: an empty field reveals, a typed one checks.
                Button {
                    checkOrReveal()
                } label: {
                    Text(input.isBlankAnswer ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: input.isBlankAnswer)
            case .almost:
                // The two amber holds — a slip, and a form the review flow
                // teaches but the dictation did not play. The box above spells
                // either one out; this waits for the tap that books it amber.
                nextButton
                    .transition(.opacity)
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
            dispatch(LetterDrillIntent.ConfirmPending.shared)
        } label: {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }
}
