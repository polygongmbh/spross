import SwiftUI
import SprossKern

/// A timed run's clock; kern sets the length (`TimedRun.SECONDS` plus `earnedSeconds`),
/// when it stands still (`clockStopped`) and handles `NumbersIntent.TimeUp`.
extension NumbersRunView {

    /// Seconds left at `date`, frozen while kern stops the clock; nil until on screen.
    func clockLeft(at date: Date) -> TimeInterval? {
        guard let clockStart else { return nil }
        let running = (clockStoppedAt ?? date).timeIntervalSince(clockStart) - clockStoppedFor
        return TimeInterval(TimedRun.shared.remainingMillis(runningMillis: Int64(running * 1000),
                                                            earnedSeconds: run.earnedSeconds)) / 1000
    }

    /// Started with the run on screen and canceled with it.
    func runClock() async {
        guard run.timed, clockStart == nil else { return }
        clockStart = .now
        // why: an earned second or a stopped stretch moves the end, so wake and look again.
        while let left = clockLeft(at: .now), left > 0 {
            try? await Task.sleep(for: .seconds(left))
            guard !Task.isCancelled else { return }
        }
        dispatch(NumbersIntent.TimeUp.shared)
    }

    /// Books the stretch a shown miss held the clock, so the time left resumes where it stood.
    func clockStopChanged(_ stopped: Bool) {
        if stopped {
            clockStoppedAt = .now
        } else if let stoppedAt = clockStoppedAt {
            clockStoppedFor += Date.now.timeIntervalSince(stoppedAt)
            clockStoppedAt = nil
        }
    }

    /// The timed half of the score line: the seconds left, then the score so far.
    func timedParts(left: TimeInterval) -> [Text] {
        let seconds = TimedRun.shared.secondsLeft(remainingMillis: Int64(left * 1000))
        return [Text(verbatim: TimedRun.shared.clock(secondsLeft: seconds)), Text("trainer.run.score \(Int(run.score))")]
    }
}
