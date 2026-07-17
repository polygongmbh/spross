import SwiftUI
import DuoKern

/// Progress tab: streak, stat tiles, 14-day activity strip.
/// Everything renders from `BoxStatistics` and `dailyStats` — never from logs.
struct FortschrittView: View {
    let model: AppModel

    private let columns = [
        GridItem(.flexible(), spacing: DL.Space.m),
        GridItem(.flexible(), spacing: DL.Space.m),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.xl) {
                Text("Fortschritt")
                    .font(DL.Fonts.hero)
                    .foregroundStyle(Color.dlTextPrimary)
                StreakFlameView(days: model.stats?.streak ?? 0)
                statGrid
                activityStrip
            }
            .padding(DL.Space.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    // MARK: - Stat tiles

    private var statGrid: some View {
        LazyVGrid(columns: columns, spacing: DL.Space.m) {
            BoxStatTile(emoji: "📦",
                        value: "\(model.stats?.activeCount ?? 0)",
                        label: "Aktive Karten")
            BoxStatTile(emoji: "⏰",
                        value: "\(model.stats?.dueCount ?? 0)",
                        label: "Heute fällig")
            BoxStatTile(emoji: "🎯",
                        value: retentionText,
                        label: "Behalten")
            BoxStatTile(emoji: "💤",
                        value: "\(model.stats?.suspendedCount ?? 0)",
                        label: "Pausierte Karten")
        }
    }

    private var retentionText: String {
        guard let retrievability = model.stats?.averageRetrievability else { return "–" }
        return "\(Int((retrievability * 100).rounded())) %"
    }

    // MARK: - 14-day activity strip

    private var activityStrip: some View {
        let days = model.last14Days()
        let maxReviews = max(days.map(\.reviews).max() ?? 1, 1)

        return VStack(alignment: .leading, spacing: DL.Space.m) {
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
