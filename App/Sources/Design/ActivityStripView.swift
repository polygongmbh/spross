import SwiftUI

/// One column of the strip: a day, what was reviewed on it, and whether the
/// current streak covers it. The box walks the streak once and hands the answer
/// over (`ActivityDay`); the strip only draws it.
struct ActivityColumn: Equatable {
    /// Local midnight of the day — the weekday letter is rendered from it.
    let day: Date
    let reviews: Int
    /// Covered by the run the header's flame counts, earned day or bridged gap.
    let inStreak: Bool
}

/// 14-day activity strip: one bar per day, plus the streak run it belongs to.
/// Bars carry volume twice — height (√-scaled, so one huge day can't flatten
/// the rest) and fill intensity. Today is clay, the streak run underlines the
/// days that earned the flame in the header. Renders from pre-aggregated daily
/// review counts — never from logs.
struct ActivityStripView: View {
    /// Trailing days, oldest first, today last.
    let days: [ActivityColumn]
    /// Authoritative streak from `BoxStatistics` — the strip never recomputes
    /// the number, only draws which days it covers.
    var streakDays: Int = 0

    @Environment(\.locale) private var locale

    private static let barSpacing = DL.Space.xs + 2
    private static let maxBarHeight: CGFloat = 52

    var body: some View {
        let maxReviews = max(days.map(\.reviews).max() ?? 1, 1)

        VStack(alignment: .leading, spacing: DL.Space.m) {
            HStack(spacing: DL.Space.s) {
                Text("progress.last14Days")
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                Spacer(minLength: DL.Space.s)
                if streakDays > 0 {
                    streakBadge
                }
            }
            HStack(alignment: .bottom, spacing: Self.barSpacing) {
                ForEach(Array(days.enumerated()), id: \.element.day) { index, entry in
                    let run = runStyle(at: index)
                    dayColumn(entry,
                              maxReviews: maxReviews,
                              isToday: index == days.count - 1,
                              run: run,
                              joinsLeft: index > 0 && runStyle(at: index - 1) == run,
                              joinsRight: index < days.count - 1
                                  && runStyle(at: index + 1) == run)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
        .accessibilityElement(children: .combine)
        .accessibilityLabel(activityLabel(days))
    }

    private var streakBadge: some View {
        (Text(verbatim: "🔥 ") + Text(streakDays.formatted()) + Text(verbatim: " ")
            + Text(streakDays == 1 ? "common.dayOne" : "common.dayMany"))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlAccent)
            .lineLimit(1)
            .accessibilityHidden(true) // why: the combined strip label names the streak
    }

    private func activityLabel(_ days: [ActivityColumn]) -> Text {
        let activeDays = days.filter { $0.reviews > 0 }.count
        let activity = Text("a11y.activity14Days \(activeDays)")
        guard streakDays > 0 else { return activity }
        return activity + Text(verbatim: ". ") + Text("a11y.streakDays \(streakDays)")
    }

    // MARK: - Runs

    private enum RunStyle: Equatable {
        case none
        /// Part of the streak that earned the flame.
        case current
        /// An older stretch of active days — takes the bars' hue, not the flame's.
        case past

        // why: opaque fills only — joined segments overlap, and translucent ones
        // would compound into a dark seam at every join.
        var color: Color? {
            switch self {
            case .none: return nil
            case .current: return .dlAccent
            case .past: return .dlSuccess
            }
        }
    }

    private func runStyle(at index: Int) -> RunStyle {
        if days[index].inStreak { return .current }
        return days[index].reviews > 0 ? .past : .none
    }

    // MARK: - Columns

    private func dayColumn(_ entry: ActivityColumn,
                           maxReviews: Int,
                           isToday: Bool,
                           run: RunStyle,
                           joinsLeft: Bool,
                           joinsRight: Bool) -> some View {
        VStack(spacing: DL.Space.xs) {
            bar(entry, maxReviews: maxReviews, isToday: isToday)
            runSegment(run, joinsLeft: joinsLeft, joinsRight: joinsRight)
            Text(weekdayLetter(entry.day))
                .font(.system(size: 9, weight: .medium, design: .rounded))
                .foregroundStyle(isToday ? Color.dlAccent : Color.dlTextSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func bar(_ entry: ActivityColumn,
                     maxReviews: Int,
                     isToday: Bool) -> some View {
        // √ of the share, so a 40-review day doesn't squash every 4-review day
        // into the same stub; intensity repeats the volume for the short bars.
        let share = Double(entry.reviews) / Double(maxReviews)
        let scaled = share > 0 ? share.squareRoot() : 0
        let height = entry.reviews > 0 ? max(10, Self.maxBarHeight * scaled) : 6
        let hue = isToday ? Color.dlAccent : Color.dlSuccess
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
        return Group {
            if entry.reviews > 0 {
                shape.fill(hue.opacity(0.45 + 0.55 * scaled))
            } else if isToday {
                // why: an empty today reads as "nothing yet", not as a gap — an
                // outline keeps the column present without claiming a review.
                shape.strokeBorder(Color.dlAccent.opacity(0.5), lineWidth: 1.5)
            } else {
                shape.fill(Color.dlSeparator)
            }
        }
        .frame(height: height)
        .frame(maxHeight: Self.maxBarHeight, alignment: .bottom)
    }

    /// The run underline. Segments overhang into the gutter on the sides where
    /// the run continues, so a stretch reads as one rule rather than a row of ticks.
    private func runSegment(_ run: RunStyle, joinsLeft: Bool, joinsRight: Bool) -> some View {
        Capsule()
            .fill(run.color ?? .clear)
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
    // Every gap here sits between two earned days, so the run bridges all of
    // them and the badge counts the 11 that were earned.
    let days: [ActivityColumn] = (0..<14).map { offset in
        let day = calendar.date(byAdding: .day, value: offset - 13, to: today)!
        return ActivityColumn(day: day, reviews: counts[offset], inStreak: true)
    }
    return VStack(spacing: DL.Space.l) {
        ActivityStripView(days: days, streakDays: 11)
        ActivityStripView(days: days.map {
            ActivityColumn(day: $0.day, reviews: 0, inStreak: false)
        })
    }
    .padding()
    .background(Color.dlBackground)
}
