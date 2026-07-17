import SwiftUI
import DuoKern

/// A stateless 10-task slot drill (numbers / years / clock). Same interaction
/// grammar as SessionView — type first, "Aufdecken" as fallback — but NO
/// FSRS/BoxEngine involvement: right or wrong only moves the round score.
struct TrainerSessionView: View {
    /// What a round drills: bare slot values, or full sentences composed
    /// from verified phrase templates + slot values.
    enum Mode {
        case slots(TrainerKind, TrainerLanguage)
        case phrases(LanguagePair)

        var language: TrainerLanguage {
            switch self {
            case .slots(_, let language): return language
            case .phrases(let pair): return pair == .deSw ? .swahili : .ukrainian
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

    private static let roundLength = 10

    @State private var tasks: [TrainerTask]
    @State private var index = 0
    @State private var correctCount = 0
    @State private var finished = false
    @State private var input = ""
    @State private var feedback: AnswerInputView.Feedback = .neutral
    @State private var autoAdvance: Task<Void, Never>?

    init(kind: TrainerKind, language: TrainerLanguage) {
        self.init(mode: .slots(kind, language))
    }

    init(phrases pair: LanguagePair) {
        self.init(mode: .phrases(pair))
    }

    init(mode: Mode) {
        self.mode = mode
        _tasks = State(initialValue: Self.makeRound(mode: mode))
    }

    private var language: TrainerLanguage { mode.language }

    var body: some View {
        Group {
            if finished {
                completion
            } else {
                SessionScaffold(position: index + 1,
                                total: Self.roundLength,
                                onClose: { dismiss() }) {
                    drillContent
                }
            }
        }
        .onDisappear { autoAdvance?.cancel() }
    }

    // MARK: - Round sampling

    /// Fresh random round; a prompt never repeats back-to-back
    /// (resample once when the draw equals the previous prompt).
    private static func makeRound(mode: Mode) -> [TrainerTask] {
        var rng = SystemRandomNumberGenerator()
        var round: [TrainerTask] = []
        for _ in 0..<roundLength {
            var task = sampleTask(mode: mode, using: &rng)
            if task.prompt == round.last?.prompt {
                task = sampleTask(mode: mode, using: &rng)
            }
            round.append(task)
        }
        return round
    }

    private static func sampleTask(mode: Mode, using rng: inout SystemRandomNumberGenerator) -> TrainerTask {
        switch mode {
        case .slots(let kind, let language):
            return Trainer.sample(kind: kind, language: language, using: &rng)
        case .phrases(let pair):
            let templates = PhraseTemplates.templates(pair: pair)
            let template = templates[Int(rng.next() % UInt64(templates.count))]
            return PhraseSlots.sample(template: template, using: &rng)
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
            VStack(spacing: DL.Space.xl) {
                TrainerPromptCard(task: current, sentence: isPhrases)
                controls
            }
            .padding(.top, DL.Space.s)
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private var controls: some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: "Auf \(language.trainerName) …") {
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
                Button {
                    submit()
                } label: {
                    Text("Prüfen")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
                .keyboardShortcut(.defaultAction)
                Button("Aufdecken") {
                    withAnimation { feedback = .revealed(correctAnswer: current.display) }
                }
                .buttonStyle(DLSoftButtonStyle())
            case .correct:
                // Auto-advances after ~800 ms (design §Review UX).
                EmptyView()
            case .revealed:
                HStack(spacing: DL.Space.m) {
                    Button("Wusste ich") { advance(correct: true) }
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

    // MARK: - Grading (round score only, no FSRS)

    private func submit() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        // Any accepted variant counts as correct.
        if current.accepted.contains(where: { AnswerNormalizer.matches(input: trimmed, expected: $0) }) {
            feedback = .correct
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(800))
                guard !Task.isCancelled else { return }
                advance(correct: true)
            }
        } else {
            feedback = .revealed(correctAnswer: current.display)
        }
    }

    private func advance(correct: Bool) {
        autoAdvance?.cancel()
        if correct { correctCount += 1 }
        input = ""
        feedback = .neutral
        if index + 1 >= Self.roundLength {
            finished = true
        } else {
            index += 1
        }
    }

    private func restart() {
        autoAdvance?.cancel()
        tasks = Self.makeRound(mode: mode)
        index = 0
        correctCount = 0
        input = ""
        feedback = .neutral
        finished = false
    }

    // MARK: - Round completion

    private var completion: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            Text(scoreEmoji)
                .font(.system(size: 72))
                .accessibilityHidden(true)
            Text("\(correctCount) von \(Self.roundLength) 🎯")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text("\(mode.title) · \(language.trainerName)")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
            Spacer()
            Button("Nochmal") { restart() }
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

    private var scoreEmoji: String {
        switch correctCount {
        case Self.roundLength: return "🏆"
        case 7...: return "🎉"
        case 4...: return "💪"
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
        VStack(spacing: DL.Space.l) {
            Text(sentence ? "💬" : task.kind.trainerEmoji)
                .font(.system(size: 44))
                .padding(DL.Space.m)
                .background(Circle().fill(Color.dlSurfaceTint))
                .accessibilityHidden(true)
            Text("\(sentence ? "Satz" : task.kind.trainerPromptLabel) · auf \(task.language.trainerName)")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .textCase(.uppercase)
            Text(task.prompt)
                .font(.system(size: sentence ? 28 : 64, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(sentence ? 4 : 1)
                .minimumScaleFactor(0.5)
                .multilineTextAlignment(.center)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 240)
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

#Preview("Clock · German · dark") {
    TrainerSessionView(kind: .clock, language: .german)
        .preferredColorScheme(.dark)
}
