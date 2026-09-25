import SwiftUI
import SprossKern

/// A stateless ENDLESS slot drill (numbers / years / clock / sentences).
/// Same interaction grammar as SessionView — type first, "Aufdecken" as
/// fallback — but NO FSRS/BoxEngine involvement: right or wrong only moves
/// the in-run streak. The run ends only when the user closes it (X → summary).
///
/// The RUN is kern's (`NumbersRun`): the draw, the ramp, the streak, the amber
/// rules and what a close books are all in `run`, and every event on this screen
/// becomes a `NumbersIntent`. What is held here is what no engine can hold — a
/// field of text, a timer, a voice, and the keyboard focus.
///
/// The run spec is `Mode` (NumbersRunView+Mode.swift), the driver is the shared
/// one (`DrillRunning`) wired up in NumbersRunView+Run.swift, screen content
/// NumbersRunView+Drill.swift,
/// and the prompt card DrillPromptCard.swift. State stays here — members are
/// internal, not private, where an extension reaches them.
struct NumbersRunView: View, LanguageNaming {
    /// The run SPEC — kern's, and never edited once the run is open.
    let mode: NumbersMode
    /// Kern grader for the typed language; nil (previews) falls back to a
    /// plain case/punctuation-insensitive comparison.
    var normalizer: AnswerNormalizer?
    /// Names the drilled language where no chrome exonym exists for it —
    /// without it a language the chrome does not know is spelled "ES".
    var catalog: Catalog?
    /// Only for saying the answer out loud. Optional because previews build a
    /// run out of nothing but a reading and a language — a drill with no model is
    /// silent rather than broken.
    var model: AppModel?
    /// Handed what the run came to — its figures and whether it took the record
    /// — just before the run closes. The page that started it shows them; this
    /// screen never does (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss

    /// The whole run, kern's: what is on screen, what the answers have done to
    /// it, and the per-exercise Sprossen it stands on.
    // why: internal, not private — the +Drill/+Audio/+UITest extensions read it.
    @State var run: NumbersRunState
    /// The learner's text — the one thing the run deliberately does NOT hold:
    /// the platform owns the field and hands what is in it over as an intent.
    @State var input = ""
    /// The reference table, raised over the run by "?".
    @State var showingReference = false
    // why: internal, not private — the +Run extension arms/cancels it.
    @State var autoAdvance: Task<Void, Never>?
    /// The pending "say the answer" wait, held so leaving a task can drop it.
    @State var answerVoice = AnswerVoice()
    /// Second focus attempt for a field that remounts (see focusAnswerField).
    @State var focusRetry: Task<Void, Never>?
    /// When a timed run's clock runs out; nil otherwise and until on screen.
    @State var deadline: Date?
    @FocusState var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @Environment(\.locale) var locale

    init(reading: NumbersReading, language: String) {
        self.init(mode: .slots(reading, language))
    }

    /// `challenge` replaces the ramp with its script (`NumbersChallenge.open`).
    init(mode: NumbersMode, challenge: NumbersChallenge? = nil, normalizer: AnswerNormalizer? = nil,
         catalog: Catalog? = nil, model: AppModel? = nil,
         onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.mode = challenge?.mode ?? mode
        self.normalizer = normalizer
        self.catalog = catalog
        self.model = model
        self.onFinish = onFinish
        if let challenge {
            _run = State(initialValue: challenge.open())
            return
        }
        #if DEBUG
        // UI-test hook: `-uitest-level N` opens the run's first exercise at that
        // Sprosse, as the letter drill's `-uitest-letters-level` does. Kern clamps it.
        let preset = UserDefaults.standard.integer(forKey: "uitest-level")
        if preset > 0, let exercise = mode.exercises.first {
            let levels: [NumbersExercise: KotlinInt] = [exercise: KotlinInt(int: Int32(preset))]
            _run = State(initialValue: NumbersRun.shared.openAt(mode: mode, levels: levels, rng: drillRandom))
        } else {
            _run = State(initialValue: NumbersRun.shared.open(mode: mode, rng: drillRandom))
        }
        #else
        _run = State(initialValue: NumbersRun.shared.open(mode: mode, rng: drillRandom))
        #endif
    }

    var language: String { mode.language }

    var namingCatalog: Catalog? { catalog }

    var body: some View {
        SessionScaffold.endless(tally: run.tally,
                                outcomes: run.outcomes.map { SessionOutcome($0) },
                                // why: the run says its answers out loud
                                // now, so it owes the learner a way to
                                // silence them here, not in Settings.
                                showsMuteButton: model != nil,
                                onClose: { closeRun() }) {
            drillContent
        }
        .onAppear { focusAnswerField() }
        .task { await runClock() }
        .onChange(of: run.index) { _, _ in focusAnswerField() }
        .saysOwedAnswer(spokenAnswer, lang: language, via: model, voice: answerVoice)
        .onDisappear {
            autoAdvance?.cancel()
            focusRetry?.cancel()
            hushAnswer()
        }
        #if DEBUG
        .onAppear { uitestStart() }
        #endif
    }

    // The draw, the ramp and the two storage identities are kern's
    // (NumbersRunView+Mode.swift points at them); the driver that reaches
    // them is NumbersRunView+Run.swift.
}

// MARK: - Previews

#Preview("Numbers · Swahili") {
    NumbersRunView(reading: .cardinal, language: "sw")
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
        masculineNumeral: false,
        swahiliNounClass: nil
    )
    NumbersRunView(mode: .phrases(source: "de", target: "uk", templates: [template]))
}

#Preview("Clock · German · dark") {
    NumbersRunView(reading: .clock, language: "de")
        .preferredColorScheme(.dark)
}
