import SwiftUI
import SprossKern

/// The bottom of Home: the 14-day strip,
/// then a picture of the orchard —
/// one tree per area, in catalog order with the learner's own words last.
///
/// It is a picture of the orchard, not a way around it:
/// tapping a tree opens the Orchard screen at that area,
/// which is still where browsing, packing and reviving live.
/// What this picture adds is the thing a count cannot —
/// how the whole orchard is shaped,
/// and which corners of the language have never been opened.
struct ForestSection: View {
    let model: AppModel
    let open: (String) -> Void

    /// Measured once and reused:
    /// this picture has to know its width before it can say how tall it is
    /// (see `ForestCanvas`).
    @State private var width: CGFloat = 0

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            widthProbe
            // Both pieces name themselves —
            // the strip in its own header, the tree picture in the caption under it —
            // so a section title above says the word a third time.
            ActivityStripView(days: model.activity.map(ActivityColumn.init),
                              streakDays: model.stats?.streakDays ?? 0,
                              flame: model.stats?.flame ?? .unlit)
            ForestCanvas(trees: model.trees, open: open, describe: describe)
                .environment(\.contentWidth, width)
            caption
        }
    }

    private var widthProbe: some View {
        Color.clear
            .frame(height: 0)
            .background {
                GeometryReader { proxy in
                    Color.clear
                        .onAppear { width = proxy.size.width }
                        .onChange(of: proxy.size.width) { _, new in width = new }
                }
            }
    }

    /// The standing split in words, under the picture:
    /// it says how the orchard is shaped,
    /// never how many words are in it.
    private var caption: some View {
        Text.joined(
            Text("progress.consolidatedCount \((model.stats?.consolidatedCards ?? 0).formatted())"),
            Text("progress.learningCount \((model.stats?.learningCards ?? 0).formatted())")
        )
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
    }

    // MARK: - Areas as trees

    /// What VoiceOver reads: the picture says nothing aloud, so the label
    /// carries the area and the same split the caption spells out.
    private func describe(_ tree: AreaTree) -> Text {
        let stats = model.areaStats(tree.id)
        return Text.joined(
            Text(tree.title),
            Text("progress.consolidatedCount \((stats?.consolidated ?? 0).formatted())"),
            Text("progress.learningCount \((stats?.learning ?? 0).formatted())")
        )
    }
}
