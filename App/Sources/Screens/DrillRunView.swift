import SwiftUI
import SprossKern

/// A typed drill run, whichever material it asks about: the atlas — name the
/// country, the people, the language, and say which is spoken where — or the
/// calendar, the weekday and month names alone and then the whole date
/// assembled out of them. Typed answers only, in whichever direction the page
/// was started with.
///
/// Stateless like the letter drill: no review is ever booked and the box is
/// never read at all — the material is the catalog's, not the learner's own
/// words. Closing leaves a summary on the page that opened it.
///
/// The RUN is kern's: the ladder, the draw, the live approve, the amber hold,
/// the ramp and the close all live in `run`, and every event becomes an intent
/// of the drill's own machine through `Face`. Those machines stay apart on
/// purpose — a different skill with its own ladder shares no state with the
/// others — and they meet only in `DrillEffect` and `DrillRunSummary`. What is
/// SHARED is this screen, which is one thing: what the two drills differ in is
/// `DrillFace` and nothing else.
///
/// Screen content lives in DrillRunView+Content.swift and the driver in
/// DrillRunView+Grading.swift; state stays here, internal where those
/// extensions reach it.
struct DrillRunView<Face: DrillFace>: View, LanguageNaming {
    let model: AppModel
    /// The joined material, handed over by the page that opened the run — one
    /// join per run, never one per question.
    let content: Face.Content
    /// Which way round the questions are asked, settled before a task is built.
    let reverse: Bool
    /// Whether a Sprosse falls on ONE clean win instead of three. Earned by having
    /// topped the ladder once (`DrillFace.fastUnlocked`); the page that opens
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
    @State var run: Face.Run
    /// The learner's text; the run holds every rule that decides what it means.
    @State var input = ""
    /// The tile this question was answered off, or nil while it is still owed —
    /// what the grid marks ✓ and ✗ with. nil the whole way up a written ladder.
    // why: internal, not private — +Content reads it and +Grading sets it.
    @State var chosen: String?
    // why: internal, not private — the +Grading extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    /// The beat between the chime and the answer being said (`autoplayAnswer`).
    @State private var answerVoice: Task<Void, Never>?
    @FocusState var answerFocused: Bool

    init(model: AppModel, content: Face.Content, reverse: Bool, fast: Bool = false,
         storageKey: String, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.content = content
        self.reverse = reverse
        self.fast = fast
        self.storageKey = storageKey
        self.onFinish = onFinish
        let normalizer = Self.normalizer(model: model, content: content, reverse: reverse)
        // Every run opens at Sprosse 1 however far the learner has climbed: what
        // the record buys is the page, never a head start (docs/surfaces.md).
        #if DEBUG
        // UI-test hook: `-uitest-<drill>-level N` opens the run at that Sprosse,
        // which is how the outer Sprossen are reached deterministically. Kern clamps it.
        let preset = UserDefaults.standard.integer(forKey: Face.uitestLevelKey)
        _run = State(initialValue: Face.open(content: content, reverse: reverse, fast: fast,
                                             normalizer: normalizer,
                                             level: preset > 0 ? preset : nil))
        #else
        _run = State(initialValue: Face.open(content: content, reverse: reverse, fast: fast,
                                             normalizer: normalizer, level: nil))
        #endif
    }

    /// The question on screen and the figures around it. A fresh join always has
    /// one — a Sprosse with none is not a Sprosse the learner could climb off.
    var current: DrillSnapshot { Face.snapshot(run) }

    /// The field's face for where kern says the answer stands.
    var feedback: AnswerInputView.Feedback { .init(current.feedback) }

    /// VoiceOver and Switch Control both make a timed screen change hostile: it
    /// truncates the correctness announcement and moves the page under the user.
    /// Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    /// A tapped question has no field to fill, and a keyboard raised over the
    /// tiles would cover the very answer it is waiting for.
    private var wantsKeyboard: Bool { !screenReaderOn && current.choices == nil }

    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        SessionScaffold.endless(tally: current.tally,
                                outcomes: current.outcomes.map { SessionOutcome($0) },
                                // why: the run says its answers out loud, so it
                                // owes the learner a way to silence them here.
                                showsMuteButton: true,
                                onClose: { closeRun() }) {
            drillContent
        }
        .onAppear {
            answerFocused = wantsKeyboard
            autoplayPrompt()
            #if DEBUG
            uitestStart()
            #endif
        }
        .onChange(of: current.index) { _, _ in
            chosen = nil
            answerFocused = wantsKeyboard
            autoplayPrompt()
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

    // MARK: - Saying the question

    /// Says the prompt as each question arrives, where the prompt is a name in
    /// the language being learned (`promptVoice`). One fire per question, keyed
    /// on the same index the card's identity is.
    ///
    /// No beat in front of it, unlike the answer's: nothing has just chimed, and
    /// the question is what the learner is waiting for.
    ///
    /// `pronounceAloud` and not the card's own action: that one fires `.tap`,
    /// which outranks the read-aloud switch because a tap is a request. This is
    /// the app speaking by itself, so it goes through `.auto` and the switch —
    /// and VoiceOver — still veto it.
    private func autoplayPrompt() {
        let task = current
        guard reverse, let text = task.promptText else { return }
        model.pronounceAloud(text, lang: task.promptLanguage)
    }

    // MARK: - Saying the answer

    /// The form currently owed to the learner: the correction after a slip,
    /// otherwise the revealed answer. nil while the answer is still theirs to
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
        let language = current.answerLanguage
        answerVoice?.cancel()
        answerVoice = Task { @MainActor in
            // why: the correct/wrong chime lands first — the same 300 ms the
            // review session waits, or the word starts under the chime.
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            model.pronounceAloud(form, lang: language)
        }
    }

    /// Silence, and drop a wait that has not fired yet. Every way out of a task
    /// goes through here — the next question, the door — because a reading
    /// belongs to the task that revealed it and to nothing after.
    func hushAnswer() {
        answerVoice?.cancel()
        answerVoice = nil
        Pronouncer.shared.stop()
    }

    // The draw, the ramp and the verdict ladder are kern's; the driver that
    // reaches them — and the close — is DrillRunView+Grading.swift.
}
