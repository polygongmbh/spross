import SwiftUI
import SprossKern

/// The atlas drill: name the country, the people, the language — and say which
/// is spoken where. Typed answers only, in whichever direction the page was
/// started with.
///
/// Stateless like both its siblings: no review is ever booked and the box is
/// never read at all — the material is the catalog's atlas, not the learner's
/// own words. Closing leaves a summary on the page that opened it.
///
/// The RUN is kern's (`CountryDrillRun`): the ladder, the draw, the live
/// approve, the amber hold, the ramp and the close all live in `run`, and every
/// event becomes a `CountryDrillIntent`. A separate machine from the slot
/// drill's on purpose — a different skill with its own ladder shares no state
/// with it — and the three meet only in `DrillEffect` and `DrillRunSummary`.
///
/// Screen content lives in CountryDrillView+Content.swift and the driver in
/// CountryDrillView+Grading.swift; state stays here, internal where those
/// extensions reach it.
struct CountryDrillView: View, LanguageNaming {
    let model: AppModel
    /// The joined atlas, handed over by the page that opened the run — one join
    /// per run, never one per question.
    let content: CountryDrillContent
    /// Which way round the questions are asked, settled before a task is built.
    let reverse: Bool
    /// Whether a Sprosse falls on ONE clean win instead of three. Earned by having
    /// topped the ladder once (`CountryDrill.fastUnlocked`); the page that opens
    /// the run has already checked the price, so it is only obeyed here.
    let fast: Bool
    /// Where the Sprosse and the record are kept — the overview's key, so the two
    /// surfaces cannot book a run under two names.
    let storageKey: String
    /// Handed the run's figures just before it closes (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss
    @Environment(\.locale) var locale
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    /// The whole run, kern's.
    // why: internal, not private — +Content and +Grading read and drive it.
    @State var run: CountryDrillRunState
    /// The learner's text; the run holds every rule that decides what it means.
    @State var input = ""
    // why: internal, not private — the +Grading extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    /// The beat between the chime and the answer being said (`autoplayAnswer`).
    @State private var answerVoice: Task<Void, Never>?
    @FocusState var answerFocused: Bool

    init(model: AppModel, content: CountryDrillContent, reverse: Bool, fast: Bool = false,
         storageKey: String, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.content = content
        self.reverse = reverse
        self.fast = fast
        self.storageKey = storageKey
        self.onFinish = onFinish
        let config = CountryDrillRunConfig(
            content: content, reverse: reverse, fast: fast,
            normalizer: Self.normalizer(model: model, content: content, reverse: reverse)
        )
        // Every run opens at Sprosse 1 however far the learner has climbed: what
        // the record buys is the page, never a head start (docs/surfaces.md).
        #if DEBUG
        // UI-test hook: `-uitest-countries-level N` opens the run at that Sprosse,
        // which is how the outer tiers are reached deterministically. Kern clamps it.
        let preset = UserDefaults.standard.integer(forKey: "uitest-countries-level")
        if preset > 0 {
            _run = State(initialValue: CountryDrillRun.shared.openAt(config: config,
                                                                     level: Int32(preset),
                                                                     rng: drillRandom))
        } else {
            _run = State(initialValue: CountryDrillRun.shared.open(config: config, rng: drillRandom))
        }
        #else
        _run = State(initialValue: CountryDrillRun.shared.open(config: config, rng: drillRandom))
        #endif
    }

    /// The question on screen. The atlas always has one — a Sprosse with none is
    /// not a Sprosse the learner could climb off.
    var current: CountryDrillTask { run.task }

    /// The language an answer is owed in — the learned one, or the learner's own
    /// where the run was turned round.
    var answerLanguage: String { run.answerLanguage }

    /// The language the prompt is written in — the other side of the same pair.
    /// Nothing on screen names it; it tags the name for VoiceOver.
    var promptLanguage: String { run.promptLanguage }

    /// The field's face for where kern says the answer stands.
    var feedback: AnswerInputView.Feedback { .init(run.feedback) }

    /// VoiceOver and Switch Control both make a timed screen change hostile: it
    /// truncates the correctness announcement and moves the page under the user.
    /// Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        SessionScaffold.endless(tally: run.tally,
                                outcomes: run.outcomes.map { SessionOutcome($0) },
                                // why: the run says its answers out loud, so it
                                // owes the learner a way to silence them here.
                                showsMuteButton: true,
                                onClose: { closeRun() }) {
            drillContent
        }
        .onAppear {
            answerFocused = !screenReaderOn
            #if DEBUG
            uitestStart()
            #endif
        }
        .onChange(of: run.index) { _, _ in
            answerFocused = !screenReaderOn
        }
        // why: one fire per answer — the trigger is "is a form owed", so a slip
        // and a miss both speak once, and the neutral state resets it.
        .onChange(of: spokenAnswer) { _, form in
            if form != nil { autoplayAnswer() }
        }
        .onDisappear {
            autoAdvance?.cancel()
            // D5: leaving mid-word must silence.
            hushAnswer()
        }
    }

    // MARK: - Saying the answer

    /// The form currently owed to the learner: the correction after a slip,
    /// otherwise the revealed name. nil while the answer is still theirs to
    /// produce — nothing may speak an answer to a question still standing.
    ///
    /// nil on a REVERSED run too, whichever way it ended: the side answered
    /// there is the learner's own language, and every autoplay `read-aloud.md`
    /// describes says a target-language form. The speaker beside the reveal
    /// still says it on request — a tap outranks the rule, as it outranks both
    /// mutes.
    var spokenAnswer: String? {
        guard !reverse else { return nil }
        switch feedback {
        case .almost(let form, _): return form
        case .revealed: return current.display
        case .neutral, .correct: return nil
        }
    }

    /// Fires once when the answer comes out, however it came out. `.auto`, so
    /// the read-aloud switch and VoiceOver both still veto it.
    ///
    /// Held rather than fired and forgotten: the wait outlives a fast tap, and
    /// a reveal closed within it would otherwise speak over whatever screen
    /// replaced the run.
    private func autoplayAnswer() {
        guard let form = spokenAnswer else { return }
        answerVoice?.cancel()
        answerVoice = Task { @MainActor in
            // why: the correct/wrong chime lands first — the same 300 ms the
            // review session waits, or the word starts under the chime.
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            model.pronounceAloud(form, lang: answerLanguage)
        }
    }

    /// Silence, and drop a wait that has not fired yet. Every way out of a task
    /// goes through here — the next question, the door — because a name belongs
    /// to the task that revealed it and to nothing after.
    func hushAnswer() {
        answerVoice?.cancel()
        answerVoice = nil
        Pronouncer.shared.stop()
    }

    // The draw, the ramp and the verdict ladder are kern's; the driver that
    // reaches them — and the close — is CountryDrillView+Grading.swift.
}
