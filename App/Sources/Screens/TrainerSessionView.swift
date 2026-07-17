import SwiftUI
import DuoKern

/// A stateless ENDLESS slot drill (numbers / years / clock / sentences).
/// Same interaction grammar as SessionView — type first, "Aufdecken" as
/// fallback — but NO FSRS/BoxEngine involvement: right or wrong only moves
/// the in-run streak. Tasks are generated lazily; the run ends only when
/// the user closes it (X → summary).
struct TrainerSessionView: View {
    /// What a run drills: bare slot values, or full sentences composed
    /// from verified phrase templates + slot values. `reverse` sentences
    /// show the target sentence and expect typed German (for learners
    /// of German).
    enum Mode {
        case slots(TrainerKind, TrainerLanguage)
        case phrases(LanguagePair, reverse: Bool)

        /// The language answers are typed in (reverse phrases → German).
        var language: TrainerLanguage {
            switch self {
            case .slots(_, let language): return language
            case .phrases(let pair, let reverse):
                return reverse ? .german : (pair == .deSw ? .swahili : .ukrainian)
            }
        }

        var title: String {
            switch self {
            case .slots(let kind, _): return kind.trainerTitle
            case .phrases: return "Sätze"
            }
        }
    }

    let mode: Mode

    @Environment(\.dismiss) private var dismiss

    @State private var tasks: [TrainerTask]
    @State private var index = 0
    @State private var doneCount = 0
    @State private var streak = 0
    @State private var bestStreak = 0
    /// Per-task results for the segmented progress bar.
    @State private var outcomes: [SessionOutcome] = []
    @State private var showingSummary = false
    @State private var input = ""
    @State private var feedback: AnswerInputView.Feedback = .neutral
    /// Set when the answer was accepted with a small typo — the proper
    /// spelling is shown during the auto-advance window.
    @State private var typoCorrection: String?
    @State private var autoAdvance: Task<Void, Never>?
    @FocusState private var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(kind: TrainerKind, language: TrainerLanguage) {
        self.init(mode: .slots(kind, language))
    }

    init(phrases pair: LanguagePair, reverse: Bool = false) {
        self.init(mode: .phrases(pair, reverse: reverse))
    }

    init(mode: Mode) {
        self.mode = mode
        _tasks = State(initialValue: [Self.sampleTask(mode: mode, avoiding: nil)])
    }

    private var language: TrainerLanguage { mode.language }

    var body: some View {
        Group {
            if showingSummary {
                summary
            } else {
                // why: endless run — position == total keeps the scaffold's
                // "n/n" counter honest (n tasks incl. the current one) and
                // the bar fills toward full as the run grows, never breaks.
                SessionScaffold(position: doneCount + 1,
                                total: doneCount + 1,
                                outcomes: outcomes,
                                onClose: { closeRun() }) {
                    drillContent
                }
            }
        }
        .onAppear { answerFocused = true }
        .onChange(of: index) { _, _ in answerFocused = true }
        .onChange(of: showingSummary) { _, summarizing in
            if !summarizing { answerFocused = true }
        }
        .onDisappear { autoAdvance?.cancel() }
        #if DEBUG
        // UI-test hooks: `-uitest-input xyz` prefills the answer field,
        // `-uitest-submit 1` submits it after 0.6 s,
        // `-uitest-streak N` presets a running streak for screenshots.
        .onAppear {
            let defaults = UserDefaults.standard
            if let prefill = defaults.string(forKey: "uitest-input") {
                input = prefill
            }
            if defaults.bool(forKey: "uitest-submit") {
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(600))
                    submit()
                }
            }
            let preset = defaults.integer(forKey: "uitest-streak")
            if preset > 0 {
                streak = preset
                bestStreak = max(preset, 12)
                doneCount = preset + 6
            }
            // `-uitest-summary 1` jumps straight to the close-summary state.
            if defaults.bool(forKey: "uitest-summary") {
                showingSummary = true
            }
            // `-uitest-typo 1` renders the accepted-with-typo state.
            if defaults.bool(forKey: "uitest-typo") {
                feedback = .correct
                typoCorrection = current.display
            }
        }
        #endif
    }

    // MARK: - Task sampling (lazy, endless)

    /// One fresh random task; a prompt never repeats back-to-back
    /// (resample once when the draw equals the previous prompt).
    private static func sampleTask(mode: Mode, avoiding previousPrompt: String?) -> TrainerTask {
        var rng = SystemRandomNumberGenerator()
        var task = sampleTask(mode: mode, using: &rng)
        if task.prompt == previousPrompt {
            task = sampleTask(mode: mode, using: &rng)
        }
        return task
    }

    private static func sampleTask(mode: Mode, using rng: inout SystemRandomNumberGenerator) -> TrainerTask {
        switch mode {
        case .slots(let kind, let language):
            return Trainer.sample(kind: kind, language: language, using: &rng)
        case .phrases(let pair, let reverse):
            let templates = PhraseTemplates.templates(pair: pair)
            let template = templates[Int(rng.next() % UInt64(templates.count))]
            return reverse
                ? PhraseSlots.reverseSample(template: template, using: &rng)
                : PhraseSlots.sample(template: template, using: &rng)
        }
    }

    private var current: TrainerTask { tasks[index] }

    // MARK: - Drill content

    private var isPhrases: Bool {
        if case .phrases = mode { return true }
        return false
    }

    private var drillContent: some View {
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
        Text(streakText)
            .font(DL.Fonts.caption)
            .foregroundStyle(streak > 0 ? Color.dlAccent : Color.dlTextSecondary)
            .monospacedDigit()
            .frame(maxWidth: .infinity)
            .animation(.easeOut(duration: 0.2), value: streak)
            .accessibilityLabel("Serie: \(streak) in Folge" +
                                (bestStreak > streak ? ", Rekord \(bestStreak)" : ""))
    }

    private var streakText: String {
        var text = "🔥 \(streak) in Folge"
        if bestStreak > streak { text += " · Rekord \(bestStreak)" }
        return text
    }

    private var controls: some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: "Auf \(language.trainerName) …",
                            focus: $answerFocused) {
                submit()
            }
            if case .revealed = feedback, let gloss = current.gloss {
                Text(gloss)
                    .font(DL.Fonts.caption)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .transition(.opacity)
            }
            switch feedback {
            case .neutral:
                // ONE primary action: empty input reveals, typed input checks.
                Button {
                    if inputEmpty {
                        DLSound.reveal()
                        withAnimation { feedback = .revealed(correctAnswer: current.display) }
                    } else {
                        submit()
                    }
                } label: {
                    Text(inputEmpty ? "Aufdecken" : "Prüfen")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: inputEmpty)
            case .correct:
                // Auto-advances after ~1.2 s (design §Review UX). A typo
                // still counts — show the proper spelling while it lasts.
                if let typoCorrection {
                    Text("richtig — geschrieben: \(typoCorrection)")
                        .font(DL.Fonts.caption)
                        .italic()
                        .foregroundStyle(Color.dlTextSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .transition(.opacity)
                } else {
                    EmptyView()
                }
            case .revealed:
                HStack(spacing: DL.Space.m) {
                    Button("Wusste ich") { advance(correct: true, segment: .tough) }
                        .buttonStyle(DLSoftButtonStyle(color: .dlTeal))
                    Button {
                        advance(correct: false)
                    } label: {
                        Text("Weiter")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    // why: Enter advances when revealed (hardware keyboards).
                    .keyboardShortcut(.defaultAction)
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    // MARK: - Grading (run streak only, no FSRS)

    private var inputEmpty: Bool {
        input.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func submit() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        let match = bestMatch(for: trimmed)
        switch match {
        case .exact, .typo:
            feedback = .correct
            DLSound.correct()
            // Typos count as correct but color the bar amber ("tough").
            let segment: SessionOutcome = { if case .typo = match { return .tough }; return .right }()
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(1200))
                guard !Task.isCancelled else { return }
                advance(correct: true, segment: segment)
            }
        case .wrong:
            feedback = .revealed(correctAnswer: current.display)
            DLSound.wrong()
        }
    }

    /// Best evaluation across all accepted variants; a typo only counts
    /// when no variant matches exactly. Sets `typoCorrection` (shown with
    /// the canonical display form, not the normalized comparison form).
    private func bestMatch(for trimmed: String) -> AnswerNormalizer.Match {
        var best = AnswerNormalizer.Match.wrong
        for variant in current.accepted {
            switch AnswerNormalizer.evaluate(input: trimmed, expected: variant) {
            case .exact:
                typoCorrection = nil
                return .exact
            case .typo(let corrected):
                if best == .wrong { best = .typo(corrected: corrected) }
            case .wrong:
                break
            }
        }
        if case .typo = best { typoCorrection = current.display }
        return best
    }

    /// A correct answer extends the streak, a wrong one resets it
    /// (the run record stays). The next task is generated on demand.
    private func advance(correct: Bool, segment: SessionOutcome? = nil) {
        autoAdvance?.cancel()
        if correct {
            streak += 1
            bestStreak = max(bestStreak, streak)
        } else {
            streak = 0
        }
        outcomes.append(segment ?? (correct ? .right : .wrong))
        doneCount += 1
        tasks.append(Self.sampleTask(mode: mode, avoiding: current.prompt))
        // why: reset in the SAME transaction as the index switch — the next
        // prompt must never render one frame with the old revealed answer.
        input = ""
        feedback = .neutral
        typoCorrection = nil
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip) {
            index += 1
        }
    }

    // MARK: - Close → summary

    /// X during a run: count a pending correct answer, then show the
    /// summary. An untouched run (nothing answered) just closes.
    private func closeRun() {
        autoAdvance?.cancel()
        if feedback == .correct {
            advance(correct: true)
        }
        guard doneCount > 0 else {
            dismiss()
            return
        }
        answerFocused = false
        withAnimation(.easeOut(duration: 0.2)) { showingSummary = true }
    }

    private var summary: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            Text(summaryEmoji)
                .font(.system(size: 72))
                .accessibilityHidden(true)
            Text("\(doneCount) Aufgaben 🎯")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text("Beste Serie: 🔥 \(bestStreak) in Folge")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
            Text("\(mode.title) · \(language.trainerName)")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
            Spacer()
            Button("Weiter üben") {
                withAnimation(.easeOut(duration: 0.2)) { showingSummary = false }
            }
            .buttonStyle(DLSoftButtonStyle())
            Button {
                dismiss()
            } label: {
                Text("Fertig")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
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

// MARK: - TrainerPromptCard

/// Simpler sibling of VocabCardView: same card framing, one big
/// tabular-digit prompt ("347", "1978", "14:35").
private struct TrainerPromptCard: View {
    let task: TrainerTask
    var sentence = false

    var body: some View {
        VStack(spacing: DL.Space.m) {
            Text(sentence ? "💬" : task.kind.trainerEmoji)
                .font(.system(size: 36))
                .padding(DL.Space.s + 2)
                .background(Circle().fill(Color.dlSurfaceTint))
                .accessibilityHidden(true)
            Text("\(sentence ? "Satz" : task.kind.trainerPromptLabel) · auf \(task.language.trainerName)")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .textCase(.uppercase)
            Text(task.prompt)
                .font(.system(size: sentence ? 28 : 56, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(sentence ? 4 : 1)
                .minimumScaleFactor(0.5)
                .multilineTextAlignment(.center)
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity)
        // why: compact enough that prompt + input + button clear the keyboard.
        .frame(minHeight: 185)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .strokeBorder(Color.dlSeparator.opacity(0.6), lineWidth: 1)
        )
        .dlCardShadow()
    }
}

// MARK: - Previews

#Preview("Numbers · Swahili") {
    TrainerSessionView(kind: .numbers, language: .swahili)
}

#Preview("Phrases · reverse (typed German)") {
    TrainerSessionView(phrases: .deUk, reverse: true)
}

#Preview("Clock · German · dark") {
    TrainerSessionView(kind: .clock, language: .german)
        .preferredColorScheme(.dark)
}
