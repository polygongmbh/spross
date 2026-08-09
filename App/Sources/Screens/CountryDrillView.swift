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
/// A separate view rather than a `TrainerSessionView.Mode`, for the letter
/// drill's reason: a different skill with its own ladder shares no state machine
/// with the slot drill, and the mode enum would carry edits through switches for
/// no reuse.
///
/// Screen content lives in CountryDrillView+Content.swift and grading in
/// CountryDrillView+Grading.swift; state stays here, internal where those
/// extensions reach it.
struct CountryDrillView: View, LanguageNaming {
    let model: AppModel
    /// The joined atlas, handed over by the page that opened the run — one join
    /// per run, never one per question.
    let content: CountryDrillContent
    /// Which way round the questions are asked, settled before a task is built.
    let reverse: Bool
    /// Whether a rung falls on ONE clean win instead of three. Earned by having
    /// topped the ladder once (`CountryDrill.fastUnlocked`); the page that opens
    /// the run has already checked the price, so it is only obeyed here.
    let fast: Bool
    /// Where the rung and the record are kept — the overview's key, so the two
    /// surfaces cannot book a run under two names.
    let storageKey: String
    /// Handed the run's figures just before it closes (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss
    @Environment(\.locale) var locale
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    @State private var tasks: [CountryDrillTask]
    @State var index = 0
    @State var doneCount = 0
    @State var streak = 0
    @State var bestStreak = 0
    /// Misses in a row already booked — the one on screen is not among them, so
    /// 1 while a miss shows means this is the second in a row (`DrillStopOffer`).
    @State var missRun = 0
    /// Per-task results for the segmented progress bar.
    @State private var outcomes: [SessionOutcome] = []
    @State var level: Int
    /// The furthest rung the run stood on — what the record books, since the
    /// ramp drops back on a miss and the rung it ends on is not what it reached.
    @State private var bestLevel: Int
    @State private var winsAtLevel = 0
    @State var input = ""
    @State var feedback: AnswerInputView.Feedback = .neutral
    /// Accepted with a small slip — the proper spelling waits for a tap.
    @State var typoCorrection: String?
    // why: internal, not private — the +Content extension arms and cancels it.
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
        // Every run opens at rung 1 however far the learner has climbed: what
        // the record buys is the page, never a head start (docs/surfaces.md).
        var start = 1
        #if DEBUG
        // UI-test hook: `-uitest-countries-level N` opens the run at that rung,
        // which is how the outer tiers are reached deterministically.
        let preset = UserDefaults.standard.integer(forKey: "uitest-countries-level")
        if preset > 0 { start = min(preset, CountryDrill.shared.ceiling) }
        #endif
        _level = State(initialValue: start)
        _bestLevel = State(initialValue: start)
        _tasks = State(initialValue: [Self.sample(content: content, level: start,
                                                  reverse: reverse, avoiding: nil)])
    }

    var current: CountryDrillTask? { tasks.indices.contains(index) ? tasks[index] : nil }

    /// The language an answer is owed in — the learned one, or the learner's own
    /// where the run was turned round.
    var answerLanguage: String { reverse ? content.source : content.target }

    /// The language the prompt is written in — the other side of the same pair.
    /// Nothing on screen names it; it tags the name for VoiceOver.
    var promptLanguage: String { reverse ? content.target : content.source }

    /// VoiceOver and Switch Control both make a timed screen change hostile: it
    /// truncates the correctness announcement and moves the page under the user.
    /// Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        SessionScaffold.endless(answered: doneCount,
                                outcomes: outcomes,
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
        .onChange(of: index) { _, _ in
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
        case .revealed: return current?.display
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

    // MARK: - Sampling

    /// One question at the current rung; `avoiding` is the row the last one
    /// asked about, which kern resamples once.
    static func sample(content: CountryDrillContent, level: Int, reverse: Bool,
                       avoiding: String?) -> CountryDrillTask {
        CountryDrill.shared.sample(content: content, level: level, reverse: reverse,
                                   avoidId: avoiding, rng: KotlinRandom.companion)
    }

    // MARK: - Ramp

    /// Books the answer, steps the rung through kern, and puts the next question
    /// up. `clean` false (a slip of spelling) is amber: it moves the rung neither
    /// way.
    func advance(correct: Bool, clean: Bool) {
        autoAdvance?.cancel()
        // why: the name belongs to the question being left — without this it
        // keeps sounding over the one that replaces it.
        hushAnswer()
        let step = CountryDrill.shared.step(level: level, winsAtLevel: winsAtLevel,
                                            correct: correct, clean: clean, fast: fast)
        let next = Self.sample(content: content, level: step.nextLevel,
                               reverse: reverse, avoiding: current?.id)
        level = step.nextLevel
        bestLevel = max(bestLevel, step.nextLevel)
        winsAtLevel = step.wins
        if correct {
            streak += 1
            bestStreak = max(bestStreak, streak)
            missRun = 0
        } else {
            streak = 0
            missRun += 1
        }
        outcomes.append(correct ? (clean ? .right : .tough) : .wrong)
        doneCount += 1
        // why: cleared in the SAME transaction as the index switch — the next
        // question must never render one frame with the last one's answer.
        input = ""
        feedback = .neutral
        typoCorrection = nil
        tasks.append(next)
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip) {
            index += 1
        }
    }

    // MARK: - Close → back to the page that opened it

    /// X during a run: book a pending correct answer, then close. An untouched
    /// run leaves nothing to report.
    func closeRun() {
        autoAdvance?.cancel()
        hushAnswer()
        if feedback.isAccepted {
            // why: a pending pause books amber, exactly as answering would —
            // closing must not upgrade it to a clean win.
            advance(correct: true, clean: typoCorrection == nil)
        }
        guard doneCount > 0 else {
            dismiss()
            return
        }
        answerFocused = false
        finish()
    }

    /// Books what the run reached — the rung it climbed to and the streak it
    /// held — and hands its figures to the page that opened it. The rung buys
    /// nothing (the drill is ungated); it is what the overview reads back.
    private func finish() {
        TrainerProgress.record(bestLevel, for: storageKey)
        let record = TrainerRecords.record(bestStreak, for: storageKey)
        // why: the cheer marks the record, not the end of a run — confetti and
        // cheer are one thing (`docs/design.md`), and the tile rains the one.
        if record { DLSound.cheer() }
        onFinish(DrillRunResult(doneCount: doneCount, bestStreak: bestStreak,
                                newRecord: record, title: "trainer.countries"))
        dismiss()
    }
}
