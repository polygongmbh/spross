import SprossKern
import UIKit

/// Central timing + accessibility guard for every "a clean correct answer
/// flips on its own" surface — vocab review, the trainer drills, the letter
/// drill. The two beats (docs/design.md § Review UX rules) and their numbers are
/// kern's `AdvanceBeat`, so the turn machine and the drills cannot drift apart;
/// the screen-reader skip lives here once so no surface can forget the guard.
enum AutoAdvance {
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

    /// Arm the beat a turn asked for, on the beat's own number — a surface
    /// driven by kern never re-picks which of the two it is. `holding` is what
    /// the beat also waits out before it fires (a drill's answer being said).
    @MainActor
    static func schedule(_ beat: AdvanceBeat, _ task: inout Task<Void, Never>?,
                         holding: (@MainActor () async -> Void)? = nil,
                         action: @escaping @MainActor () -> Void) {
        task?.cancel()
        guard !screenReaderOn else { task = nil; return }
        task = Task {
            try? await Task.sleep(for: .milliseconds(beat.delayMs))
            await holding?()
            guard !Task.isCancelled else { return }
            action()
        }
    }
}
