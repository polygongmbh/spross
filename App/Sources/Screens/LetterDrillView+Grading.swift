import SwiftUI
import SprossKern

/// The RUN half of the letter drill: how an event reaches kern, what comes
/// back, and what a close leaves behind — plus the run-through hooks that drive
/// it. State lives on LetterDrillView; split out purely for file size.
///
/// Grading itself is `LetterDrillRun.verdict`'s: a tile and a typed glyph are
/// exact after normalization, and dictation runs the whole catalog, because only
/// a catalog-wide grader can tell a slip of the played word from a different
/// word entirely (`kufunga` / `kufungua`). All this side still owes is the
/// grader itself — one strict normalizer over the learner's own cards.
extension LetterDrillView {

    // MARK: - Driving the run

    func dispatch(_ intent: LetterDrillIntent) {
        let reduction = LetterDrillRun.shared.reduce(state: run, intent: intent, rng: drillRandom)
        let moved = reduction.state.index != run.index
        if moved {
            // why: cleared in the SAME transaction as the question — the next
            // one must never render a frame carrying the last one's answer.
            input = ""
            #if DEBUG
            // The no-FSRS proof, printed where a review would have been booked.
            uitestBox("answered")
            #endif
        }
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip)
            : .easeOut(duration: 0.25)
        withAnimation(animation) { run = reduction.state }
        for effect in reduction.effects { apply(effect) }
        // Nothing left to ask: hand the run back, never sit on a blank card.
        if reduction.state.finished { closeRun() }
    }

    private func apply(_ effect: DrillEffect) {
        switch onEnum(of: effect) {
        case .armAdvance(let beat):
            // why: AutoAdvance skips the timer under a screen reader — it
            // truncates the correctness announcement and moves the screen under
            // the user, and the branches render "Weiter" there instead.
            AutoAdvance.schedule(beat.tier, &autoAdvance) {
                dispatch(LetterDrillIntent.AdvanceElapsed.shared)
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
            // why: both amber holds wait for a tap, and a held keyboard covers
            // the button they wait for.
            answerFocused = false
        case .silence:
            // D5: the clip belongs to the question being left.
            Pronouncer.shared.stop()
        }
    }

    // MARK: - What the learner does

    /// One attempt per tile question — a second tap after the answer is in
    /// would be a retry, and the ramp has no verdict for that (kern's guard).
    func choose(_ glyph: String) {
        dispatch(LetterDrillIntent.Choose(glyph: glyph))
    }

    func submit() {
        dispatch(LetterDrillIntent.Submit(text: input))
    }

    /// ONE primary action: an empty field reveals — the CARD carries the answer
    /// and the question books a miss — a typed one checks.
    func checkOrReveal() {
        if input.isBlankAnswer {
            dispatch(LetterDrillIntent.Reveal.shared)
        } else {
            submit()
        }
    }

    // MARK: - Close → back to the page that opened it

    /// X during a run: kern books a pending answer exactly as the tap would,
    /// then hands the figures back. An untouched run leaves nothing to report,
    /// and no record line — the letter drill keeps no record store (D12).
    func closeRun() {
        let closed = LetterDrillRun.shared.close(state: run)
        run = closed.state
        for effect in closed.effects { apply(effect) }
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        answerFocused = false
        onFinish(DrillRunResult(summary, title: "trainer.skill.letters"))
        dismiss()
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
/// Run-through hooks (UserDefaults launch arguments), in the shape the slot
/// drill and the pronunciation probe already use: they drive the screen and
/// PRINT the states the checklist asserts, because playback itself cannot be
/// observed from outside the process.
extension LetterDrillView {

    func uitestStart() {
        let defaults = UserDefaults.standard
        // `-uitest-streak N`, the slot drill's figure under the slot drill's
        // name: a run mid-streak, which a screenshot run has no thumb to reach.
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 {
            run = run.doCopy(config: run.config, task: run.task, index: run.index,
                             level: run.level, winsAtLevel: run.winsAtLevel,
                             done: Int32(preset + 6), streak: Int32(preset),
                             bestStreak: Int32(max(preset, 12)), missRun: run.missRun,
                             outcomes: run.outcomes, solved: run.solved,
                             chosen: run.chosen, feedback: run.feedback,
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
