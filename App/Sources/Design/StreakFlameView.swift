import SprossKern
import SwiftUI

/// How bright and how colorful the mark burns in each `StreakHealth`.
extension StreakHealth {
    /// Full strength where the day is answered, only a whisper of fade where it is
    /// still owed, and faint where there is no run behind the mark at all.
    var opacity: Double {
        switch self {
        case .earned: return 1
        case .bridgeable: return 0.9
        case .ending: return 0.9
        case .noRun: return 0.4
        }
    }

    /// How much color is drained out of the emoji: none while the run is whole,
    /// half of it while today still owes the run — a flame cooling, which asks for
    /// renewal without being faded out — and all of it once a missed today would
    /// end the run, a flame gone cold, which is louder than any amount of fading.
    var grayscale: Double {
        switch self {
        case .earned: return 0
        case .bridgeable: return 0.5
        case .ending, .noRun: return 1
        }
    }
}

struct StreakFlameView: View {
    let days: Int
    /// What today still owes the run, worn by the flame itself — the mark says
    /// the run is exposed on exactly the day it is, without a word for it.
    var health: StreakHealth = .earned
    /// The mark the run wears. The flame is the streak's identity everywhere it is
    /// merely reported; a screen that IS the celebration hands its own emoji in and
    /// carries one badge instead of a badge under a hero saying the same thing twice.
    var emoji: String?

    var body: some View {
        HStack(spacing: Theme.spacing.sm) {
            mark
                .font(.title2)
                .accessibilityHidden(true)
            Text(days.formatted())
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
            // why: the number carries the big type, so the unit stands alone —
            // and a string that does not name its count cannot be plural-varied
            // (the compiler refuses it), which is what these two keys are for.
            Text(days == 1 ? "common.day.one" : "common.day.other")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
        }
        .padding(.horizontal, Theme.spacing.lg)
        .padding(.vertical, Theme.spacing.md)
        .background(Theme.colors.surfaceTint, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("a11y.count.streakDays \(days)"))
    }

    /// The celebrating screen's own emoji where one is handed in, else the flame
    /// in the grade the day has earned it.
    @ViewBuilder
    private var mark: some View {
        if let emoji {
            Text(verbatim: emoji)
        } else {
            Text(verbatim: "🔥")
                .grayscale(health.grayscale)
                .opacity(health.opacity)
        }
    }
}
