import SwiftUI
import SprossKern

/// The driver every typed drill run stands on: one event put to kern, its next
/// state back, the field cleared in the same transaction as the question, the
/// effects carried out, and the close that hands the figures to the page that
/// opened the run.
///
/// The drills ask the same way — a card, a field or a bank of tiles, ONE primary
/// action, an amber hold, a ✕ — so they are this driver with the parameters
/// below and not five cuts of it (`docs/design.md` § Review UX rules).
/// What actually differs is the machine underneath: kern keeps a heard glyph, a
/// typed numeral, an atlas, a mixed-up spelling and a shuffled phrase apart on
/// purpose, so each drill hands over its own run state and its own intent
/// vocabulary, and each files a close in stores of its own. Those are the two
/// associated types and the handful of members under them; nothing else varies.
///
/// Nothing here decides a rule. Which branch waits, what an answer is worth and
/// when a run is over are kern's, read off `DrillStep`, `DrillEffect` and
/// `DrillClose`; what this owns is the field, the animation, the keyboard and
/// the voice — the platform's half (`CLAUDE.md`).
@MainActor
protocol DrillRunning: View {

    // MARK: - The machine under this drill

    /// Kern's whole run state, replaced whole by every reduction — always one
    /// of kern's `DrillRunProgress` states, which is where what the question
    /// says aloud comes from (`Reading`).
    associatedtype Run: DrillRunProgress
    /// The vocabulary that machine takes its events in.
    associatedtype Move

    // MARK: - What the screen holds

    var run: Run { get nonmutating set }
    /// The learner's text — the one thing kern deliberately does not hold, and
    /// so the one thing the driver has to clear itself.
    var input: String { get nonmutating set }
    var autoAdvance: Task<Void, Never>? { get nonmutating set }
    var answerFocused: Bool { get nonmutating set }
    var reduceMotion: Bool { get }
    var dismiss: DismissAction { get }
    /// Handed the run's figures just before it closes (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void { get }

    // MARK: - Putting an event to kern

    func reduce(_ run: Run, _ move: Move) -> DrillStep<Run>

    /// Which question the run stands on — the card's identity, and what tells
    /// the driver the run moved on.
    func questionIndex(_ run: Run) -> Int

    /// Nothing left to ask.
    func isFinished(_ run: Run) -> Bool

    /// A live keystroke, in this drill's words. nil where the drill has no
    /// field to type in.
    func typedMove(_ text: String) -> Move?

    /// Check and Enter alike. nil where there is no field to check — placing
    /// the last atom IS the answer on the drill whose question is an order
    /// rather than a spelling.
    func submitMove(_ text: String) -> Move?

    /// The explicit tap that books whatever the feedback already said.
    var confirmMove: Move { get }

    /// The armed beat elapsed.
    var advanceMove: Move { get }

    /// Going on from a pause kern called (`DrillPacing`).
    var keepPracticingMove: Move { get }

    /// Where kern says the answer stands; the field's face is read off it.
    var turnFeedback: TurnFeedback { get }

    // MARK: - What kern asks of the device

    /// What says the question and its answer, and what an armed beat waits on
    /// while the answer sounds.
    var reader: Reader { get }

    /// Where the voice looks an answer up; nil in a preview, which says nothing.
    var voiceModel: AppModel? { get }

    /// Silencing whatever the question being left was saying.
    func silence()

    /// Dropping the keyboard for a pause that waits for a tap.
    func releaseFocus()

    /// The run reached the next question — for a drill with something of its
    /// own to do there.
    func movedOn()

    // MARK: - Closing

    /// kern's close, with whatever this drill's own stores take from it already
    /// filed: WHICH stores those are is the drill's, every value written kern's.
    func closing() -> DrillClose<Run>

    /// What the tile a closed run leaves calls it.
    var resultTitle: LocalizedStringKey { get }

    /// Where an answered close stamps this drill's last run (`DrillSuggestion.lastRunKey`).
    var lastRunKey: String { get }

    #if DEBUG
    /// `-uitest-streak N`: stand the run mid-streak.
    func seedAnswerStreak(_ answerStreak: Int)
    #endif
}

/// A closed run as the driver takes it back: the state it ends on, the figures
/// for the page that started it, and what kern asked the device for on the way
/// out. What a drill files in its own stores never appears here — it is already
/// written by the time this is handed over.
struct DrillClose<Run> {
    let run: Run
    /// nil ⇒ the run was never answered: dismiss, store nothing.
    let summary: DrillRunSummary?
    let effects: [DrillEffect]
}

// MARK: - Driving the run

extension DrillRunning {

    /// One event to kern and everything that follows from its answer.
    func dispatch(_ move: Move) {
        let step = reduce(run, move)
        let moved = questionIndex(step.run) != questionIndex(run)
        if moved {
            // why: the field is ours, so kern cannot clear it — and the text has
            // to go in the SAME transaction as the question, or the next prompt
            // renders one frame carrying the last one's answer.
            input = ""
            movedOn()
        }
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .cardFlip)
            : .cardReveal
        withAnimation(animation) { run = step.run }
        for effect in step.effects { apply(effect) }
        // why: after the effects — a verdict's Silence would cut the answer this
        // starts, and an armed beat only asks the reader once its delay is up.
        reader.follow(step.run.reading, model: voiceModel)
        // Nothing left to ask: hand the run back, never repeat a question.
        if isFinished(step.run) { closeRun() }
    }

    func apply(_ effect: DrillEffect) {
        DrillEffects.apply(effect, advance: &autoAdvance, reader: reader,
                           onAdvance: { dispatch(advanceMove) },
                           releaseFocus: { releaseFocus() },
                           silence: { silence() })
    }

    // MARK: - What the learner does

    /// "Finishing the word IS the answer" — every keystroke is offered to kern,
    /// which decides whether it approves, withdraws an approval, or is ignored
    /// because a pause is standing.
    func typed() {
        guard let move = typedMove(input) else { return }
        dispatch(move)
    }

    /// The ONE primary action, button and Enter alike: kern checks what stands
    /// in the field, and reveals the answer when nothing does.
    func submit() {
        guard let move = submitMove(input) else { return }
        dispatch(move)
    }

    /// The tap that books whatever the feedback already said.
    func confirm() {
        dispatch(confirmMove)
    }

    /// Keep practicing, from the pause: the same run goes on.
    func keepPracticing() {
        dispatch(keepPracticingMove)
    }

    /// Where the answer area under every drill hands its taps: the field and the one
    /// primary action to kern, the held verdict's Next, and the way out where kern offers it.
    var answerActions: AnswerActions {
        AnswerActions(submit: { submit() }, type: { _ in typed() },
                      confirm: { confirm() }, stop: { closeRun() })
    }

    // MARK: - Close → back to the page that opened it

    /// X during a run: kern books a pending answer exactly as the tap would,
    /// then hands the figures back. An untouched run leaves nothing to report.
    func closeRun() {
        let closed = closing()
        run = closed.run
        for effect in closed.effects { apply(effect) }
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        answerFocused = false
        TrainerProgress.stampRun(lastRunKey)
        // why: confetti and cheer are one thing (`docs/design.md`); the page the
        // run closes onto rains the one, so the close sounds the other.
        if summary.celebrated { Sound.cheer() }
        onFinish(DrillRunResult(summary, title: resultTitle))
        dismiss()
    }

    // MARK: - The two things the screen reads back

    /// The field's face for where kern says the answer stands.
    var feedback: AnswerInputView.Feedback { .init(turnFeedback) }

    /// VoiceOver and Switch Control both make a timed screen change hostile: it
    /// truncates the correctness announcement and moves the page under the user.
    /// Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    // MARK: - What most drills have nothing of their own to do about

    func typedMove(_ text: String) -> Move? { nil }

    // why: a pause that waits for a tap must not hold the keyboard — it covers
    // the button the pause is waiting for.
    func releaseFocus() { answerFocused = false }

    func movedOn() {}

    func silence() { reader.hush() }
}

/// Where the run state is one of kern's own, the three figures the driver reads
/// come off `DrillRunProgress` — the drill spells none of them out.
extension DrillRunning {

    func questionIndex(_ run: Run) -> Int { Int(run.index) }

    func isFinished(_ run: Run) -> Bool { run.finished }

    var turnFeedback: TurnFeedback { run.feedback }

    /// The question the learner can see — nil while a pause stands in its place,
    /// so what a view does on a question's arrival (autoplay, focus) waits until
    /// the run goes on rather than playing under the pause.
    var shownQuestion: Int? { run.pause == nil ? Int(run.index) : nil }

    /// The run's screen, or kern's pause in its place (`DrillPacing`): Done
    /// closes the run as the ✕ does, keep practicing carries it on. Every drill
    /// screen stands in this, so it is also where each question is read aloud.
    func pausable(_ screen: some View) -> some View {
        Group {
            if run.pause != nil {
                DrillPauseView(run: run, onDone: { closeRun() }, onKeepPracticing: { keepPracticing() })
                    .transition(.opacity)
            } else {
                screen
            }
        }
        // why: the first question, and the one a pause hands back, arrive
        // without a dispatch — this says their prompt as they show.
        .onChange(of: run.reading, initial: true) { _, reading in
            reader.follow(reading, model: voiceModel)
        }
    }

    /// The run on its endless chrome — kern's tally as the counter, its outcomes as
    /// the segments — with the pause standing in when kern calls one. `asking` false
    /// is a run that opened with nothing to ask: the page that offered it gates on the
    /// same predicate, so this closes at once rather than showing a screen.
    @ViewBuilder
    func runScreen(asking: Bool = true, showsMuteButton: Bool = false,
                   speaksPastMute: Bool = false,
                   scoreLine: some View,
                   @ViewBuilder content: () -> some View) -> some View {
        if asking {
            pausable(SessionScaffold.endless(tally: run.tally,
                                             outcomes: run.outcomes.map { SessionOutcome($0) },
                                             showsMuteButton: showsMuteButton,
                                             speaksPastMute: speaksPastMute,
                                             scoreLine: scoreLine,
                                             onClose: { closeRun() },
                                             content: content))
        } else {
            Theme.colors.background.ignoresSafeArea().onAppear { dismiss() }
        }
    }
}

#if DEBUG
extension DrillRunning {

    /// The two run-through hooks (UserDefaults launch arguments) every drill
    /// takes, so a screenshot run needs no thumb: `-uitest-streak N` stands a run
    /// mid-streak, and `-uitest-close 1` leaves the way the ✕ leaves, so the tile
    /// the run drops on the page behind it can be photographed.
    ///
    /// Each drill's own hooks sit beside this call, never inside it.
    func uitestDriveRun() {
        let defaults = UserDefaults.standard
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 { seedAnswerStreak(preset) }
        if defaults.bool(forKey: "uitest-close") {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(400))
                closeRun()
            }
        }
    }
}
#endif

/// The Sprosse a `-uitest-<drill>-level N` launch argument opens a run on, which is
/// how a run-through reaches an outer Sprosse deterministically (kern clamps it);
/// 0 where none is given, and always in a release build.
func uitestOpeningSprosse(_ key: String) -> Int32 {
    #if DEBUG
    Int32(UserDefaults.standard.integer(forKey: key))
    #else
    0
    #endif
}
