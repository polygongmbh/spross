import SwiftUI
import SprossKern

/// What the shared run driver (`DrillRunning`) needs to drive the sentence
/// scramble: kern's `SentenceScrambleRun`, its intent vocabulary, and a close
/// that files the ladder the run climbed. State and content live on
/// SentenceScrambleView; split out purely for file size, the way the word
/// scramble splits its own off.
///
/// This is the drill whose answer is an ARRANGEMENT, and the two places that
/// shows are parameters of the driver rather than a driver of its own: there is
/// no text to hold and no submit to put to kern, so both decline below. Kern
/// grades against nothing device-side — the answer is a permutation of atoms it
/// dealt itself, so this side owes it no normalizer either.
extension SentenceScrambleView: DrillRunning {

    // MARK: - The two things this drill has none of

    /// No field: the words are given and only their order is withheld, so there
    /// is never text of the learner's for the driver to carry or to clear.
    var input: String {
        get { "" }
        nonmutating set {}
    }

    /// No keyboard either, so no pause can be waiting behind one.
    var answerFocused: Bool {
        get { false }
        nonmutating set {}
    }

    // MARK: - The machine under this drill

    func reduce(_ run: SentenceScrambleRunState,
                _ intent: SentenceScrambleIntent) -> DrillStep<SentenceScrambleRunState> {
        let reduction = SentenceScrambleRun.shared.reduce(state: run, intent: intent,
                                                          rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    func questionIndex(_ run: SentenceScrambleRunState) -> Int { Int(run.index) }

    func isFinished(_ run: SentenceScrambleRunState) -> Bool { run.finished }

    /// Placing the last atom IS the answer, so there is nothing a check tap
    /// could put to kern that the bank has not already put there.
    func submitMove(_ text: String) -> SentenceScrambleIntent? { nil }

    var confirmMove: SentenceScrambleIntent { SentenceScrambleIntent.ConfirmPending.shared }

    var advanceMove: SentenceScrambleIntent { SentenceScrambleIntent.AdvanceElapsed.shared }

    var turnFeedback: TurnFeedback { run.feedback }

    var resultTitle: LocalizedStringKey { "trainer.drill.sentenceScramble" }

    func silence() { Pronouncer.shared.stop() }

    // MARK: - What the learner does instead of typing

    /// A bank slot tapped: the atom joins the end of the arrangement, and the
    /// last one grades.
    func place(_ index: Int) {
        dispatch(SentenceScrambleIntent.PlaceAtom(index: Int32(index)))
    }

    /// An arranged slot tapped: the atom goes back, while the order is still owed.
    func take(_ index: Int) {
        dispatch(SentenceScrambleIntent.ReturnAtom(index: Int32(index)))
    }

    // MARK: - Close → back to the hub that opened it

    /// An untouched run leaves nothing to report, and no record line either —
    /// arrangement is not recall, so this drill keeps no streak record. What it
    /// DOES file is the ladder: the next run opens on the lowest Sprosse the
    /// mask does not hold, so a Sprosse climbed clean is never asked for twice.
    func closing() -> DrillClose<SentenceScrambleRunState> {
        let closed = SentenceScrambleRun.shared.close(state: run)
        TrainerProgress.bookCleared(closed.clearedSprossen, for: storageKey)
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects)
    }
}

#if DEBUG
extension SentenceScrambleView {

    func seedStreak(_ streak: Int) {
        run = run.doCopy(config: run.config, task: run.task, placed: run.placed,
                         index: run.index, level: run.level, bestLevel: run.bestLevel,
                         winsAtLevel: run.winsAtLevel,
                         clearedSprossen: run.clearedSprossen, blemished: run.blemished,
                         core: run.core.doCopy(done: Int32(streak + 6),
                                               streak: Int32(streak),
                                               bestStreak: Int32(max(streak, 12)),
                                               missRun: run.core.missRun,
                                               outcomes: run.core.outcomes,
                                               solved: run.core.solved),
                         feedback: run.feedback, finished: run.finished)
    }
}
#endif
