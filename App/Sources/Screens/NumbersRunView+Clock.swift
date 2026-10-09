import SwiftUI
import SprossKern

/// A timed run's clock; kern sets the length (`TimedRun.SECONDS` plus `earnedSeconds`)
/// and handles `NumbersIntent.TimeUp`.
extension NumbersRunView {

    /// When the clock runs out, pushed out by every second an answer earned; nil until on screen.
    var deadline: Date? {
        clockStart?.addingTimeInterval(TimeInterval(Int(TimedRun.shared.SECONDS) + Int(run.earnedSeconds)))
    }

    /// Started with the run on screen and canceled with it.
    func runClock() async {
        guard run.timed, clockStart == nil else { return }
        clockStart = .now
        // why: an answer booked while asleep moves the deadline, so wake and look again.
        while let deadline, deadline > .now {
            try? await Task.sleep(for: .seconds(deadline.timeIntervalSinceNow))
            guard !Task.isCancelled else { return }
        }
        dispatch(NumbersIntent.TimeUp.shared)
    }

    /// The timed half of the score line: the seconds left, then the score so far.
    func timedParts(left: TimeInterval) -> [Text] {
        let seconds = TimedRun.shared.secondsLeft(remainingMillis: Int64(left * 1000))
        return [Text(verbatim: TimedRun.shared.clock(secondsLeft: seconds)), Text("trainer.run.score \(Int(run.score))")]
    }
}
