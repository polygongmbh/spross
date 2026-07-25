import SwiftUI

/// Condensed progress section at the bottom of Heute: 14-day activity strip
/// plus active-card count and retention. Streak lives on the session/done
/// cards; due count on the session ring; suspended cards surface in the Box.
/// Everything renders from `BoxStatistics` and `dailyStats` — never from logs.
struct FortschrittSection: View {
    let model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("progress.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            ActivityStripView(days: model.last14Days())
            HStack(spacing: DL.Space.m) {
                BoxStatTile(emoji: "📦",
                            value: "\(model.stats?.activeCards ?? 0)",
                            label: "progress.activeCards")
                BoxStatTile(emoji: "🎯",
                            value: retentionText,
                            label: "progress.retention")
            }
        }
    }

    private var retentionText: String {
        guard let retrievability = model.stats?.retrievability else { return "–" }
        return "\(Int((retrievability * 100).rounded())) %"
    }
}
