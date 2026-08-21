#if DEBUG
import SwiftUI
import SprossKern

/// Run-through hooks of the slot drill (UserDefaults launch arguments), in the
/// shape the letter drill already uses: they drive the screen into a state a
/// screenshot run cannot reach with a thumb. State lives on TrainerSessionView;
/// split out purely for file size.
///
/// The hooks that stand a run somewhere it did not play to write kern's state
/// directly (`seeded`), which is the whole of the license they take: everything
/// a thumb could do goes through an intent, exactly as a finger would.
extension TrainerSessionView {

    /// `-uitest-level N` starts the run's FIRST variant at that rung (numbers:
    /// digit count), the only way to photograph a long prompt without playing up to it;
    /// `-uitest-streak N` presets a running streak;
    /// `-uitest-misses N` presets the run's booked miss streak;
    /// `-uitest-close 1` closes the run the way the ✕ does, so the tile it leaves
    /// on the page behind it can be photographed — add `-uitest-record 1` to drop
    /// the stored record first, so the run books one and the tile shows it;
    /// `-uitest-typo 1` renders the accepted-with-typo state;
    /// `-uitest-reference 1` raises the numbers table the "?" opens.
    func uitestStart() {
        let defaults = UserDefaults.standard
        let presetLevel = defaults.integer(forKey: "uitest-level")
        if presetLevel > 0, let variant = mode.variants.first {
            let capped = min(presetLevel, Int(mode.maxLevel(variant: variant)))
            var levels = run.levels
            levels[variant] = KotlinInt(int: Int32(capped))
            run = run.seeded(current: mode.draw(levels: levels, avoiding: nil, rng: drillRandom),
                             levels: levels)
        }
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 {
            run = run.seeded(done: Int32(preset + 6), streak: Int32(preset),
                             bestStreak: Int32(max(preset, 12)))
        }
        // `-uitest-misses N` presets misses ALREADY booked, so a wrong answer on
        // top of it lands on the state where the way out is offered.
        let misses = max(0, defaults.integer(forKey: "uitest-misses"))
        if misses > 0 { run = run.seeded(missRun: Int32(misses)) }
        if defaults.bool(forKey: "uitest-close") {
            if defaults.bool(forKey: "uitest-record") { TrainerRecords.clear(mode.recordKey) }
            // why: through closeRun, not by seeding the page — the tile is worth
            // photographing only if the run really books what it claims to.
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(400))
                closeRun()
            }
        }
        if defaults.bool(forKey: "uitest-typo") {
            run = run.seeded(feedback: TurnFeedbackAlmost(correctForm: run.currentTask.display,
                                                         reason: .typo))
        }
        if defaults.bool(forKey: "uitest-reference") {
            // why: a sheet raised while the run under it is still animating in is
            // dropped — the tap this stands in for always comes after that.
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(600))
                lookUp()
            }
        }
    }
}

extension TrainerRunState {
    /// kern's `copy` with the run's own values standing in for everything a hook
    /// does not touch. No default argument crosses the ObjC boundary, so the
    /// fifteen unchanged fields are written once here rather than at four call sites.
    func seeded(current: DrawnTask? = nil,
                levels: [DrillVariant: KotlinInt]? = nil,
                done: Int32? = nil,
                streak: Int32? = nil,
                bestStreak: Int32? = nil,
                missRun: Int32? = nil,
                feedback: (any TurnFeedback)? = nil) -> TrainerRunState {
        doCopy(mode: mode,
               current: current ?? self.current,
               index: index,
               levels: levels ?? self.levels,
               winsAtLevel: winsAtLevel,
               bestLevels: bestLevels,
               done: done ?? self.done,
               streak: streak ?? self.streak,
               bestStreak: bestStreak ?? self.bestStreak,
               missRun: missRun ?? self.missRun,
               outcomes: outcomes,
               seenDigitCounts: seenDigitCounts,
               hintUsed: hintUsed,
               feedback: feedback ?? self.feedback,
               finished: finished)
    }
}
#endif
