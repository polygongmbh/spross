import SwiftUI
import SprossKern

/// What the shared run driver (`DrillRunning`) needs to drive the letter drill:
/// kern's `LetterDrillRun`, its intent vocabulary, and a close that files
/// nothing — the letter drill keeps no record store (D12). State lives on
/// LetterDrillView; split out purely for file size.
///
/// Grading itself is `LetterDrillRun.verdict`'s: a tile and a typed glyph are
/// exact after normalization, and dictation runs the whole catalog, because only
/// a catalog-wide grader can tell a slip of the played word from a different
/// word entirely (`kufunga` / `kufungua`). All this side still owes is the
/// grader itself — one strict normalizer over the learner's own cards.
extension LetterDrillView: DrillRunning {

    // MARK: - The machine under this drill

    func reduce(_ run: LetterDrillRunState,
                _ intent: LetterDrillIntent) -> DrillStep<LetterDrillRunState> {
        let reduction = LetterDrillRun.shared.reduce(state: run, intent: intent, rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    func questionIndex(_ run: LetterDrillRunState) -> Int { Int(run.index) }

    func isFinished(_ run: LetterDrillRunState) -> Bool { run.finished }

    func submitMove(_ text: String) -> LetterDrillIntent {
        LetterDrillIntent.Submit(text: text)
    }

    var advanceMove: LetterDrillIntent { LetterDrillIntent.AdvanceElapsed.shared }

    var turnFeedback: TurnFeedback { run.feedback }

    var resultTitle: LocalizedStringKey { "trainer.drill.letters" }

    func silence() { Pronouncer.shared.stop() }

    func movedOn() {
        #if DEBUG
        // The no-FSRS proof, printed where a review would have been booked.
        uitestBox("answered")
        #endif
    }

    // MARK: - What the learner does beyond the field

    /// One attempt per tile question — a second tap after the answer is in
    /// would be a retry, and the ramp has no verdict for that (kern's guard).
    func choose(_ glyph: String) {
        dispatch(LetterDrillIntent.Choose(glyph: glyph))
    }

    // MARK: - Close → back to the page that opened it

    /// An untouched run leaves nothing to report, and no record line either —
    /// the letter drill keeps no record store (D12).
    func closing() -> DrillClose<LetterDrillRunState> {
        let closed = LetterDrillRun.shared.close(state: run)
        return DrillClose(run: closed.state, summary: closed.summary, effects: closed.effects)
    }

    /// The STRICT drill grader with the whole join in view: a per-word slip
    /// budget alone would accept `kufungua` for `kufunga`, and only the
    /// catalog-wide grader withdraws that credit. Resolved once, when the run
    /// opens — dictation is the only stage that consults it.
    @MainActor static func dictationGrader(model: AppModel, language: String) -> CatalogAnswerGrader? {
        guard let info = model.languageInfo(language), let box = model.box else { return nil }
        let normalizer = AnswerNormalizer.companion.drill(answerLanguage: info)
        return CatalogAnswerGrader(normalizer: normalizer, cards: Array(box.cards.values))
    }
}

#if DEBUG
/// Run-through hooks of the letter drill beyond the two every drill takes
/// (`DrillRunning.uitestDriveRun`): they drive the screen and PRINT the states
/// the checklist asserts, because playback itself cannot be observed from
/// outside the process.
extension LetterDrillView {

    func seedStreak(_ streak: Int) {
        run = run.doCopy(config: run.config, task: run.task, index: run.index,
                         level: run.level, winsAtLevel: run.winsAtLevel,
                         core: run.core.doCopy(done: Int32(streak + 6),
                                               streak: Int32(streak),
                                               bestStreak: Int32(max(streak, 12)),
                                               missRun: run.core.missRun,
                                               outcomes: run.core.outcomes,
                                               solved: run.core.solved),
                         chosen: run.chosen, feedback: run.feedback,
                         finished: run.finished)
    }

    func uitestStart() {
        uitestDriveRun()
        let defaults = UserDefaults.standard
        if let pick = defaults.string(forKey: "uitest-letters-choose") { uitestChoose(pick) }
        if defaults.bool(forKey: "uitest-letters-replay") { uitestReplay() }
        if defaults.bool(forKey: "uitest-letters-probe") { uitestBox("open") }
    }

    /// `-uitest-letters-replay` — taps the replay button once the field has
    /// taken focus, which is the whole assertion: hearing the question again
    /// must not take the keyboard away from a learner mid-word.
    private func uitestReplay() {
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(1200))
            replayAction?()
        }
    }

    /// `-uitest-letters-choose right|wrong` — taps a tile after 0.6 s.
    private func uitestChoose(_ pick: String) {
        guard let task = current, let choices = task.choices,
              let glyph = pick == "wrong" ? choices.first(where: { $0 != task.display }) : task.display
        else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(600))
            choose(glyph)
        }
    }

    /// Every fire, with everything the autoplay assertions need: that the FIRST
    /// question of a fresh run played at all, on which trigger, and — the one
    /// gate this drill still has — whether a screen reader was standing.
    func uitestPlay(_ task: LetterDrillTask, pronunciation: Pronunciation,
                    trigger: Pronouncer.Trigger) {
        guard UserDefaults.standard.bool(forKey: "uitest-letters-probe") else { return }
        let gated = trigger != .tap && screenReaderOn
        let name = switch trigger {
        case .auto: "auto"
        case .essential: "essential"
        case .listening: "listening"
        case .tap: "tap"
        }
        // why: the analysis index rides along — the letters are the recordings
        // it exists for, and this is where a run shows it reached the player.
        print("""
            LetterDrill probe: play \(name) \
            stage \(task.stage.name) level \(run.level) kind \(task.promptKind.name) \
            text "\(task.promptText)" recording \(pronunciation.recordingPath ?? "none") \
            index \(pronunciation.gain) dB/\(pronunciation.leadMs) ms \
            screenReader \(screenReaderOn) \
            → \(gated ? "SUPPRESSED" : "played")
            """)
    }

    /// The replay tap must leave the keyboard where it was.
    func uitestFocus() {
        guard UserDefaults.standard.bool(forKey: "uitest-letters-probe") else { return }
        print("LetterDrill probe: after replay tap, answer field focused \(answerFocused)")
    }

    /// The no-FSRS proof: the same figures on the way in and on the way out.
    func uitestBox(_ moment: String) {
        guard UserDefaults.standard.bool(forKey: "uitest-letters-probe") else { return }
        guard let stats = model.stats else {
            print("LetterDrill probe: box \(moment) — none")
            return
        }
        print("""
            LetterDrill probe: box \(moment) active \(stats.activeCards) due \(stats.dueCards) \
            consolidated \(stats.consolidatedCards) reviewsToday \(model.today.map { Int($0.reviews) } ?? -1)
            """)
    }
}
#endif
