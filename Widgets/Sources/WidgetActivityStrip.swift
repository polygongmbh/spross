import SwiftUI

/// Header-sized activity strip: one bar per trailing day, oldest first and today last,
/// each sized by kern and carried in the snapshot (`WidgetSnapshot.activityBars`).
/// No weekday letters and no streak underline — both are illegible beside a caption-height flame,
/// and the run the flame counts is the header's own business anyway.
struct WidgetActivityStrip: View {
    let bars: [WidgetSnapshot.Bar]
    /// The row's reserved height, the tallest a bar grows.
    let height: Double

    private static let barWidth: CGFloat = 3
    private static let spacing: CGFloat = 1.5

    var body: some View {
        HStack(alignment: .bottom, spacing: Self.spacing) {
            ForEach(Array(bars.enumerated()), id: \.offset) { index, bar in
                barShape(bar, isToday: index == bars.count - 1)
            }
        }
        .frame(height: height, alignment: .bottom)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }

    private func barShape(_ bar: WidgetSnapshot.Bar, isToday: Bool) -> some View {
        // Same two hues the app's strip keys to: today takes the accent, every
        // other worked day the forest green (`WordWidgetView`'s palette copy).
        let hue = isToday ? WidgetColors.accent : WidgetColors.success
        return Capsule()
            .fill(bar.worked ? hue.opacity(bar.fillOpacity) : Color.secondary.opacity(0.3))
            .frame(width: Self.barWidth, height: bar.height)
    }

    private var label: Text {
        Text("widget.activity \(bars.filter(\.worked).count) \(bars.count)", tableName: GlanceChrome.table)
    }
}
