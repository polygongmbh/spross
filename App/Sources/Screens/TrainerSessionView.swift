import SwiftUI
import SprossKern

/// A stateless ENDLESS slot drill (numbers / years / clock / sentences).
/// Same interaction grammar as SessionView — type first, "Aufdecken" as
/// fallback — but NO FSRS/BoxEngine involvement: right or wrong only moves
/// the in-run streak. Tasks are generated lazily; the run ends only when
/// the user closes it (X → summary).
struct TrainerSessionView: View {
    /// What a run drills: bare slot values, or full sentences composed
    /// from verified phrase templates + slot values. Languages are catalog
    /// codes; `reverse` sentences show the target sentence and expect the
    /// template's source language typed (for learners of German).
    enum Mode {
        case slots(TrainerKind, String)
        case phrases(source: String, target: String, reverse: Bool)

        /// The language answers are typed in.
        var typedLanguage: String {
            switch self {
            case .slots(_, let language): return language
            case .phrases(let source, let target, let reverse):
                return reverse ? source : target
            }
        }

        /// Localized display key for the run title (German source = catalog key).
        var titleKey: LocalizedStringKey {
            switch self {
            case .slots(let kind, _): return kind.trainerTitleKey
            case .phrases: return "Sätze"
            }
        }
    }

    let mode: Mode
    /// Kern grader for the typed language; nil (previews) falls back to a
    /// plain case/punctuation-insensitive comparison.
    var normalizer: AnswerNormalizer?

    @Environment(\.dismiss) private var dismiss

    @State private var tasks: [TrainerTask]
    @State private var index = 0
    @State private var doneCount = 0
    @State private var streak = 0
    @State private var bestStreak = 0
    /// Per-task results for the segmented progress bar.
    @State private var outcomes: [SessionOutcome] = []
    /// Adaptive difficulty (numbers: digit count). Two rights in a row at a
    /// level ramp up; one miss steps down.
    @State private var level = 1
    @State private var winsAtLevel = 0
    @State private var showingSummary = false
    /// Digit counts already introduced with a place-value hint — each length
    /// is hinted only the first time it appears.
    @State private var seenDigitCounts: Set<Int> = []
    /// The learner tapped "?" for the tens reference on this task: it stays
    /// visible and marks the answer amber (no level progress).
    @State private var hintUsed = false
    @State private var input = ""
    @State private var feedback: AnswerInputView.Feedback = .neutral
    /// Set when the answer was accepted with a small typo — the proper
    /// spelling is shown during the auto-advance window.
    @State private var typoCorrection: String?
    @State private var autoAdvance: Task<Void, Never>?
    @FocusState private var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.locale) private var locale

    init(kind: TrainerKind, language: String) {
        self.init(mode: .slots(kind, language))
    }

    init(mode: Mode, normalizer: AnswerNormalizer? = nil) {
        self.mode = mode
        self.normalizer = normalizer
        _tasks = State(initialValue: [Self.sampleTask(mode: mode, level: 1, avoiding: nil)])
    }

    private var language: String { mode.typedLanguage }

    private func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: locale, catalog: nil)
    }

    var body: some View {
        Group {
            if showingSummary {
                summary
            } else {
                // why: endless run — position == total keeps the scaffold's
                // "n/n" counter honest (n tasks incl. the current one) and
                // the bar fills toward full as the run grows, never breaks.
                // Counter = "correct/answered" (an endless run has no total).
                SessionScaffold(position: doneCount + 1,
                                total: doneCount + 1,
                                outcomes: outcomes,
                                counter: "\(outcomes.filter { $0 != .wrong }.count)/\(doneCount)",
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

    /// One fresh random task at the current difficulty level; a prompt never
    /// repeats back-to-back (resample once when it equals the previous one).
    private static func sampleTask(mode: Mode, level: Int, avoiding previousPrompt: String?) -> TrainerTask {
        var task = sampleOnce(mode: mode, level: level)
        if task.prompt == previousPrompt {
            task = sampleOnce(mode: mode, level: level)
        }
        return task
    }

    private static func sampleOnce(mode: Mode, level: Int) -> TrainerTask {
        let rng = KotlinRandom.companion
        switch mode {
        case .slots(let kind, let language):
            return Trainer.shared.sample(kind: kind, language: language,
                                         level: Int32(level), rng: rng)
        case .phrases(let source, let target, let reverse):
            let templates = PhraseTemplates.shared.templates(source: source, target: target)
            let template = templates[Int.random(in: 0..<templates.count)]
            return reverse
                ? PhraseSlots.shared.reverseSample(template: template, rng: rng)
                : PhraseSlots.shared.sample(template: template, rng: rng)
        }
    }

    private var maxLevel: Int {
        if case .slots(let kind, _) = mode { return Int(Trainer.shared.maxLevel(kind: kind)) }
        return 1
    }

    private var current: TrainerTask { tasks[index] }

    // MARK: - Drill content

    private var isPhrases: Bool {
        if case .phrases = mode { return true }
        return false
    }

    private var isNumbers: Bool {
        if case .slots(.numbers, _) = mode { return true }
        return false
    }

    /// Digit count of the current numeric prompt (nil outside the numbers drill).
    private var currentDigits: Int? { isNumbers ? current.prompt.count : nil }

    /// Place word shown the first time a new number length appears.
    private var placeValueHint: String? {
        guard let digits = currentDigits, !seenDigitCounts.contains(digits) else { return nil }
        return Trainer.shared.placeValueHint(digits: Int32(digits), language: language)
    }

    /// Tens look-up for the current drill (Swahili numbers only).
    private var tensReference: [String]? {
        isNumbers ? Trainer.shared.tensReference(language: language) : nil
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
                if let placeValueHint {
                    hintPill(icon: "textformat.123", text: "Neue Stelle: \(placeValueHint)")
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
        if case .slots(let kind, _) = mode, maxLevel > 1 {
            parts.append(kind == .numbers ? Text("🔢 \(level) Stellen") : Text("Stufe \(level)"))
        }
        parts.append(Text("🔥 \(streak) in Folge"))
        if bestStreak > streak { parts.append(Text("Rekord \(bestStreak)")) }
        return parts.joined() ?? Text(verbatim: "")
    }

    private var streakAccessibility: Text {
        var result = Text("Serie: \(streak) in Folge")
        if bestStreak > streak { result = result + Text(", Rekord \(bestStreak)") }
        return result
    }

    private var controls: some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: String(format: DLChrome.string("Auf %@ …", locale: locale),
                                                languageName(language)),
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
                        Text(inputEmpty ? "Aufdecken" : "Prüfen")
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
                            Label("Zehner nachschlagen", systemImage: "questionmark.circle")
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
                        Text("Fast! Richtig geschrieben: \(typoCorrection)")
                            .font(DL.Fonts.caption)
                            .italic()
                            .foregroundStyle(Color.dlTextSecondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                        Button {
                            advance(correct: true, segment: .tough)
                        } label: {
                            Text("Weiter")
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
                    Text("Weiter")
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
            Text("Zehner")
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

    // MARK: - Grading (run streak only, no FSRS)

    private var inputEmpty: Bool {
        input.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func submit() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        if isCorrect(trimmed) {
            feedback = .correct
            DLSound.correct()
            // A hint-assisted answer stays amber (no level progress).
            let segment: SessionOutcome = hintUsed ? .tough : .right
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(1200))
                guard !Task.isCancelled else { return }
                advance(correct: true, segment: segment)
            }
        } else {
            feedback = .revealed(correctAnswer: current.display)
            DLSound.wrong()
        }
    }

    /// Normalize-insensitive comparison against every accepted variant,
    /// through the Kern normalizer when present.
    private func isCorrect(_ trimmed: String) -> Bool {
        let typed = normalized(trimmed)
        return current.accepted.contains { normalized($0) == typed }
    }

    private func normalized(_ raw: String) -> String {
        if let normalizer { return normalizer.normalize(raw: raw) }
        return raw.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    /// A correct answer extends the streak, a wrong one resets it
    /// (the run record stays). The next task is generated on demand.
    private func advance(correct: Bool, segment: SessionOutcome? = nil) {
        autoAdvance?.cancel()
        // Mark this length as introduced so its place-value hint shows once.
        if let currentDigits { seenDigitCounts.insert(currentDigits) }
        if correct {
            streak += 1
            bestStreak = max(bestStreak, streak)
            // Ramp: two clean rights at a level earn the next one. A typo or
            // hint-assisted answer (amber) never counts toward it.
            if segment != .tough {
                winsAtLevel += 1
                if winsAtLevel >= 2, level < maxLevel {
                    level += 1
                    winsAtLevel = 0
                }
            }
        } else {
            streak = 0
            // A miss steps difficulty down one notch.
            level = max(1, level - 1)
            winsAtLevel = 0
        }
        outcomes.append(segment ?? (correct ? .right : .wrong))
        doneCount += 1
        tasks.append(Self.sampleTask(mode: mode, level: level, avoiding: current.prompt))
        // why: reset in the SAME transaction as the index switch — the next
        // prompt must never render one frame with the old revealed answer.
        input = ""
        feedback = .neutral
        typoCorrection = nil
        hintUsed = false
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
            Text.joined(Text(mode.titleKey), Text(verbatim: languageName(language)))
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

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(spacing: DL.Space.m) {
            Text(sentence ? "💬" : task.kind.trainerEmoji)
                .font(.system(size: 36))
                .padding(DL.Space.s + 2)
                .background(Circle().fill(Color.dlSurfaceTint))
                .accessibilityHidden(true)
            Text.joined(sentence ? Text("Satz") : Text(task.kind.trainerPromptLabelKey),
                        Text("auf \(LanguageNames.display(task.language, locale: locale, catalog: nil))"))
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
    TrainerSessionView(kind: .numbers, language: "sw")
}

#Preview("Phrases · reverse (typed German)") {
    TrainerSessionView(mode: .phrases(source: "de", target: "uk", reverse: true))
}

#Preview("Clock · German · dark") {
    TrainerSessionView(kind: .clock, language: "de")
        .preferredColorScheme(.dark)
}
