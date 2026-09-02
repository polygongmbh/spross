import SwiftUI
import SprossKern

/// The RUN half of TrainerSessionView: how an event reaches kern, what comes
/// back, and what a close books. Nothing here decides a rule — grading, the
/// ramp, the amber verdicts and the two store writes are `TrainerRun`'s. Every
/// event becomes a `TrainerIntent`, kern's next state replaces the run whole,
/// and its effects are the only things allowed to reach outside it (the pattern
/// SessionView+Turn.swift sets for the review session).
extension TrainerSessionView {

    // MARK: - Driving the run

    func dispatch(_ intent: TrainerIntent) {
        let reduction = TrainerRun.shared.reduce(state: run, intent: intent,
                                                 normalizer: normalizer, rng: drillRandom)
        let moved = reduction.state.index != run.index
        // why: the field is ours, so kern cannot clear it — and the text has to
        // go in the SAME transaction as the question, or the next prompt renders
        // one frame carrying the last one's answer.
        if moved { input = "" }
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .cardFlip)
            : .easeOut(duration: 0.25)
        withAnimation(animation) { run = reduction.state }
        for effect in reduction.effects { apply(effect) }
        // Nothing left to ask: hand the run back, never repeat a question.
        if reduction.state.finished { closeRun() }
    }

    private func apply(_ effect: DrillEffect) {
        switch onEnum(of: effect) {
        case .armAdvance(let beat):
            // why: AutoAdvance skips the timer under a screen reader (it
            // truncates the announcement and moves the screen), and the branch
            // renders "Weiter" there — same booking, through ConfirmPending.
            AutoAdvance.schedule(beat.tier, &autoAdvance) {
                dispatch(TrainerIntent.AdvanceElapsed.shared)
            }
        case .cancelAdvance:
            autoAdvance?.cancel()
        case .tone(let cue):
            switch cue.kind {
            case .correct: Sound.correct()
            case .wrong: Sound.wrong()
            case .reveal: Sound.reveal()
            }
        case .releaseFocus:
            // why: a pause that waits for a tap must not hold the keyboard —
            // it covers the button the pause is waiting for. The pending retry
            // is canceled first, or it re-focuses 120 ms later.
            focusRetry?.cancel()
            answerFocused = false
        case .silence:
            hushAnswer()
        }
    }

    // MARK: - What the learner does

    /// "Finishing the word IS the answer" — every keystroke is offered to kern,
    /// which decides whether it approves, withdraws an approval, or is ignored
    /// because an amber hold is standing.
    func typed() {
        dispatch(TrainerIntent.InputChanged(text: input))
    }

    func submit() {
        dispatch(TrainerIntent.Submit(text: input))
    }

    /// ONE primary action: an empty field reveals, a typed one checks.
    func checkOrReveal() {
        if input.isBlankAnswer {
            dispatch(TrainerIntent.Reveal.shared)
        } else {
            submit()
        }
    }

    /// The whole numbers page, one tap away mid-run. Kern is told first: a
    /// look-up while the answer is still owed costs the Sprosse.
    func lookUp() {
        dispatch(TrainerIntent.LookUp.shared)
        showingReference = true
    }

    // MARK: - Close → summary

    /// X during a run: kern books whatever was pending, hands back the figures
    /// and the two store writes, and an untouched run just closes.
    // why: internal, not private — the +UITest hook closes a run the way the ✕ does.
    func closeRun() {
        let closed = TrainerRun.shared.close(state: run,
                                             standingRecord: Int32(TrainerRecords.best(for: mode.recordKey)),
                                             standingProgress: standingProgress)
        run = closed.state
        for effect in closed.effects { apply(effect) }
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        answerFocused = false
        TrainerRecords.record(Int(summary.bestStreak), for: closed.recordKey)
        // why: booked here, alongside the record, because a run that is still
        // going can still climb — a Sprosse is only final once the run closes.
        TrainerProgress.book(closed.progressBookings)
        // why: the cheer marks the record, not the end of a run — closing a
        // drill is a dozen-times-an-evening event and owes no fanfare.
        if summary.newRecord { Sound.cheer() }
        onFinish(DrillRunResult(summary, title: mode.titleKey))
        dismiss()
    }

    /// What the Sprosse store holds now for every variant this run could book —
    /// kern compares against it so a Sprosse already earned is not fresh progress.
    private var standingProgress: [String: KotlinInt] {
        TrainerProgress.standing(mode.variants.map { mode.progressKey(variant: $0) })
    }
}
