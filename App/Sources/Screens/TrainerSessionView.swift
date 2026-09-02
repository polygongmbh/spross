import SwiftUI
import SprossKern

/// A stateless ENDLESS slot drill (numbers / years / clock / sentences).
/// Same interaction grammar as SessionView — type first, "Aufdecken" as
/// fallback — but NO FSRS/BoxEngine involvement: right or wrong only moves
/// the in-run streak. The run ends only when the user closes it (X → summary).
///
/// The RUN is kern's (`TrainerRun`): the draw, the ramp, the streak, the amber
/// rules and what a close books are all in `run`, and every event on this screen
/// becomes a `TrainerIntent`. What is held here is what no engine can hold — a
/// field of text, a timer, a voice, and the keyboard focus.
///
/// The run spec is `Mode` (TrainerSessionView+Mode.swift), the driver is
/// TrainerSessionView+Grading.swift, screen content TrainerSessionView+Drill.swift,
/// and the prompt card TrainerPromptCard.swift. State stays here — members are
/// internal, not private, where an extension reaches them.
struct TrainerSessionView: View, LanguageNaming {
    /// The run SPEC — kern's, and never edited once the run is open.
    let mode: TrainerMode
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

    /// The whole run, kern's: what is on screen, what the answers have done to
    /// it, and the per-variant Sprossen it stands on.
    // why: internal, not private — the +Drill/+Audio/+UITest extensions read it.
    @State var run: TrainerRunState
    /// The learner's text — the one thing the run deliberately does NOT hold:
    /// the platform owns the field and hands what is in it over as an intent.
    @State var input = ""
    /// The reference table, raised over the run by "?".
    @State var showingReference = false
    // why: internal, not private — the +Grading extension arms/cancels it.
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

    init(mode: TrainerMode, normalizer: AnswerNormalizer? = nil, catalog: Catalog? = nil,
         model: AppModel? = nil, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.mode = mode
        self.normalizer = normalizer
        self.catalog = catalog
        self.model = model
        self.onFinish = onFinish
        _run = State(initialValue: TrainerRun.shared.open(mode: mode, rng: drillRandom))
    }

    var language: String { mode.language }

    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    var namingCatalog: Catalog? { catalog }

    /// The field's face for where kern says the answer stands.
    var feedback: AnswerInputView.Feedback { .init(run.feedback) }

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
        .onChange(of: run.index) { _, _ in focusAnswerField() }
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

    // The draw, the ramp and the two storage identities are kern's
    // (TrainerSessionView+Mode.swift points at them); the driver that reaches
    // them is TrainerSessionView+Grading.swift.
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
        masculineNumeral: false,
        swahiliNounClass: nil
    )
    TrainerSessionView(mode: .phrases(source: "de", target: "uk", templates: [template]))
}

#Preview("Clock · German · dark") {
    TrainerSessionView(kind: .clock, language: "de")
        .preferredColorScheme(.dark)
}
