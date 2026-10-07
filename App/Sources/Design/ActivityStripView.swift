import SprossKern
import SwiftUI

/// 14-day activity strip: one bar per day, plus the streak run it belongs to.
/// Every bar's height, intensity and run is kern's (`ActivityBars`); this view only draws them.
/// Today is clay, the streak run underlines the days that earned the flame in the header.
struct ActivityStripView: View {
    /// Kern's activity window (`streakWindow`), oldest first, today last.
    let days: [ActivityDay]
    /// Authoritative streak from `BoxStatistics` — the strip never recomputes
    /// the number, only draws which days it covers.
    var streakDays: Int = 0
    /// What today still owes the run, handed over beside the number — the badge's
    /// flame wears the same grade as the one on the session card above it.
    var health: StreakHealth = .earned

    @Environment(\.locale) private var locale

    private static let barSpacing = Theme.spacing.xs + 2

    var body: some View {
        let bars = ActivityBars.shared.of(days: days, scale: ActivityScale.companion.strip)

        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            HStack(spacing: Theme.spacing.sm) {
                Text("progress.last14Days")
                    .font(Theme.typography.title)
                    .foregroundStyle(Theme.colors.textPrimary)
                Spacer(minLength: Theme.spacing.sm)
                if streakDays > 0 {
                    streakBadge
                }
            }
            HStack(alignment: .bottom, spacing: Self.barSpacing) {
                ForEach(Array(bars.enumerated()), id: \.element.day) { index, bar in
                    dayColumn(bar,
                              joinsLeft: index > 0 && bars[index - 1].run == bar.run,
                              joinsRight: index < bars.count - 1 && bars[index + 1].run == bar.run)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .panelSurface()
        .cardShadow()
        .accessibilityElement(children: .combine)
        .accessibilityLabel(activityLabel(bars))
    }

    private var streakBadge: some View {
        // The count keeps the accent whatever the flame is doing: the cooled or cold
        // MARK is the warning, and fading the number only costs contrast.
        // Two views rather than one concatenated Text: grayscale is a View modifier,
        // and the emoji is the only half that wears it.
        HStack(spacing: Theme.spacing.xs) {
            Text(verbatim: "🔥")
                .grayscale(1 - health.flameSaturation)
                .opacity(health.flameOpacity)
            (Text(streakDays.formatted()) + Text(verbatim: " ")
                + Text(streakDays == 1 ? "common.day.one" : "common.day.other"))
                .foregroundStyle(Theme.colors.accent)
        }
        .font(Theme.typography.caption)
        .lineLimit(1)
        .accessibilityHidden(true) // why: the combined strip label names the streak
    }

    private func activityLabel(_ bars: [ActivityBar]) -> Text {
        let activity = Text("a11y.count.activity14Days \(Int(ActivityBars.shared.activeDays(bars: bars)))")
        guard streakDays > 0 else { return activity }
        return activity + Text(verbatim: ". ") + Text("a11y.count.streakDays \(streakDays)")
    }

    // why: opaque fills only — joined segments overlap, and translucent ones
    // would compound into a dark seam at every join.
    private func runColor(_ run: StripRun) -> Color {
        switch run {
        case .current: return Theme.colors.accent
        case .past: return Theme.colors.success
        default: return .clear
        }
    }

    // MARK: - Columns

    private func dayColumn(_ bar: ActivityBar, joinsLeft: Bool, joinsRight: Bool) -> some View {
        VStack(spacing: Theme.spacing.xs) {
            barShape(bar)
            runSegment(bar.run, joinsLeft: joinsLeft, joinsRight: joinsRight)
            Text(weekdayLetter(Date(epochMillis: bar.dayStartEpochMillis)))
                .font(.system(size: 9, weight: .medium, design: .rounded)) // card-parity: a weekday letter under a 2pt bar, below every type role
                .foregroundStyle(bar.isToday ? Theme.colors.accent : Theme.colors.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func barShape(_ bar: ActivityBar) -> some View {
        let hue = bar.isToday ? Theme.colors.accent : Theme.colors.success
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous) // card-parity: the bar's own corner, not a card radius
        return Group {
            if bar.worked {
                shape.fill(hue.opacity(bar.fillOpacity))
            } else if bar.isEmptyToday {
                // why: an empty today reads as "nothing yet", not as a gap — an
                // outline keeps the column present without claiming a review.
                shape.strokeBorder(Theme.colors.accent.opacity(Palette.shared.TODAY_OUTLINE), lineWidth: 1.5)
            } else {
                shape.fill(Theme.colors.separator)
            }
        }
        .frame(height: bar.height)
        .frame(maxHeight: ActivityScale.companion.strip.maxHeight, alignment: .bottom)
    }

    /// The run underline. Segments overhang into the gutter on the sides where
    /// the run continues, so a stretch reads as one rule rather than a row of ticks.
    private func runSegment(_ run: StripRun, joinsLeft: Bool, joinsRight: Bool) -> some View {
        Capsule()
            .fill(runColor(run))
            .frame(height: 2.5)
            // why: a full-gutter overhang makes neighbors overlap, so the rounded
            // caps hide inside the run instead of pinching it into dashes.
            .padding(.leading, joinsLeft ? -Self.barSpacing : 0)
            .padding(.trailing, joinsRight ? -Self.barSpacing : 0)
    }

    private func weekdayLetter(_ date: Date) -> String {
        let formatter = Date.FormatStyle(locale: locale)
            .weekday(.narrow)
        return date.formatted(formatter)
    }
}

#Preview {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: .now)
    let counts = [4, 0, 9, 3, 26, 6, 0, 5, 7, 0, 11, 8, 14, 5]
    // Every gap here sits between two earned days, so the run bridges all of them.
    let days: [ActivityDay] = (0..<14).map { offset in
        let day = calendar.date(byAdding: .day, value: offset - 13, to: today)!
        return ActivityDay(day: "\(offset)",
                           dayStartEpochMillis: Int64(day.timeIntervalSince1970 * 1000),
                           reviews: Int32(counts[offset]),
                           role: counts[offset] > 0 ? .earned : .bridged)
    }
    return VStack(spacing: Theme.spacing.lg) {
        ActivityStripView(days: days, streakDays: 11)
        ActivityStripView(days: days, streakDays: 11, health: .ending)
        ActivityStripView(days: days.map {
            ActivityDay(day: $0.day, dayStartEpochMillis: $0.dayStartEpochMillis, reviews: 0, role: .outside)
        })
    }
    .padding()
    .background(Theme.colors.background)
}
