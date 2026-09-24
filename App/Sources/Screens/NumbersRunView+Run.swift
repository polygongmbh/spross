import SwiftUI
import SprossKern

/// What the shared run driver (`DrillRunning`) needs to drive the slot drill:
/// kern's `NumbersRun`, its intent vocabulary, and the two store writes a close
/// books. Nothing here decides a rule — grading, the ramp, the amber verdicts
/// and what a close is worth are `NumbersRun`'s; every event becomes a
/// `NumbersIntent` and kern's next state replaces the run whole.
extension NumbersRunView: DrillRunning {

    // MARK: - The machine under this drill

    func reduce(_ run: NumbersRunState, _ intent: NumbersIntent) -> DrillStep<NumbersRunState> {
        let reduction = NumbersRun.shared.reduce(state: run, intent: intent,
                                                 normalizer: normalizer, rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    func typedMove(_ text: String) -> NumbersIntent? { NumbersIntent.InputChanged(text: text) }

    func submitMove(_ text: String) -> NumbersIntent? { NumbersIntent.Submit(text: text) }

    var confirmMove: NumbersIntent { NumbersIntent.ConfirmPending.shared }

    var advanceMove: NumbersIntent { NumbersIntent.AdvanceElapsed.shared }

    var resultTitle: LocalizedStringKey { mode.titleKey }

    func silence() { hushAnswer() }

    // why: the pending retry is canceled first, or it re-focuses 120 ms later.
    func releaseFocus() {
        focusRetry?.cancel()
        answerFocused = false
    }

    // MARK: - What the learner does beyond the field

    /// The whole numbers page, one tap away mid-run. Kern is told first: a
    /// look-up while the answer is still owed costs the Sprosse.
    func lookUp() {
        dispatch(NumbersIntent.LookUp.shared)
        showingReference = true
    }

    // MARK: - Close → summary

    /// The record and the Sprossen, both booked here: a run that is still going
    /// can still climb, so a Sprosse is only final once the run closes.
    // why: internal, not private — the +UITest hook closes a run the way the ✕ does.
    func closing() -> DrillClose<NumbersRunState> {
        let closed = NumbersRun.shared.close(state: run,
                                             standingRecord: Int32(TrainerRecords.best(for: mode.recordKey)),
                                             standingProgress: standingProgress)
        if let summary = closed.summary {
            // why: kern already measured the record — a timed run's is its score, and a
            // challenge sets none — so only a fallen one is written.
            if summary.newRecord { TrainerRecords.record(Int(summary.recordFigure), for: closed.recordKey) }
            TrainerProgress.book(closed.progressBookings)
        }
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects)
    }

    /// What the Sprosse store holds now for every exercise this run could book —
    /// kern compares against it so a Sprosse already earned is not fresh progress.
    private var standingProgress: [String: KotlinInt] {
        TrainerProgress.standing(mode.exercises.map { mode.progressKey(exercise: $0) })
    }
}
