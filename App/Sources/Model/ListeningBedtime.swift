import Foundation
import SprossKern

/// The listening run's sleep timer, and the only clock in the whole mode.
///
/// Kern owns every judgment about it — which lengths may be picked
/// (`LISTENING_TIMER_CHOICES_MIN`), how the last two minutes ramp down
/// (`listeningGainDb`) and where the run is over (`listeningExpired`) — and
/// reads no clock itself, so what is left to this side is holding a deadline
/// and handing kern the milliseconds.
///
/// It does not stop dead: a hard cut is a change loud enough to wake someone,
/// which is the exact opposite of what a bedtime is for.
@MainActor
@Observable
final class ListeningBedtime {

    /// The picked length in minutes; 0 is OFF and the default, where the
    /// playlist laps for as long as it is left alone.
    private(set) var minutes = 0
    /// Whole minutes left, rounded UP so a bedtime never reads zero while words
    /// are still playing; nil while none is set. The ONLY thing the capsule
    /// watches — it moves once a minute, so nothing on screen redraws in
    /// between (a ticking clock is a clock you watch, which is the opposite of
    /// what a sleep timer is for).
    private(set) var minutesLeft: Int?
    /// Run on the main actor the moment the bedtime arrives.
    var onExpire: (() -> Void)?

    private var deadline: Date?
    private var ticker: Task<Void, Never>?

    /// What is left, nil while no bedtime is set — what kern is handed, read
    /// off the deadline at the moment it is asked rather than stored, so the
    /// fade stays exact without a stored millisecond anything can observe.
    var remainingMs: Int64? { deadline.map { Self.millis(until: $0) } }

    /// [delta] steps along kern's list, wrapping at both ends — one cycling
    /// capsule rather than a picker, because "let it run while I fall asleep"
    /// is not an ask anybody answers to the minute. Backwards is VoiceOver's
    /// swipe down, the same list walked the other way.
    func step(_ delta: Int) {
        let choices = LISTENING_TIMER_CHOICES_MIN.map { Int(truncating: $0) }
        guard !choices.isEmpty else { return }
        let here = choices.firstIndex(of: minutes) ?? 0
        minutes = choices[((here + delta) % choices.count + choices.count) % choices.count]
        deadline = minutes > 0 ? Date().addingTimeInterval(TimeInterval(minutes * 60)) : nil
        startTicking()
    }

    /// Kern's ramp across the WHOLE bedtime — 0 dB, and no arithmetic at all,
    /// while none is set. The length is handed in with what is left of it: the
    /// ramp is a fraction of the run, so a 15-minute bedtime dims at four times
    /// the rate of an hour and both end in the same place.
    var fadeDb: Double {
        guard let remainingMs else { return 0 }
        return listeningGainDb(msRemaining: remainingMs, totalMs: Int64(minutes) * 60_000)
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
