import SwiftUI
import SprossKern

/// A timed run's clock; kern sets the length (`TimedRun.SECONDS`) and handles `NumbersIntent.TimeUp`.
extension NumbersRunView {

    /// Started with the run on screen and cancelled with it.
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
