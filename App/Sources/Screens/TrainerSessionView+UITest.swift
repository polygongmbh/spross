#if DEBUG
import SwiftUI
import SprossKern

/// Run-through hooks of the slot drill (UserDefaults launch arguments), in the
/// shape the letter drill already uses: they drive the screen into a state a
/// screenshot run cannot reach with a thumb. State lives on TrainerSessionView;
/// split out purely for file size.
extension TrainerSessionView {

    /// The shared answer hooks (`UITestAnswer`), plus:
    /// `-uitest-level N` starts the run at that rung (numbers: digit count),
    /// which is the only way to photograph a long prompt without playing up to it;
    /// `-uitest-streak N` presets a running streak;
    /// `-uitest-summary 1` jumps straight to the close-summary state — add
    /// `-uitest-record 1` to drop the stored record first, so the run books one
    /// and the summary shows its record state;
    /// `-uitest-typo 1` renders the accepted-with-typo state.
    func uitestStart() {
        let defaults = UserDefaults.standard
        let presetLevel = defaults.integer(forKey: "uitest-level")
        if presetLevel > 0 {
            level = min(presetLevel, maxLevel)
            tasks = [Self.sampleTask(mode: mode, level: level, avoiding: nil)]
        }
        if let prefill = UITestAnswer.prefill { input = prefill }
        UITestAnswer.submitAfterBeat { submit() }
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 {
            streak = preset
            bestStreak = max(preset, 12)
            doneCount = preset + 6
        }
        if defaults.bool(forKey: "uitest-summary") {
            if defaults.bool(forKey: "uitest-record") { TrainerRecords.clear(mode.recordKey) }
            newRecord = TrainerRecords.record(bestStreak, for: mode.recordKey)
            showingSummary = true
        }
        if defaults.bool(forKey: "uitest-typo") {
            feedback = .almost(correctForm: current.display, reason: .typo)
            typoCorrection = current.display
        }
    }
}
#endif
