import SwiftUI
import SprossKern

/// A stateless ENDLESS slot drill (numbers / years / clock / sentences).
/// Same interaction grammar as SessionView — type first, "Aufdecken" as
/// fallback — but NO FSRS/BoxEngine involvement: right or wrong only moves
/// the in-run streak. Tasks are generated lazily; the run ends only when
/// the user closes it (X → summary).
///
/// Screen content (drill + summary) lives in TrainerSessionView+Drill.swift;
/// the prompt card is TrainerPromptCard.swift. State stays here — members are
/// internal, not private, where the +Drill extension reaches them.
struct TrainerSessionView: View {
    /// What a run drills: bare slot values, or full sentences composed from
    /// the catalog's sentence frames + slot values. Languages are catalog
    /// codes. The frames are carried, not looked up — a run samples from the
    /// set it was opened with.
    enum Mode {
        case slots(TrainerKind, String)
        case phrases(source: String, target: String, templates: [PhraseTemplate])

        /// The language answers are typed in.
        var typedLanguage: String {
            switch self {
            case .slots(_, let language): return language
            case .phrases(_, let target, _): return target
            }
        }

        /// Catalog key for the run title.
        var titleKey: LocalizedStringKey {
            switch self {
            case .slots(let kind, _): return kind.trainerTitleKey
            case .phrases: return "trainer.phrases"
            }
        }

        /// Identity a record is kept under (`TrainerRecords`): what is drilled
        /// and in which pair — a sentence run typed in German is not the same
        /// feat as the same frames typed in Swahili.
        var recordKey: String {
            switch self {
            case .slots(let kind, let language): return "\(kind.name).\(language)"
            case .phrases(let source, let target, _): return "phrases.\(source)-\(target)"
            }
        }
    }

    let mode: Mode
    /// Kern grader for the typed language; nil (previews) falls back to a
    /// plain case/punctuation-insensitive comparison.
    var normalizer: AnswerNormalizer?
    /// Names the drilled language where no chrome exonym exists for it —
    /// without it a language the chrome does not know is spelled "ES".
    var catalog: Catalog?

    @Environment(\.dismiss) var dismiss

    @State private var tasks: [TrainerTask]
    @State var index = 0
    @State var doneCount = 0
    @State var streak = 0
    @State var bestStreak = 0
    /// This run beat the drill's standing record (`TrainerRecords`), booked
    /// once when the summary opens — the summary's confetti and its record line.
    @State var newRecord = false
    /// Per-task results for the segmented progress bar.
    @State private var outcomes: [SessionOutcome] = []
    /// Adaptive difficulty (numbers: digit count). Two rights in a row at a
    /// level ramp up; one miss steps down.
    @State var level = 1
    @State private var winsAtLevel = 0
    @State var showingSummary = false
    /// Digit counts already introduced with a place-value hint — each length
    /// is hinted only the first time it appears.
    @State var seenDigitCounts: Set<Int> = []
    /// The learner tapped "?" for the tens reference on this task: it stays
    /// visible and marks the answer amber (no level progress).
    @State var hintUsed = false
    @State var input = ""
    @State var feedback: AnswerInputView.Feedback = .neutral
    /// Set when the answer was accepted with a small typo — the proper
    /// spelling is shown during the auto-advance window.
    @State var typoCorrection: String?
    // why: internal, not private — the +Grading extension cancels/schedules it.
    @State var autoAdvance: Task<Void, Never>?
    @FocusState var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @Environment(\.locale) var locale

    init(kind: TrainerKind, language: String) {
        self.init(mode: .slots(kind, language))
    }

    init(mode: Mode, normalizer: AnswerNormalizer? = nil, catalog: Catalog? = nil) {
        self.mode = mode
        self.normalizer = normalizer
        self.catalog = catalog
        _tasks = State(initialValue: [Self.sampleTask(mode: mode, level: 1, avoiding: nil)])
    }

    var language: String { mode.typedLanguage }

    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: locale, catalog: catalog)
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
            // `-uitest-summary 1` jumps straight to the close-summary state;
            // add `-uitest-record 1` to drop the stored record first, so the
            // run books one and the summary shows its record state.
            if defaults.bool(forKey: "uitest-summary") {
                if defaults.bool(forKey: "uitest-record") { TrainerRecords.clear(mode.recordKey) }
                newRecord = TrainerRecords.record(bestStreak, for: mode.recordKey)
                showingSummary = true
            }
            // `-uitest-typo 1` renders the accepted-with-typo state.
            if defaults.bool(forKey: "uitest-typo") {
                feedback = .almost(correctForm: current.display, reason: .typo)
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
        case .phrases(_, _, let templates):
            // why: non-empty by construction — the hub resolves the pair's
            // frames and only offers the chip when the catalog joined some.
            let template = templates[Int.random(in: 0..<templates.count)]
            // Leveled slot values — same ramp semantics as the plain drills;
            // Kern clamps the level to each frame's own slot kind.
            return PhraseSlots.shared.sample(template: template, level: Int32(level), rng: rng)
        }
    }

    /// Ramp ceiling: slot drills per kind; the sentence drill ramps to the
    /// highest ceiling among its frames' slot kinds.
    var maxLevel: Int {
        switch mode {
        case .slots(let kind, _):
            return Int(Trainer.shared.maxLevel(kind: kind))
        case .phrases(_, _, let templates):
            return templates
                .map { Int(Trainer.shared.maxLevel(kind: $0.slotKind)) }
                .max() ?? 1
        }
    }

    var current: TrainerTask { tasks[index] }

    // Grading lives in TrainerSessionView+Grading.swift (file-size split).

    /// A correct answer extends the streak, a wrong one resets it
    /// (the run record stays). The next task is generated on demand.
    func advance(correct: Bool, segment: SessionOutcome? = nil) {
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
        if feedback.isAccepted {
            // why: a pending typo pause books amber, same as answering —
            // closing must not upgrade it to a clean win (level ramp).
            advance(correct: true, segment: typoCorrection != nil ? .tough : nil)
        }
        guard doneCount > 0 else {
            dismiss()
            return
        }
        answerFocused = false
        newRecord = TrainerRecords.record(bestStreak, for: mode.recordKey)
        // why: the cheer marks the record, not the end of a run — closing a
        // drill is a dozen-times-an-evening event and owes no fanfare.
        if newRecord { DLSound.cheer() }
        withAnimation(.easeOut(duration: 0.2)) { showingSummary = true }
    }
}

// MARK: - Previews

#Preview("Numbers · Swahili") {
    TrainerSessionView(kind: .numbers, language: "sw")
}

#Preview("Phrases · German → Ukrainian") {
    // Hand-built frame: a preview has no catalog to join one out of.
    let template = PhraseTemplate(
        id: "train-departs-at",
        source: "de",
        target: "uk",
        sourceTemplate: "Der Zug fährt um {slot} Uhr ab.",
        targetTemplate: "Потяг відправляється о {slot}.",
        slotKind: .clock,
        acceptedFrames: [],
        note: nil,
        countForms: nil,
        sourceCountForms: nil,
        masculineNumeral: false
    )
    TrainerSessionView(mode: .phrases(source: "de", target: "uk", templates: [template]))
}

#Preview("Clock · German · dark") {
    TrainerSessionView(kind: .clock, language: "de")
        .preferredColorScheme(.dark)
}
