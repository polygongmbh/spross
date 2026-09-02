import SwiftUI
import SprossKern

/// The RUN half of a typed drill: how an event reaches kern, what comes back,
/// and what a close leaves behind — plus the run-through hooks that drive it.
/// State lives on DrillRunView; split out purely for file size, the way the
/// letter drill splits its off.
///
/// Grading itself is kern's, against every form the run accepts (each spelling
/// of each valid answer; on the dates ladder every pattern filling of an
/// assembled date). All this side still owes is the grader — one STRICT drill
/// normalizer for the language the answer is owed in.
extension DrillRunView {

    // MARK: - Driving the run

    func dispatch(_ move: DrillMove) {
        let step = Face.reduce(run, move)
        let next = Face.snapshot(step.run)
        let moved = next.index != current.index
        // why: the field is ours, so kern cannot clear it — and the text has to
        // go in the SAME transaction as the question, or the next prompt renders
        // one frame carrying the last one's answer.
        if moved { input = "" }
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .cardFlip)
            : .easeOut(duration: 0.25)
        withAnimation(animation) { run = step.run }
        for effect in step.effects { apply(effect) }
        // Nothing left to ask: hand the run back, never repeat a question.
        if next.finished { closeRun() }
    }

    private func apply(_ effect: DrillEffect) {
        switch onEnum(of: effect) {
        case .armAdvance(let beat):
            // why: AutoAdvance skips the timer under a screen reader — it
            // truncates the correctness announcement and moves the screen under
            // the user, and the branches render "Weiter" there instead.
            AutoAdvance.schedule(beat.tier, &autoAdvance) {
                dispatch(.advanced)
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
            // why: the amber hold waits for a tap, and a held keyboard covers
            // the button it waits for.
            answerFocused = false
        case .silence:
            // D5: the reading belongs to the question being left.
            hushAnswer()
        }
    }

    // MARK: - What the learner does

    /// "Finishing the answer IS the answer" — every keystroke is offered to
    /// kern, which decides whether it approves, withdraws an approval, or is
    /// ignored because a pause is standing.
    func typed() {
        dispatch(.typed(input))
    }

    func submit() {
        dispatch(.submitted(input))
    }

    /// ONE primary action: an empty field reveals — the CARD carries the answer
    /// and the question books a miss — a typed one checks.
    func checkOrReveal() {
        if input.isBlankAnswer {
            dispatch(.revealed)
        } else {
            submit()
        }
    }

    /// The tap that books whatever the feedback already said.
    func confirm() {
        dispatch(.confirmed)
    }

    // MARK: - Close → back to the page that opened it

    /// X during a run: kern books a pending answer exactly as the tap would,
    /// then hands the figures and the Sprosse it reached back. An untouched run
    /// leaves nothing to report.
    func closeRun() {
        let closed = Face.close(run, standingRecord: TrainerRecords.best(for: storageKey))
        run = closed.run
        for effect in closed.effects { apply(effect) }
        // why: the Sprosse buys nothing (the drill is ungated); it is what the
        // overview reads back, and what Fast is priced against.
        TrainerProgress.record(closed.bestLevel, for: storageKey)
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        answerFocused = false
        TrainerRecords.record(Int(summary.bestStreak), for: storageKey)
        // why: the cheer marks the record, not the end of a run — confetti and
        // cheer are one thing (`docs/design.md`), and the tile rains the one.
        if summary.newRecord { Sound.cheer() }
        onFinish(DrillRunResult(summary, title: Face.resultTitle))
        dismiss()
    }

    /// The STRICT drill normalizer, built exactly as the letter drill builds
    /// its own: no article leniency (the material authors its own article, and
    /// its variants are what admit the accusative), one slip per word. Resolved
    /// once, when the run opens, for the language the answer is owed IN.
    @MainActor static func normalizer(model: AppModel, content: Face.Content,
                                      reverse: Bool) -> AnswerNormalizer? {
        let language = Face.answerLanguage(content: content, reverse: reverse)
        return model.languageInfo(language).map { AnswerNormalizer.companion.drill(answerLanguage: $0) }
    }
}

#if DEBUG
/// Run-through hooks (UserDefaults launch arguments), in the shape every drill
/// uses: they drive the screen so a screenshot run needs no thumb.
extension DrillRunView {

    func uitestStart() {
        let defaults = UserDefaults.standard
        // `-uitest-streak N`: a run mid-streak, which a screenshot run has no
        // thumb to reach.
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 {
            run = Face.seedStreak(run, preset)
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
