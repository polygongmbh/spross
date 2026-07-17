import SwiftUI

/// 14-day activity strip: one bar per day, scaled to the busiest day.
/// Renders from pre-aggregated daily review counts — never from logs.
struct ActivityStripView: View {
    /// Trailing days, oldest first (see `AppModel.last14Days()`).
    let days: [(day: Date, reviews: Int)]

    var body: some View {
        let maxReviews = max(days.map(\.reviews).max() ?? 1, 1)

        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("Letzte 14 Tage")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            HStack(alignment: .bottom, spacing: DL.Space.xs + 2) {
                ForEach(days, id: \.day) { entry in
                    dayBar(entry, maxReviews: maxReviews)
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

    private func dayBar(_ entry: (day: Date, reviews: Int), maxReviews: Int) -> some View {
        let fraction = Double(entry.reviews) / Double(maxReviews)
        return VStack(spacing: DL.Space.xs) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(entry.reviews > 0 ? Color.dlSuccess : Color.dlSeparator)
                .frame(height: entry.reviews > 0 ? max(10, 52 * fraction) : 6)
                .frame(maxHeight: 52, alignment: .bottom)
            Text(weekdayLetter(entry.day))
                .font(.system(size: 9, weight: .medium, design: .rounded))
                .foregroundStyle(Color.dlTextSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func weekdayLetter(_ date: Date) -> String {
        let formatter = Date.FormatStyle(locale: Locale(identifier: "de_DE"))
            .weekday(.narrow)
        return date.formatted(formatter)
    }

    private func activityLabel(_ days: [(day: Date, reviews: Int)]) -> String {
        let activeDays = days.filter { $0.reviews > 0 }.count
        return "Aktivität der letzten 14 Tage: an \(activeDays) Tagen gelernt"
    }
}

#Preview {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: .now)
    let days: [(day: Date, reviews: Int)] = (0..<14).map { offset in
        let day = calendar.date(byAdding: .day, value: offset - 13, to: today)!
        return (day: day, reviews: [0, 3, 8, 0, 12, 5, 2][offset % 7])
    }
    return ActivityStripView(days: days)
        .padding()
        .background(Color.dlBackground)
}
