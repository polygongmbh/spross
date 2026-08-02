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
/// A separate view, not a `TrainerSessionView.Mode`: an audio prompt with
/// choice tiles shares no state machine with the typed slot drill, and the
/// mode enum would carry edits through switches for no reuse at all.
///
/// Stage bodies and grading live in LetterDrillView+Stages.swift; the prompt
/// card is HearPromptCard.swift. State stays here — members are internal where
/// the +Stages extension reaches them.
struct LetterDrillView: View {
    let model: AppModel
    let language: String
    /// What this device can ask — resolved when the run opens, not per task.
    let availability: LetterDrillAvailability

    @Environment(\.dismiss) var dismiss
    @Environment(\.locale) var locale
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    @State private var tasks: [LetterDrillTask]
    @State var index = 0
    @State var doneCount = 0
    @State var streak = 0
    @State var bestStreak = 0
    /// Per-task results for the segmented progress bar.
    @State private var outcomes: [SessionOutcome] = []
    @State var level: Int
    @State private var winsAtLevel = 0
    @State var showingSummary = false
    @State var input = ""
    @State var feedback: AnswerInputView.Feedback = .neutral
    /// Accepted with a small slip — the proper spelling waits for a tap.
    @State var typoCorrection: String?
    /// A synonym of the dictated word was typed: amber, and the line names
    /// what actually played. Never wrong — the review flow teaches those forms.
    @State var heardInstead: String?
    /// The tile the learner picked, so the grid can mark both it and the
    /// answer. Non-nil ⇒ this question is answered.
    @State var chosen: String?
    // why: internal, not private — the +Stages extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    @FocusState var answerFocused: Bool
    @AccessibilityFocusState var replayFocused: Bool

    init(model: AppModel, language: String) {
        self.model = model
        self.language = language
        let availability = LetterDrillAvailability(model: model, language: language)
        self.availability = availability
        let consolidated = model.stats?.consolidatedCards ?? 0
        let ceiling = LetterDrill.shared.ceiling(dictation: availability.dictationAvailable)
        var start = min(LetterDrill.shared.entryLevel(consolidated: consolidated), ceiling)
        #if DEBUG
        // UI-test hook: `-uitest-letters-level N` opens the run at that rung,
        // which is how any stage is reached deterministically.
        let preset = UserDefaults.standard.integer(forKey: "uitest-letters-level")
        if preset > 0 { start = min(preset, ceiling) }
        #endif
        _level = State(initialValue: start)
        _tasks = State(initialValue: [Self.sample(model: model, language: language,
                                                  availability: availability, level: start,
                                                  avoiding: nil, avoidingWord: nil)].compactMap { $0 })
    }

    /// The rung ceiling: 9 where dictation exists, else 7.
    var maxLevel: Int { LetterDrill.shared.ceiling(dictation: availability.dictationAvailable) }

    /// How long a rung is — one clean win for a consolidated vocabulary, the
    /// classic two below it (Kern's step function, not this view's).
    var winsRequired: Int {
        LetterDrill.shared.winsToAdvance(consolidated: model.stats?.consolidatedCards ?? 0)
    }

    var current: LetterDrillTask? { tasks.indices.contains(index) ? tasks[index] : nil }

    /// VoiceOver and Switch Control both make a timed screen change hostile:
    /// it truncates the correctness announcement and moves the page under the
    /// user. Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: locale, catalog: model.catalog)
    }

    var body: some View {
        Group {
            if showingSummary {
                summary
            } else if current != nil {
                // why: endless run — position == total keeps the scaffold's
                // counter honest and the bar fills as the run grows.
                SessionScaffold(position: doneCount + 1,
                                total: doneCount + 1,
                                outcomes: outcomes,
                                counter: "\(outcomes.filter { $0 != .wrong }.count)/\(doneCount)",
                                showsMuteButton: true,
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
            playPrompt(trigger: .auto)
            answerFocused = !screenReaderOn && typing
        }
        .onChange(of: index) { _, _ in
            Pronouncer.shared.stop()
            playPrompt(trigger: .auto)
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
        .onChange(of: showingSummary) { _, done in if done { uitestBox("summary") } }
        #endif
    }

    /// True on the stages that carry an input field.
    var typing: Bool {
        guard let stage = current?.stage else { return false }
        return stage == .typed || stage == .dictation
    }

    // MARK: - Audio

    /// Says the current question. Autoplay passes `.auto`, which is where the
    /// read-aloud switch and the VoiceOver gate apply; every explicit tap
    /// passes `.tap` and always sounds. Both live in Pronouncer, not here.
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

    /// The blocking unmute row's action: switch reading aloud on — which also
    /// carries the drill past a silenced phone, the other silence this card
    /// names — and play the question at once, so the fix and its proof are a
    /// single tap.
    func unmute() {
        Pronouncer.shared.setReadAloud(on: true)
        playPrompt(trigger: .tap)
    }

    // MARK: - Sampling

    /// One question at the current rung. Dictation draws from the box, every
    /// other stage from the alphabet; `avoiding` is the previous answer and
    /// `avoidingWord` the word it gapped, each of which Kern resamples once.
    static func sample(model: AppModel, language: String, availability: LetterDrillAvailability,
                       level: Int, avoiding: String?, avoidingWord: String?) -> LetterDrillTask? {
        let drill = LetterDrill.shared
        let rng = KotlinRandom.companion
        if drill.stage(level: level) == .dictation, !availability.dictationCandidates.isEmpty {
            return drill.sampleDictation(candidates: availability.dictationCandidates,
                                         alphabet: availability.alphabet,
                                         level: Int32(level), avoidCardId: avoiding, rng: rng)
        }
        guard let alphabet = availability.alphabet, !availability.promptableRefs.isEmpty else {
            return nil
        }
        return drill.sample(alphabet: alphabet,
                            targetExamples: { availability.examples($0) },
                            level: Int32(level),
                            promptableRefs: availability.promptableRefs,
                            avoidRef: avoiding,
                            avoidWord: avoidingWord,
                            rng: rng)
    }

    // MARK: - Ramp

    /// Books the answer, steps the rung through Kern, and puts the next
    /// question up. `clean` false (a typo, a reveal-assisted or synonym answer)
    /// is amber: it moves the rung neither way.
    func advance(correct: Bool, clean: Bool) {
        autoAdvance?.cancel()
        Pronouncer.shared.stop()
        let step = LetterDrill.shared.step(level: level, winsAtLevel: winsAtLevel,
                                           correct: correct, clean: clean,
                                           maxLevel: maxLevel, winsRequired: winsRequired)
        let next = Self.sample(model: model, language: language, availability: availability,
                               level: step.nextLevel, avoiding: current?.answerRef,
                               avoidingWord: current?.gapText == nil ? nil : current?.promptText)
        level = step.nextLevel
        winsAtLevel = step.wins
        if correct {
            streak += 1
            bestStreak = max(bestStreak, streak)
        } else {
            streak = 0
        }
        outcomes.append(correct ? (clean ? .right : .tough) : .wrong)
        doneCount += 1
        #if DEBUG
        // The no-FSRS proof, printed where a review would have been booked.
        uitestBox("answered")
        #endif
        // why: cleared in the SAME transaction as the index switch — the next
        // question must never render one frame with the last one's answer.
        input = ""
        feedback = .neutral
        typoCorrection = nil
        heardInstead = nil
        chosen = nil
        guard let next else {
            // Nothing left to ask: end on the summary, never on a blank card.
            withAnimation(.easeOut(duration: 0.2)) { showingSummary = true }
            return
        }
        tasks.append(next)
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip) {
            index += 1
        }
    }

    // MARK: - Close → summary

    /// X during a run: book a pending correct answer, then summarise. An
    /// untouched run just closes.
    func closeRun() {
        autoAdvance?.cancel()
        Pronouncer.shared.stop()
        if feedback == .correct {
            // why: a pending pause books amber, exactly as answering would —
            // closing must not upgrade it to a clean win.
            advance(correct: true, clean: typoCorrection == nil && heardInstead == nil)
        }
        guard doneCount > 0 else {
            dismiss()
            return
        }
        answerFocused = false
        withAnimation(.easeOut(duration: 0.2)) { showingSummary = true }
    }
}
