import Foundation
import SprossKern

/// The listening run's sleep timer, and the only clock in the whole mode.
///
/// Kern owns every judgment about it — what a tap on the chip leaves standing
/// (`listeningTimerStepMs`), how the whole run ramps down (`listeningGainDb`)
/// and where the run is over (`listeningExpired`) — and reads no clock itself,
/// so what is left to this side is holding a deadline and handing kern the
/// milliseconds.
///
/// It does not stop dead: a hard cut is a change loud enough to wake someone,
/// which is the exact opposite of what a bedtime is for.
@MainActor
@Observable
final class ListeningBedtime {

    /// Whole minutes left, rounded UP so a bedtime never reads zero while words
    /// are still playing; nil while none is set. The ONLY thing the capsule
    /// watches — it moves once a minute, so nothing on screen redraws in
    /// between (a ticking clock is a clock you watch, which is the opposite of
    /// what a sleep timer is for).
    private(set) var minutesLeft: Int?
    /// Run on the main actor the moment the bedtime arrives.
    var onExpire: (() -> Void)?

    private var deadline: Date?
    /// The stretch the CURRENT bedtime runs over — what kern's ramp spans, and
    /// what every tap resets: a bedtime extended at midnight is a new stretch,
    /// not the old one with a longer tail, so the fade starts over from full.
    private var totalMs: Int64 = 0
    private var ticker: Task<Void, Never>?

    /// Whether a bedtime is set at all — off, the playlist laps for as long as
    /// it is left alone.
    var isSet: Bool { deadline != nil }

    /// What is left, nil while no bedtime is set — what kern is handed, read
    /// off the deadline at the moment it is asked rather than stored, so the
    /// fade stays exact without a stored millisecond anything can observe.
    var remainingMs: Int64? { deadline.map { Self.millis(until: $0) } }

    /// One tap adds kern's step to WHAT IS LEFT; a swipe down takes the same
    /// off it, and past the end there is only OFF. Kern does the arithmetic
    /// (`listeningTimerStepMs`) — all this holds is the moment it lands on.
    func step(_ delta: Int) {
        totalMs = listeningTimerStepMs(msRemaining: remainingMs ?? 0, steps: Int32(delta))
        deadline = totalMs > 0 ? Date().addingTimeInterval(TimeInterval(totalMs) / 1000) : nil
        startTicking()
    }

    /// Long-press: the bedtime is cleared and the run laps again from where it
    /// is — the one gesture that must reach zero in a single move rather than
    /// walking the minutes down, which the chip cannot do.
    func turnOff() {
        totalMs = 0
        deadline = nil
        startTicking()
    }

    /// Kern's ramp across the WHOLE bedtime — 0 dB, and no arithmetic at all,
    /// while none is set. The length is handed in with what is left of it: the
    /// ramp is a fraction of the run, so a 15-minute bedtime dims at four times
    /// the rate of an hour and both end in the same place.
    var fadeDb: Double {
        guard let remainingMs else { return 0 }
        return listeningGainDb(msRemaining: remainingMs, totalMs: totalMs)
    }

    /// How far into the current stretch the run is, and how long that stretch
    /// runs — the pair a lock screen draws a progress bar from. nil while no
    /// bedtime is set: a playlist that laps has nothing to be a fraction of.
    var progress: (elapsed: TimeInterval, total: TimeInterval)? {
        guard let remainingMs, totalMs > 0 else { return nil }
        let total = TimeInterval(totalMs) / 1000
        return (total - TimeInterval(remainingMs) / 1000, total)
    }

    /// Whether the bedtime has arrived. Kern owns where the line is; what to do
    /// about it is the run's.
    var expired: Bool {
        guard let remainingMs else { return false }
        return listeningExpired(msRemaining: remainingMs)
    }

    func stop() {
        ticker?.cancel()
        ticker = nil
    }

    /// Wakes when the MINUTE the capsule shows changes, and on the deadline
    /// itself — nothing here needs a per-second clock: the number moves a
    /// minute at a time and the fade reads the deadline whenever it is asked.
    /// No bedtime, no ticker.
    private func startTicking() {
        ticker?.cancel()
        guard let deadline else {
            ticker = nil
            minutesLeft = nil
            return
        }
        ticker = Task { @MainActor [weak self] in
            while !Task.isCancelled, let self {
                minutesLeft = Self.minutes(until: deadline)
                if expired {
                    stop()
                    onExpire?()
                    return
                }
                try? await Task.sleep(for: .milliseconds(Self.msUntilTheMinuteTurns(deadline)))
            }
        }
    }

    private static func millis(until deadline: Date) -> Int64 {
        Int64(max(0, deadline.timeIntervalSinceNow * 1000))
    }

    private static func minutes(until deadline: Date) -> Int {
        Int((Double(millis(until: deadline)) / 60_000).rounded(.up))
    }

    /// How long the shown minute still stands: what is left, less the whole
    /// minutes that will still be left after it turns. On the last minute that
    /// is the whole remainder, so the final wake IS the deadline.
    private static func msUntilTheMinuteTurns(_ deadline: Date) -> Int64 {
        let left = millis(until: deadline)
        let whole = Int64(max(minutes(until: deadline) - 1, 0))
        return max(left - whole * 60_000, 50)
    }
}
