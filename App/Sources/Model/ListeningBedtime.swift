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
    /// What is left, nil while no bedtime is set — what the chip reads and what
    /// kern is handed.
    private(set) var remainingMs: Int64?
    /// Run on the main actor the moment the bedtime arrives.
    var onExpire: (() -> Void)?

    private var deadline: Date?
    private var ticker: Task<Void, Never>?

    /// The next length on kern's list, wrapping back to off — one cycling chip
    /// rather than a picker, because "let it run while I fall asleep" is not an
    /// ask anybody answers to the minute.
    func cycle() {
        let choices = LISTENING_TIMER_CHOICES_MIN.map { Int(truncating: $0) }
        guard !choices.isEmpty else { return }
        minutes = choices[((choices.firstIndex(of: minutes) ?? 0) + 1) % choices.count]
        deadline = minutes > 0 ? Date().addingTimeInterval(TimeInterval(minutes * 60)) : nil
        remainingMs = deadline.map { Self.millis(until: $0) }
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

    /// One second is as fine as the chip reads, and the fade is a ramp over two
    /// minutes — nothing here needs a display link. No bedtime, no ticker.
    private func startTicking() {
        ticker?.cancel()
        guard let deadline else {
            ticker = nil
            remainingMs = nil
            return
        }
        ticker = Task { @MainActor [weak self] in
            while !Task.isCancelled, let self {
                remainingMs = Self.millis(until: deadline)
                if expired {
                    stop()
                    onExpire?()
                    return
                }
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    private static func millis(until deadline: Date) -> Int64 {
        Int64(max(0, deadline.timeIntervalSinceNow * 1000))
    }
}
