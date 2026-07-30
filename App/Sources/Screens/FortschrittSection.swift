import SwiftUI

/// Condensed progress section at the bottom of Heute: 14-day activity strip
/// plus the settled/fresh split of the active cards (the two sum to the active
/// count the Box header spells out). The strip labels the streak it draws;
/// due count on the session ring; suspended cards surface in the Box.
/// Everything renders from `BoxStatistics` and `dailyStats` — never from logs.
struct FortschrittSection: View {
    let model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("progress.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            ActivityStripView(days: model.last14Days(),
                              streakDays: model.stats?.streakDays ?? 0)
            HStack(spacing: DL.Space.m) {
                BoxStatTile(emoji: "🌳",
                            value: "\(model.stats?.settledCards ?? 0)",
                            label: "progress.consolidated")
                BoxStatTile(emoji: "🌱",
                            value: "\(model.stats?.freshCards ?? 0)",
                            label: "progress.fresh")
            }
        }
    }
}
