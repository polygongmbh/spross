import SprossKern
import UIKit

/// Central timing + accessibility guard for every "a clean correct answer
/// flips on its own" surface — vocab review, the trainer drills, the letter
/// drill. Two tiers (docs/design.md § Review UX rules, "0.45-1.2 s"):
/// `scheduleLive` for a word confirmed by finishing typing it (no Check/Enter
/// needed), `scheduleExplicit` for one confirmed through an explicit
/// Check/tile tap. Both numbers are kern's `AdvanceTier`, so the turn machine
/// and the drills that never reach it cannot drift apart; the screen-reader
/// skip lives here once so no surface can forget the guard.
enum AutoAdvance {
    /// "Finishing the word IS the answer": armed the instant a live-typed
    /// answer goes exact. Short enough that the flip still reads as
    /// finishing the word, not a separate beat after it.
    static let liveDelay: Duration = .milliseconds(SprossKern.ADVANCE_LIVE_MS)
    /// The beat after an explicit Check/tile tap — long enough that the
    /// correctness chime and the tap's own confirmation both land before
    /// the screen moves.
    static let explicitDelay: Duration = .milliseconds(SprossKern.ADVANCE_EXPLICIT_MS)

    /// VoiceOver and Switch Control both make a timed screen change
    /// hostile: it truncates the correctness announcement and moves the
    /// page under the user. Every auto-advance surface reads this one flag
    /// instead of deciding for itself — where it's true, callers must
    /// render an explicit "Weiter" in the branch that would otherwise be
    /// EmptyView() while the timer ran.
    @MainActor
    static var screenReaderOn: Bool {
        UIAccessibility.isVoiceOverRunning || UIAccessibility.isSwitchControlRunning
    }

    @MainActor
    static func scheduleLive(_ task: inout Task<Void, Never>?, action: @escaping @MainActor () -> Void) {
        schedule(&task, delay: liveDelay, action: action)
    }

    @MainActor
    static func scheduleExplicit(_ task: inout Task<Void, Never>?, action: @escaping @MainActor () -> Void) {
        schedule(&task, delay: explicitDelay, action: action)
    }

    /// Arm the beat a turn asked for, on the tier's own number — a surface
    /// driven by kern never re-picks which of the two it is.
    @MainActor
    static func schedule(_ tier: AdvanceTier, _ task: inout Task<Void, Never>?,
                         action: @escaping @MainActor () -> Void) {
        schedule(&task, delay: .milliseconds(tier.delayMs), action: action)
    }

    @MainActor
    private static func schedule(_ task: inout Task<Void, Never>?, delay: Duration, action: @escaping @MainActor () -> Void) {
        task?.cancel()
        guard !screenReaderOn else { task = nil; return }
        task = Task {
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            action()
        }
    }
}
