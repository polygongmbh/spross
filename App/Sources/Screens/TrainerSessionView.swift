import SwiftUI
import SprossKern

/// A stateless ENDLESS slot drill (numbers / years / clock / sentences).
/// Same interaction grammar as SessionView — type first, "Aufdecken" as
/// fallback — but NO FSRS/BoxEngine involvement: right or wrong only moves
/// the in-run streak. Tasks are generated lazily; the run ends only when
/// the user closes it (X → summary).
///
/// The run spec is `Mode` (TrainerSessionView+Mode.swift), screen content
/// lives in TrainerSessionView+Drill.swift, and the prompt card is
/// TrainerPromptCard.swift. State stays here — members are internal, not
/// private, where an extension reaches them.
struct TrainerSessionView: View, LanguageNaming {
    let mode: Mode
    /// Kern grader for the typed language; nil (previews) falls back to a
    /// plain case/punctuation-insensitive comparison.
    var normalizer: AnswerNormalizer?
    /// Names the drilled language where no chrome exonym exists for it —
    /// without it a language the chrome does not know is spelled "ES".
    var catalog: Catalog?
    /// Only for saying the answer out loud. Optional because previews build a
    /// run out of nothing but a kind and a language — a drill with no model is
    /// silent rather than broken.
    var model: AppModel?
    /// Handed what the run came to — its figures and whether it took the record
    /// — just before the run closes. The page that started it shows them; this
    /// screen never does (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss

    // why: internal, not private — the +UITest extension reseeds the run
    // at a preset rung.
    @State var tasks: [DrawnTask]
    @State var index = 0
    @State var doneCount = 0
    @State var streak = 0
    @State var bestStreak = 0
    /// This run beat the drill's standing record (`TrainerRecords`), booked once
    /// as the run closes — the result tile's record line and its confetti.
    @State var newRecord = false
    /// Per-task results for the segmented progress bar.
    @State private var outcomes: [SessionOutcome] = []
    /// Adaptive difficulty PER VARIANT (numbers: digit count), each starting at 1
    /// however far the learner has climbed before — persisted progress drives
    /// unlocks only, never the ramp, because the climb is the drill.
    // why: internal, not private — the +UITest extension starts a run at a rung.
    @State var levels: [DrillVariant: Int]
    /// The highest rung each variant stood on in THIS run — what `TrainerProgress`
    /// books on close. Tracked separately because a rung steps back down on a miss,
    /// and the ladder rewards reaching one, not finishing on it. A variant the run
    /// never drew stays absent: an unasked rung was never stood on.
    @State private var bestLevels: [DrillVariant: Int] = [:]
    @State private var winsAtLevel: [DrillVariant: Int] = [:]
    /// Digit counts already introduced with a place-value hint — each length
    /// is hinted only the first time it appears.
    @State var seenDigitCounts: Set<Int> = []
    /// The learner looked the numbers up while owing this answer: it marks the
    /// answer amber (no level progress).
    @State var hintUsed = false
    /// The reference table, raised over the run by "?".
    // why: internal, not private — the +Drill extension owns the button.
    @State var showingReference = false
    @State var input = ""
    @State var feedback: AnswerInputView.Feedback = .neutral
    /// Set when the answer was accepted with a small typo — the proper
    /// spelling is shown during the auto-advance window.
    @State var typoCorrection: String?
    // why: internal, not private — the +Grading extension cancels/schedules it.
    @State var autoAdvance: Task<Void, Never>?
    /// The pending "say the answer" wait, held so leaving a task can drop it.
    @State var answerVoice: Task<Void, Never>?
    /// Second focus attempt for a field that remounts (see focusAnswerField).
    @State var focusRetry: Task<Void, Never>?
    @FocusState var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @Environment(\.locale) var locale

    init(kind: TrainerKind, language: String) {
        self.init(mode: .slots(kind, language))
    }

    init(mode: Mode, normalizer: AnswerNormalizer? = nil, catalog: Catalog? = nil,
         model: AppModel? = nil, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.mode = mode
        self.normalizer = normalizer
        self.catalog = catalog
        self.model = model
        self.onFinish = onFinish
        let start = Dictionary(uniqueKeysWithValues: mode.variants.map { ($0, 1) })
        _levels = State(initialValue: start)
        _tasks = State(initialValue: [Self.sampleTask(mode: mode, levels: start, avoiding: nil)])
    }

    var language: String { mode.language }

    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    var namingCatalog: Catalog? { catalog }

    var body: some View {
        SessionScaffold.endless(answered: doneCount,
                                outcomes: outcomes,
                                // why: the run says its answers out loud
                                // now, so it owes the learner a way to
                                // silence them here, not in Settings.
                                showsMuteButton: model != nil,
                                onClose: { closeRun() }) {
            drillContent
        }
        .onAppear { focusAnswerField() }
        .onChange(of: index) { _, _ in focusAnswerField() }
        // why: one fire per answer — the trigger is "is a form owed", so a slip
        // and a miss both speak once, and the neutral state resets it.
        .onChange(of: spokenAnswer) { _, form in
            if form != nil { autoplayAnswer() }
        }
        .onDisappear {
            autoAdvance?.cancel()
            focusRetry?.cancel()
            hushAnswer()
        }
        #if DEBUG
        .onAppear { uitestStart() }
        #endif
    }

    // Task sampling and the ramp ceilings live in TrainerSessionView+Mode.swift.

    var current: TrainerTask { tasks[index].task }

    /// Which of the run's variants asked the question on screen — what a win, a
    /// miss and the header line all apply to.
    var currentVariant: DrillVariant { tasks[index].variant }

    /// The question on screen was flipped: the reading is the prompt and the value
    /// is owed. The only thing the screen needs it for is the keyboard.
    var currentReversed: Bool { tasks[index].reversed }

    /// The rung a variant is standing on right now. Every variant starts at 1.
    func level(_ variant: DrillVariant) -> Int { levels[variant] ?? 1 }

    // Grading lives in TrainerSessionView+Grading.swift (file-size split).

    /// A correct answer extends the streak, a wrong one resets it
    /// (the run record stays). The next task is generated on demand.
    func advance(correct: Bool, segment: SessionOutcome? = nil) {
        autoAdvance?.cancel()
        // why: the reading belongs to the task being left — without this it
        // keeps sounding over the prompt that replaces it.
        hushAnswer()
        // Mark this length as introduced so its place-value hint shows once.
        if let currentDigits { seenDigitCounts.insert(currentDigits) }
        if correct {
            streak += 1
            bestStreak = max(bestStreak, streak)
        } else {
            streak = 0
        }
        // The rung ramp is kern's, shared with the letter drill: clean wins climb,
        // a miss steps down, an amber answer moves neither way. It applies to the
        // variant that asked — the other variants of a mixed run stand where they were.
        let variant = currentVariant
        let step = DrillRamp.shared.step(level: level(variant), winsAtLevel: winsAtLevel[variant] ?? 0,
                                         correct: correct, clean: segment != .tough,
                                         maxLevel: maxLevel(variant),
                                         winsRequired: Int(Trainer.shared.winsToAdvance(fast: mode.isFast)))
        levels[variant] = step.nextLevel
        winsAtLevel[variant] = step.wins
        bestLevels[variant] = max(bestLevels[variant] ?? 1, step.nextLevel)
        outcomes.append(segment ?? (correct ? .right : .wrong))
        doneCount += 1
        tasks.append(Self.sampleTask(mode: mode, levels: levels, avoiding: current.prompt))
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
    // why: internal, not private — the +UITest hook closes a run the way the ✕ does.
    func closeRun() {
        autoAdvance?.cancel()
        hushAnswer()
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
        // why: booked here, alongside the record, because a run that is still
        // going can still climb — a rung is only final once the run closes. Every
        // variant the run asked, not only the one it ended on: the unlock ladder
        // reads each variant's rung on its own.
        for (variant, best) in bestLevels {
            TrainerProgress.record(best, for: mode.progressKey(variant))
        }
        // why: the cheer marks the record, not the end of a run — closing a
        // drill is a dozen-times-an-evening event and owes no fanfare.
        if newRecord { DLSound.cheer() }
        onFinish(DrillRunResult(doneCount: doneCount, bestStreak: bestStreak,
                                newRecord: newRecord, title: mode.titleKey))
        dismiss()
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
