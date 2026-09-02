import SwiftUI
import SprossKern

/// The letter drill: hear a sound, find the letter. Four glyph tiles, then
/// confusable ones, then typing the glyph, and finally dictation of words the
/// learner already holds — one level, mapped to stages by Kern.
///
/// Stateless like its slot-drill sibling and then some: no review is ever
/// booked (D12 — transcription is not recall). The box is READ, for the pacing
/// figures and the dictation pool, and never written. Closing shows a summary.
///
/// The RUN is kern's (`LetterDrillRun`): the draw, the stages, the verdict
/// ladder and the ramp all live in `run`, and every event becomes a
/// `LetterDrillIntent`. A separate machine from the slot drill's on purpose —
/// an audio prompt with choice tiles shares no grammar with a typed numeral —
/// and the two meet only in `DrillEffect` and `DrillRunSummary`.
///
/// The driver lives in LetterDrillView+Grading.swift, stage bodies in
/// LetterDrillView+Stages.swift, the prompt card in HearPromptCard.swift. State
/// stays here — members are internal where an extension reaches them.
struct LetterDrillView: View, LanguageNaming {
    let model: AppModel
    let language: String
    /// Handed the run's figures just before it closes; the page that started it
    /// shows them (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss
    @Environment(\.locale) var locale
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    /// The whole run, kern's.
    // why: internal, not private — +Grading and +Stages read and drive it.
    @State var run: LetterDrillRunState
    /// The learner's text; the run holds every rule that decides what it means.
    @State var input = ""
    // why: internal, not private — the +Grading extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    @FocusState var answerFocused: Bool
    @AccessibilityFocusState var replayFocused: Bool

    init(model: AppModel, language: String, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.language = language
        self.onFinish = onFinish
        let config = LetterDrillRunConfig(
            report: LetterDrillAvailability(model: model, language: language).report,
            cards: model.box?.cards ?? [:],
            dictationGrader: Self.dictationGrader(model: model, language: language)
        )
        #if DEBUG
        // UI-test hook: `-uitest-letters-level N` opens the run at that Sprosse,
        // which is how any stage is reached deterministically. Kern clamps it.
        let preset = UserDefaults.standard.integer(forKey: "uitest-letters-level")
        if preset > 0 {
            _run = State(initialValue: LetterDrillRun.shared.openAt(config: config,
                                                                    level: Int32(preset),
                                                                    rng: drillRandom))
        } else {
            _run = State(initialValue: LetterDrillRun.shared.open(config: config, rng: drillRandom))
        }
        #else
        _run = State(initialValue: LetterDrillRun.shared.open(config: config, rng: drillRandom))
        #endif
    }

    /// The question on screen; nil only once this device can ask nothing more.
    var current: LetterDrillTask? { run.task }

    /// True on the stages that carry an input field.
    var typing: Bool { run.typing }

    /// The field's face for where kern says the answer stands.
    var feedback: AnswerInputView.Feedback { .init(run.feedback) }

    /// VoiceOver and Switch Control both make a timed screen change hostile:
    /// it truncates the correctness announcement and moves the page under the
    /// user. Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        Group {
            if current != nil {
                SessionScaffold.endless(tally: run.tally,
                                        outcomes: run.outcomes.map { SessionOutcome($0) },
                                        onClose: { closeRun() }) {
                    drillContent
                }
            } else {
                // Nothing this device can ask — the hub gates on the same
                // predicate, so this is a closed door, not a screen.
                Color.dlBackground.ignoresSafeArea().onAppear { dismiss() }
            }
        }
        // why: BOTH hooks. .onChange never fires for the FIRST item, and a
        // single hook therefore ships a silent first question.
        .onAppear {
            playPrompt(trigger: .essential)
            answerFocused = !screenReaderOn && typing
        }
        .onChange(of: run.index) { _, _ in
            Pronouncer.shared.stop()
            playPrompt(trigger: .essential)
            // The audio question, one action away, on every task.
            replayFocused = true
            // why: not under a screen reader — moving the keyboard focus there
            // would drag VoiceOver off the replay button it was just given.
            answerFocused = !screenReaderOn && typing
        }
        .onDisappear {
            autoAdvance?.cancel()
            // D5: leaving mid-clip must silence.
            Pronouncer.shared.stop()
        }
        #if DEBUG
        .onAppear { uitestStart() }
        #endif
    }

    // MARK: - Audio

    /// Says the current question. Autoplay passes `.essential`: here the sound
    /// IS the question, so the read-aloud switch never reaches it and only
    /// VoiceOver — which must not be talked over — still holds it back. Every
    /// explicit tap passes `.tap`. Both gates live in Pronouncer, not here.
    func playPrompt(trigger: Pronouncer.Trigger) {
        guard let task = current, let pronunciation = model.promptPronunciation(for: task) else { return }
        #if DEBUG
        uitestPlay(task, pronunciation: pronunciation, trigger: trigger)
        #endif
        Pronouncer.shared.pronounce(pronunciation,
                                    recordingURL: model.audioURL(pronunciation.recordingPath),
                                    trigger: trigger)
    }

    /// The replay button's action — nil where nothing can play this prompt, so
    /// the card shows a dead speaker rather than pretending. It never touches
    /// focus: a keyboard dismissed on every replay makes dictation unusable.
    var replayAction: (() -> Void)? {
        guard let task = current, let pronunciation = model.promptPronunciation(for: task),
              Pronouncer.shared.canPronounce(pronunciation,
                                             recordingURL: model.audioURL(pronunciation.recordingPath))
        else { return nil }
        return {
            playPrompt(trigger: .tap)
            #if DEBUG
            uitestFocus()
            #endif
        }
    }

    /// Whether the current question's prompt is sounding right now — pulses
    /// the replay glyph on `HearPromptCard`.
    var promptIsPlaying: Bool {
        guard let task = current, let pronunciation = model.promptPronunciation(for: task) else { return false }
        return Pronouncer.shared.playingKey == Pronouncer.key(for: pronunciation)
    }

    // The draw, the ramp and the verdict ladder are kern's; the driver that
    // reaches them — and the close — is LetterDrillView+Grading.swift.
}
