import SwiftUI

/// One column of the widget's strip: a day and what was reviewed on it.
struct ActivityDay: Hashable {
    /// Local midnight of the day.
    let day: Date
    let reviews: Int
    let isToday: Bool
}

/// Header-sized activity strip: one bar per trailing day, no weekday letters and
/// no streak underline — both are illegible beside a caption-height flame. The
/// run the flame counts is the header's own business anyway.
struct WidgetActivityStrip: View {
    let days: [ActivityDay]

    private static let barWidth: CGFloat = 3
    private static let spacing: CGFloat = 1.5
    private static let maxHeight: CGFloat = 16

    var body: some View {
        // why: √ of the share, mirroring App/Sources/Design/ActivityStripView.swift,
        // so one huge day cannot flatten the rest and both surfaces agree on what a
        // bar height means.
        let maxReviews = max(days.map(\.reviews).max() ?? 1, 1)
        HStack(alignment: .bottom, spacing: Self.spacing) {
            ForEach(days, id: \.day) { entry in
                bar(entry, maxReviews: maxReviews)
            }
        }
        .frame(height: Self.maxHeight, alignment: .bottom)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }

    private func bar(_ entry: ActivityDay, maxReviews: Int) -> some View {
        let scaled = entry.reviews > 0
            ? (Double(entry.reviews) / Double(maxReviews)).squareRoot()
            : 0
        let height = entry.reviews > 0 ? max(3, Self.maxHeight * scaled) : 1.5
        // Same two hues the app's strip keys to: today takes the accent, every
        // other worked day the forest green (`WordWidgetView`'s palette copy).
        let hue = entry.isToday ? Color.wgAccent : Color.wgSuccess
        return Capsule()
            .fill(entry.reviews > 0 ? hue.opacity(0.45 + 0.55 * scaled) : Color.secondary.opacity(0.3))
            .frame(width: Self.barWidth, height: height)
    }

    private var label: Text {
        Text("\(days.filter { $0.reviews > 0 }.count) aktive Tage der letzten \(days.count)")
    }
}
