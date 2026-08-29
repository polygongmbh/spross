import SwiftUI
import SprossKern

/// The RUN half of the atlas drill: how an event reaches kern, what comes back,
/// and what a close leaves behind — plus the run-through hooks that drive it.
/// State lives on CountryDrillView; split out purely for file size, the way both
/// sibling drills split theirs off.
///
/// Grading itself is `CountryDrillRun.grade`'s, against every form kern accepts
/// (`CountryDrillTask.accepted`: each spelling of each valid answer, and for the
/// union kinds every language a country speaks). All this side still owes is the
/// grader — one STRICT drill normalizer for the language the answer is owed in.
extension CountryDrillView {

    // MARK: - Driving the run

    func dispatch(_ intent: CountryDrillIntent) {
        let reduction = CountryDrillRun.shared.reduce(state: run, intent: intent, rng: drillRandom)
        let moved = reduction.state.index != run.index
        // why: the field is ours, so kern cannot clear it — and the text has to
        // go in the SAME transaction as the question, or the next prompt renders
        // one frame carrying the last one's answer.
        if moved { input = "" }
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip)
            : .easeOut(duration: 0.25)
        withAnimation(animation) { run = reduction.state }
        for effect in reduction.effects { apply(effect) }
    }

    private func apply(_ effect: DrillEffect) {
        switch onEnum(of: effect) {
        case .armAdvance(let beat):
            // why: AutoAdvance skips the timer under a screen reader — it
            // truncates the correctness announcement and moves the screen under
            // the user, and the branches render "Weiter" there instead.
            AutoAdvance.schedule(beat.tier, &autoAdvance) {
                dispatch(CountryDrillIntent.AdvanceElapsed.shared)
            }
        case .cancelAdvance:
            autoAdvance?.cancel()
        case .tone(let cue):
            switch cue.kind {
            case .correct: DLSound.correct()
            case .wrong: DLSound.wrong()
            case .reveal: DLSound.reveal()
            }
        case .releaseFocus:
            // why: the amber hold waits for a tap, and a held keyboard covers
            // the button it waits for.
            answerFocused = false
        case .silence:
            // D5: the name belongs to the question being left.
            hushAnswer()
        }
    }

    // MARK: - What the learner does

    /// "Finishing the name IS the answer" — every keystroke is offered to kern,
    /// which decides whether it approves, withdraws an approval, or is ignored
    /// because a pause is standing.
    func typed() {
        dispatch(CountryDrillIntent.InputChanged(text: input))
    }

    func submit() {
        dispatch(CountryDrillIntent.Submit(text: input))
    }

    /// ONE primary action: an empty field reveals — the CARD carries the answer
    /// and the question books a miss — a typed one checks.
    func checkOrReveal() {
        if input.isBlankAnswer {
            dispatch(CountryDrillIntent.Reveal.shared)
        } else {
            submit()
        }
    }

    /// The tap that books whatever the feedback already said.
    func confirm() {
        dispatch(CountryDrillIntent.ConfirmPending.shared)
    }

    // MARK: - Close → back to the page that opened it

    /// X during a run: kern books a pending answer exactly as the tap would,
    /// then hands the figures and the rung it reached back. An untouched run
    /// leaves nothing to report.
    func closeRun() {
        let closed = CountryDrillRun.shared.close(state: run,
                                                   standingRecord: Int32(TrainerRecords.best(for: storageKey)))
        run = closed.state
        for effect in closed.effects { apply(effect) }
        // why: the rung buys nothing (the drill is ungated); it is what the
        // overview reads back, and what Fast is priced against.
        TrainerProgress.record(Int(closed.bestLevel), for: storageKey)
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        answerFocused = false
        TrainerRecords.record(Int(summary.bestStreak), for: storageKey)
        // why: the cheer marks the record, not the end of a run — confetti and
        // cheer are one thing (`docs/design.md`), and the tile rains the one.
        if summary.newRecord { DLSound.cheer() }
        onFinish(DrillRunResult(summary, title: "trainer.countries"))
        dismiss()
    }

    /// The STRICT drill normalizer, built exactly as both sibling drills build
    /// theirs: no article leniency (the atlas authors the article in the name
    /// and the bare form beside it), one slip per word. Resolved once, when the
    /// run opens, for the language the answer is owed IN.
    @MainActor static func normalizer(model: AppModel, content: CountryDrillContent,
                                      reverse: Bool) -> AnswerNormalizer? {
        let language = CountryDrill.shared.answerLanguage(content: content, reverse: reverse)
        return model.languageInfo(language).map { AnswerNormalizer.companion.drill(answerLanguage: $0) }
    }
}

#if DEBUG
/// Run-through hooks (UserDefaults launch arguments), in the shape both sibling
/// drills already use: they drive the screen so a screenshot run needs no thumb.
extension CountryDrillView {

    func uitestStart() {
        let defaults = UserDefaults.standard
        // `-uitest-streak N`: a run mid-streak, which a screenshot run has no
        // thumb to reach.
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 {
            run = run.doCopy(config: run.config, task: run.task, index: run.index,
                             level: run.level, bestLevel: run.bestLevel,
                             winsAtLevel: run.winsAtLevel, done: Int32(preset + 6),
                             streak: Int32(preset), bestStreak: Int32(max(preset, 12)),
                             missRun: run.missRun, outcomes: run.outcomes,
                             feedback: run.feedback, otherWord: run.otherWord,
                             finished: run.finished)
        }
        // `-uitest-close 1`: leave the way the ✕ leaves, so the tile the run
        // drops on the page behind it can be photographed.
        if defaults.bool(forKey: "uitest-close") {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(400))
                closeRun()
            }
        }
    }
}
#endif
