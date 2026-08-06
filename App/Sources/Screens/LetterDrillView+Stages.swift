import SwiftUI
import SprossKern

/// Screen content of the letter drill: the four glyph tiles, the typed and
/// dictated stages, and the close summary. State lives on LetterDrillView;
/// split out purely for file size.
extension LetterDrillView {

    var drillContent: some View {
        ScrollView {
            VStack(spacing: DL.Space.m) {
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
                                       muted: Pronouncer.shared.muted,
                                       unmute: { unmute() },
                                       // why: once per run — a line under every
                                       // question is furniture, not a hint.
                                       showsSilentSwitchHint: doneCount == 0,
                                       replayFocus: $replayFocused)
                            .id(index)
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
            .padding(.bottom, DL.Space.l)
        }
        // why: the sibling drill's focus discipline, verbatim — a keyboard that
        // dismisses on a replay tap makes dictation unusable.
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// What the question asks: a letter by its name, a grapheme missing from a
    /// heard word, or a whole word to transcribe.
    func question(for task: LetterDrillTask) -> LocalizedStringKey {
        if task.stage == .dictation { return "letters.dictation" }
        return task.gapText == nil ? "letters.hear" : "letters.spell"
    }

    /// The answer, once the learner has stopped owing it. A gap question closes
    /// its blank with the word Kern already handed over (`gloss`); a dictation
    /// grows the transcription with its meaning below.
    ///
    /// The two amber holds reveal too: a slip and a heard-instead both leave a
    /// spelling worth seeing whole, and the box under the field says which of
    /// the two it was.
    private func cardReveal(_ task: LetterDrillTask) -> HearPromptCard.Reveal? {
        switch feedback {
        case .neutral:
            return nil
        case .correct:
            // why: a clean answer flips in ~1.2 s — opening the card for a beat
            // reads as a correction the learner did not earn.
            return nil
        case .almost, .revealed:
            guard let word = task.gapText == nil ? task.display : task.gloss else { return nil }
            return .init(word: word,
                         // why: the meaning is a REVEAL, never a cue — and a gap
                         // question's gloss IS the word, so it would repeat it.
                         note: task.gapText == nil ? task.gloss : nil,
                         pronounce: model.pronounceAction(for: word, lang: task.language),
                         isPlaying: model.isPronouncing(word, lang: task.language))
        }
    }

    private var streakLine: some View {
        streakText
            .font(DL.Fonts.caption)
            .foregroundStyle(streak > 0 ? Color.dlAccent : Color.dlTextSecondary)
            .monospacedDigit()
            .frame(maxWidth: .infinity)
            .animation(.easeOut(duration: 0.2), value: streak)
            .accessibilityLabel(Text("a11y.streakInARow \(streak.formatted())"))
    }

    private var streakText: Text {
        var parts: [Text] = [Text("trainer.level \(level.formatted())")]
        parts.append(Text("trainer.streak \(streak.formatted())"))
        if bestStreak > streak { parts.append(Text("trainer.record \(bestStreak.formatted())")) }
        return parts.joined() ?? Text(verbatim: "")
    }

    // MARK: - Multiple choice

    /// 2×2 of glyph tiles in Kern's shuffled order — both platforms render the
    /// same draw, so a seeded run is reproducible.
    private func choiceGrid(_ task: LetterDrillTask) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: DL.Space.m),
                            GridItem(.flexible(), spacing: DL.Space.m)],
                  spacing: DL.Space.m) {
            ForEach(task.choices ?? [], id: \.self) { glyph in
                tile(glyph, answer: task.display)
            }
        }
        .animation(.easeOut(duration: 0.2), value: chosen)
    }

    private func tile(_ glyph: String, answer: String) -> some View {
        let answered = chosen != nil
        let isAnswer = glyph == answer
        let isChosen = glyph == chosen
        return Button {
            choose(glyph, answer: answer)
        } label: {
            Text(verbatim: glyph)
                .font(.system(size: 44, weight: .bold, design: .rounded))
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                .frame(maxWidth: .infinity, minHeight: 72)
                .padding(DL.Space.m)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                        .fill(tileFill(answered: answered, isAnswer: isAnswer, isChosen: isChosen))
                )
                // why: correctness is never colour alone — the mark carries it
                // for anyone who cannot tell the two tints apart.
                .overlay(alignment: .topTrailing) {
                    tileMark(answered: answered, isAnswer: isAnswer, isChosen: isChosen)
                }
        }
        .buttonStyle(TrainerChipButtonStyle())
        .disabled(answered)
        // why: a bare Cyrillic glyph read by a German engine is a guess —
        // "Buchstabe ч" is not.
        .accessibilityLabel(Text(verbatim: String(format: DLChrome.string("a11y.letterChoice %@",
                                                                         locale: locale),
                                                  glyph)))
        .accessibilityValue(answered && isAnswer ? Text("a11y.correct") : Text(verbatim: ""))
    }

    private func tileFill(answered: Bool, isAnswer: Bool, isChosen: Bool) -> Color {
        guard answered else { return .dlSurfaceTint }
        if isAnswer { return Color.dlSuccess.opacity(0.22) }
        return isChosen ? Color.dlWrong.opacity(0.22) : .dlSurfaceTint
    }

    @ViewBuilder
    private func tileMark(answered: Bool, isAnswer: Bool, isChosen: Bool) -> some View {
        if answered, isAnswer {
            mark("checkmark.circle.fill", tint: .dlSuccess)
        } else if answered, isChosen {
            mark("xmark.circle.fill", tint: .dlWrong)
        }
    }

    private func mark(_ symbol: String, tint: Color) -> some View {
        Image(systemName: symbol)
            .font(.title3)
            .foregroundStyle(tint)
            .padding(DL.Space.s)
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
            if screenReaderOn {
                nextButton { advance(correct: true, clean: true) }
            }
        // why: tiles grade exact-only (no typo budget), so this cannot arise
        // here — it books like any other accepted answer if it ever does.
        case .almost:
            nextButton { advance(correct: true, clean: false) }
        case .revealed:
            nextButton { advance(correct: false, clean: true) }
        }
    }

    // MARK: - Typed and dictated

    @ViewBuilder
    private func typedControls(_ task: LetterDrillTask) -> some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: String(format: DLChrome.string("session.answer.placeholder %@",
                                                                        locale: locale),
                                                languageName(task.language)),
                            focus: $answerFocused,
                            pronounceCorrection: correctionPronounce(task),
                            correctionIsPlaying: correctionPlaying(task)) {
                submit(task)
            }
            switch feedback {
            case .neutral:
                // ONE primary action: an empty field reveals, a typed one checks.
                Button {
                    if inputEmpty { reveal(task) } else { submit(task) }
                } label: {
                    Text(inputEmpty ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: inputEmpty)
            case .almost:
                // The two amber holds — a slip, and a form the review flow
                // teaches but the dictation did not play. The box above spells
                // either one out; this waits for the tap that books it amber.
                nextButton { advance(correct: true, clean: false) }
                    .transition(.opacity)
            case .correct:
                // why: the timer never arms under a screen reader, so a clean
                // hit would otherwise have nothing to move on with.
                if screenReaderOn {
                    nextButton { advance(correct: true, clean: true) }
                }
            case .revealed:
                nextButton { advance(correct: false, clean: true) }
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// Tap-to-replay for the correction box — the form the slip owed, said in
    /// the drilled language.
    private func correctionPronounce(_ task: LetterDrillTask) -> (() -> Void)? {
        guard case .almost(let form, _) = feedback else { return nil }
        return model.pronounceAction(for: form, lang: task.language)
    }

    private func correctionPlaying(_ task: LetterDrillTask) -> Bool {
        guard case .almost(let form, _) = feedback else { return false }
        return model.isPronouncing(form, lang: task.language)
    }

    var inputEmpty: Bool { input.trimmingCharacters(in: .whitespaces).isEmpty }

    private func nextButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }

    // MARK: - Close summary

    var summary: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            Text(verbatim: summaryEmoji)
                .font(.system(size: 72))
                .dlSway(angle: 4, period: 3.4)
                .accessibilityHidden(true)
            Text("trainer.tasksDone \(doneCount)")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text("trainer.bestStreak \(bestStreak.formatted())")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
            Text.joined(Text("trainer.letters"), Text(verbatim: languageName(language)))
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
            Spacer()
            SessionExitButtons(
                onDone: { dismiss() },
                onPractice: { withAnimation(.easeOut(duration: 0.2)) { showingSummary = false } }
            )
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        .sessionCloseCorner(label: "common.done") { dismiss() }
    }

    private var summaryEmoji: String {
        switch bestStreak {
        case 10...: return "🏆"
        case 5...: return "🎉"
        case 2...: return "💪"
        default: return "🌱"
        }
    }
}
