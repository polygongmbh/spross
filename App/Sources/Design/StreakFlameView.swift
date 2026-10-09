import SprossKern
import SwiftUI

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
                .grayscale(1 - health.flameSaturation)
                .opacity(health.flameOpacity)
        }
    }
}
