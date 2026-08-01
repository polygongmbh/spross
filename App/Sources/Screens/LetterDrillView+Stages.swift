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
                                       replay: replayAction,
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
                            focus: $answerFocused) {
                submit(task)
            }
            // why: the meaning is a REVEAL, never a cue — a dictation that
            // shows what the word means is no longer taken from the sound.
            if case .revealed = feedback, let gloss = task.gloss {
                Text(verbatim: gloss)
                    .font(DL.Fonts.subheadline)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .transition(.opacity)
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
            case .correct:
                pause
            case .revealed:
                nextButton { advance(correct: false, clean: true) }
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// The two amber holds: a slip worth seeing spelled out, and a form the
    /// review flow teaches but the dictation did not play.
    @ViewBuilder
    private var pause: some View {
        if let line = pauseLine {
            VStack(spacing: DL.Space.m) {
                line
                    .font(DL.Fonts.subheadline)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                nextButton { advance(correct: true, clean: false) }
            }
            .transition(.opacity)
        } else if screenReaderOn {
            nextButton { advance(correct: true, clean: true) }
        }
    }

    private var pauseLine: Text? {
        if let heardInstead { return Text("letters.heardInstead \(heardInstead)") }
        if let typoCorrection { return Text("session.typoCorrection \(typoCorrection)") }
        return nil
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
