import SwiftUI

/// Condensed progress section at the bottom of Heute: 14-day activity strip
/// plus the consolidated/fresh split of the active cards (the two sum to the active
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
            ActivityStripView(days: model.activityWindow().map(ActivityColumn.init),
                              streakDays: model.stats?.streakDays ?? 0)
            HStack(spacing: DL.Space.m) {
                // Standing totals with today's movement under them: the totals say
                // where the box stands, the deltas say that today moved it.
                BoxStatTile(emoji: "🌳",
                            value: "\(model.stats?.consolidatedCards ?? 0)",
                            label: "progress.consolidated",
                            delta: delta(Int(model.today?.consolidated ?? 0)))
                BoxStatTile(emoji: "🌱",
                            value: "\(model.stats?.learningCards ?? 0)",
                            label: "progress.fresh",
                            // Today's arrivals that are still fresh — an older word
                            // consolidating now belongs to the tile beside this one,
                            // and must not eat a delta it never contributed to.
                            delta: delta(Int(model.today?.stillFresh ?? 0)))
            }
        }
    }

    /// "+3 heute", or nothing at all on a figure the day has not moved.
    private func delta(_ count: Int) -> LocalizedStringKey? {
        count > 0 ? "progress.today \(count.formatted())" : nil
    }
}
