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

    /// Kern's whole run state, replaced whole by every reduction.
    associatedtype Run
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

    /// A live keystroke, in this drill's words. nil where a keystroke means
    /// nothing until it is submitted — the letters ladder grades whole answers.
    func typedMove(_ text: String) -> Move?

    /// Check and Enter alike. nil where there is no field to check — placing
    /// the last atom IS the answer on the drill whose question is an order
    /// rather than a spelling.
    func submitMove(_ text: String) -> Move?

    /// The explicit tap that books whatever the feedback already said.
    var confirmMove: Move { get }

    /// The armed beat elapsed.
    var advanceMove: Move { get }

    /// Where kern says the answer stands; the field's face is read off it.
    var turnFeedback: TurnFeedback { get }

    // MARK: - What kern asks of the device

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

    #if DEBUG
    /// `-uitest-streak N`: stand the run mid-streak.
    func seedStreak(_ streak: Int)
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
            : .easeOut(duration: 0.25)
        withAnimation(animation) { run = step.run }
        for effect in step.effects { apply(effect) }
        // Nothing left to ask: hand the run back, never repeat a question.
        if isFinished(step.run) { closeRun() }
    }

    func apply(_ effect: DrillEffect) {
        DrillEffects.apply(effect, advance: &autoAdvance,
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
        // why: the cheer marks the record, not the end of a run — confetti and
        // cheer are one thing (`docs/design.md`), and the tile rains the one.
        // A drill with no record store never reports one.
        if summary.newRecord { Sound.cheer() }
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
}

/// Where the run state is one of kern's own, the three figures the driver reads
/// come off `DrillRunProgress` — the drill spells none of them out.
extension DrillRunning where Run: DrillRunProgress {

    func questionIndex(_ run: Run) -> Int { Int(run.index) }

    func isFinished(_ run: Run) -> Bool { run.finished }

    var turnFeedback: TurnFeedback { run.feedback }
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
        if preset > 0 { seedStreak(preset) }
        if defaults.bool(forKey: "uitest-close") {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(400))
                closeRun()
            }
        }
    }
}
#endif
