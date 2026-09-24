import SwiftUI
import SprossKern

/// The clock of a timed run. Kern names how long it lasts (`TimedRun.SECONDS`)
/// and what running out does (`NumbersIntent.TimeUp`: the run is over, and the
/// close books a pending answer as the ✕ would); the timer and the seconds on
/// screen are this side's. State lives on NumbersRunView; split out for file size.
extension NumbersRunView {

    /// Started once the run is on screen, and cancelled with it — a run closed
    /// early must not be told its time is up.
    func runClock() async {
        guard run.timed, deadline == nil else { return }
        let seconds = Int(TimedRun.shared.SECONDS)
        deadline = .now.addingTimeInterval(TimeInterval(seconds))
        try? await Task.sleep(for: .seconds(seconds))
        guard !Task.isCancelled else { return }
        dispatch(NumbersIntent.TimeUp.shared)
    }

    /// The timed half of the score line: the seconds left, then the score so far.
    func timedParts(left: TimeInterval) -> [Text] {
        let seconds = max(0, Int(left.rounded(.up)))
        let clock = String(format: "%d:%02d", seconds / 60, seconds % 60)
        return [Text(verbatim: "⏱ \(clock)"), Text("trainer.run.score \(Int(run.score))")]
    }
}
