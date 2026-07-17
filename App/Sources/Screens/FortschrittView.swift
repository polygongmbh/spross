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
                ActivityStripView(days: model.last14Days())
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
}
