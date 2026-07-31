import SwiftUI
import SprossKern

/// Drill screen content + close summary of TrainerSessionView. State lives
/// on TrainerSessionView; split out purely for file size.
extension TrainerSessionView {
    private var isPhrases: Bool {
        if case .phrases = mode { return true }
        return false
    }

    private var isNumbers: Bool {
        if case .slots(.numbers, _) = mode { return true }
        return false
    }

    /// Digit count of the current numeric prompt (nil outside the numbers
    /// drill). Internal: advance() marks each length as seen.
    var currentDigits: Int? { isNumbers ? current.prompt.count : nil }

    /// Place word shown the first time a new number length appears.
    private var placeValueHint: String? {
        guard let digits = currentDigits, !seenDigitCounts.contains(digits) else { return nil }
        return Trainer.shared.placeValueHint(digits: Int32(digits), language: language)
    }

    /// Tens look-up for the current drill (Swahili numbers only).
    private var tensReference: [String]? {
        isNumbers ? Trainer.shared.tensReference(language: language) : nil
    }

    var drillContent: some View {
        ScrollView {
            VStack(spacing: DL.Space.m) {
                streakLine
                // ZStack so outgoing and incoming prompt overlap during the
                // flip; .id gives each run position its own view identity.
                ZStack {
                    TrainerPromptCard(task: current, sentence: isPhrases)
                        .id(index)
                        .transition(reduceMotion ? .opacity : .dlCardFlip)
                }
                if let placeValueHint {
                    hintPill(icon: "textformat.123", text: "trainer.newPlace \(placeValueHint)")
                        .transition(.opacity)
                }
                controls
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// Compact in-run score: current streak, plus the run record once it
    /// exceeds the current streak.
    private var streakLine: some View {
        streakText
            .font(DL.Fonts.caption)
            .foregroundStyle(streak > 0 ? Color.dlAccent : Color.dlTextSecondary)
            .monospacedDigit()
            .frame(maxWidth: .infinity)
            .animation(.easeOut(duration: 0.2), value: streak)
            .accessibilityLabel(streakAccessibility)
    }

    /// Composed as `Text` (not a joined String) so each part localizes via the
    /// environment locale with catalog plural handling.
    private var streakText: Text {
        var parts: [Text] = []
        if maxLevel > 1 {
            parts.append(isNumbers ? Text("trainer.digits \(level)") : Text("trainer.level \(level.formatted())"))
        }
        parts.append(Text("trainer.streak \(streak.formatted())"))
        if bestStreak > streak { parts.append(Text("trainer.record \(bestStreak.formatted())")) }
        return parts.joined() ?? Text(verbatim: "")
    }

    private var streakAccessibility: Text {
        var result = Text("a11y.streakInARow \(streak.formatted())")
        if bestStreak > streak { result = result + Text("a11y.recordSuffix \(bestStreak.formatted())") }
        return result
    }

    private var inputEmpty: Bool {
        input.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var controls: some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: String(format: DLChrome.string("session.answer.placeholder %@", locale: locale),
                                                languageName(language)),
                            focus: $answerFocused) {
                submit()
            }
            if case .revealed = feedback, let gloss = current.gloss {
                // why: post-reveal text is read, not glanced at — same size as
                // the vocab card's gloss so the two drills stay consistent.
                Text(gloss)
                    .font(DL.Fonts.subheadline)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .transition(.opacity)
            }
            switch feedback {
            case .neutral:
                VStack(spacing: DL.Space.s) {
                    // ONE primary action: empty input reveals, typed input checks.
                    Button {
                        if inputEmpty {
                            DLSound.reveal()
                            // why: fill the field with the answer instead of
                            // leaving an empty box beside the reveal panel.
                            input = current.display
                            withAnimation { feedback = .revealed(correctAnswer: current.display) }
                        } else {
                            submit()
                        }
                    } label: {
                        Text(inputEmpty ? "session.reveal" : "common.check")
                            .frame(maxWidth: .infinity)
                            .contentTransition(.opacity)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    .keyboardShortcut(.defaultAction)
                    .animation(.easeOut(duration: 0.15), value: inputEmpty)
                    // "?" tens reference — using it marks the answer amber.
                    if tensReference != nil, !hintUsed {
                        Button {
                            withAnimation { hintUsed = true }
                        } label: {
                            Label("trainer.tensLookup", systemImage: "questionmark.circle")
                                .font(DL.Fonts.caption)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.dlTextSecondary)
                    }
                }
            case .correct:
                // A clean answer auto-advances after ~1.2 s. A typo pauses —
                // show the proper spelling and wait for a tap.
                if let typoCorrection {
                    VStack(spacing: DL.Space.m) {
                        Text("session.typoCorrection \(typoCorrection)")
                            .font(DL.Fonts.subheadline)
                            .italic()
                            .foregroundStyle(Color.dlTextSecondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                        Button {
                            advance(correct: true, segment: .tough)
                        } label: {
                            Text("common.next")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(DLPrimaryButtonStyle())
                        .keyboardShortcut(.defaultAction)
                    }
                    .transition(.opacity)
                } else {
                    EmptyView()
                }
            case .revealed:
                // why: no "Wusste ich" here — drills are generated, so
                // self-reporting after seeing the answer proves nothing;
                // revealed simply counts as a miss and moves on.
                Button {
                    advance(correct: false)
                } label: {
                    Text("common.next")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                // why: Enter advances when revealed (hardware keyboards).
                .keyboardShortcut(.defaultAction)
            }
            if let visibleReference {
                referenceCard(visibleReference)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// Tens look-up shown once the learner taps "?" or after a wrong answer.
    private var visibleReference: [String]? {
        guard let tensReference else { return nil }
        let afterWrong = { if case .revealed = feedback { return true }; return false }()
        return (hintUsed || afterWrong) ? tensReference : nil
    }

    private func hintPill(icon: String, text: LocalizedStringKey) -> some View {
        Label(text, systemImage: icon)
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlAccent)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.s)
            .background(
                Capsule().fill(Color.dlSurfaceTint)
            )
    }

    private func referenceCard(_ entries: [String]) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("trainer.tens")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .textCase(.uppercase)
            Text(entries.joined(separator: " · "))
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurfaceTint)
        )
    }

    // MARK: - Close summary

    var summary: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            Text(summaryEmoji)
                .font(.system(size: 72))
                .dlSway(angle: 4, period: 3.4)
                .accessibilityHidden(true)
            Text("trainer.tasksDone \(doneCount)")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            VStack(spacing: DL.Space.s) {
                Text("trainer.bestStreak \(bestStreak.formatted())")
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
                if newRecord {
                    Text("trainer.newRecord")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlAccent)
                }
            }
            Text.joined(Text(mode.titleKey), Text(verbatim: languageName(language)))
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
        // why: confetti is what a record costs — a drill can be closed a dozen
        // times an evening, and a screen that celebrates every close celebrates
        // nothing. The run itself always sways; only the record rains.
        .overlay {
            if newRecord { ConfettiView().ignoresSafeArea() }
        }
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
