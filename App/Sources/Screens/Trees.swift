import SwiftUI
import SprossKern

/// The bottom of Home: the 14-day strip,
/// then the box as one picture —
/// one tree per area, in catalog order with the learner's own words last,
/// standing in rows on shared ground —
/// and the standing split in words under it.
///
/// It is a picture of the box, not a way around it:
/// tapping a tree opens the Box screen at that area,
/// which is still where browsing, packing and reviving live.
/// What this picture adds is the thing a count cannot —
/// how the whole box is shaped,
/// and which corners of the language have never been opened.
///
/// Drawn as ONE Canvas, never a view per tree.
/// It never animates: a box grows over weeks,
/// and motion would claim a change the picture is not showing.
/// The canvas is hidden from accessibility;
/// each tree carries a real button on the very cell kern gave it,
/// so what a sighted learner taps and what VoiceOver reads are one element.
struct Trees: View {
    let model: AppModel
    let open: (String) -> Void

    /// Measured once and reused: the picture has to know its width before it can say how tall it is.
    @State private var width: CGFloat = 0
    @State private var placement = Placement()

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            widthProbe
            // Both pieces name themselves —
            // the strip in its own header, the tree picture in the caption under it —
            // so a section title above says the word a third time.
            ActivityStripView(days: model.activity.map(ActivityColumn.init),
                              streakDays: model.stats?.streakDays ?? 0,
                              health: model.stats?.streakHealth ?? .noRun)
            picture
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

    private var picture: some View {
        let placed = placement.marks(model.trees, garden: model.garden, width: width)
        return ZStack(alignment: .topLeading) {
            BleedingCanvas(bleed: 24) { context, _ in
                for mark in placed.marks { TreeShapes.draw(&context, mark) }
                // why: labels go over every tree — crowns may tangle, but a label is never covered.
                for mark in placed.marks { label(&context, mark) }
            }
            .accessibilityHidden(true)
            ForEach(placed.marks, id: \.area) { mark in
                tapTarget(mark)
            }
        }
        .frame(width: width, height: placed.height, alignment: .topLeading)
    }

    /// The area's emoji, on the strip of ground under its own tree —
    /// the only text small enough to sit under a 58pt cell.
    private func label(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let text = Text(verbatim: model.areaEmoji(mark.area))
            .font(.system(size: 13)) // card-parity: a mark under a 58pt cell, below every type role
        let at = CGPoint(x: mark.foot.x, y: mark.baseline + Self.labelHeight / 2)
        guard mark.canopy.isBare else { return context.draw(text, at: at, anchor: .center) }
        context.drawLayer { faded in
            faded.opacity = 0.4
            faded.draw(text, at: at, anchor: .center)
        }
    }

    private static let labelHeight = CGFloat(TreesLayout.shared.LABEL_HEIGHT)

    /// What a learner taps, and what VoiceOver reads: one invisible element
    /// on the cell kern gave the tree, carrying everything the picture itself cannot say.
    private func tapTarget(_ mark: TreeMark) -> some View {
        Color.clear
            .frame(width: mark.cell.width, height: mark.cell.height)
            .contentShape(Rectangle())
            .offset(x: mark.cell.minX, y: mark.cell.minY)
            .onTapGesture { open(mark.area) }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(describe(mark.area))
            .accessibilityAddTraits(.isButton)
    }

    /// The standing split in words, under the picture:
    /// it says how the box is shaped, never how many words are in it.
    private var caption: some View {
        Text.joined(
            Text("progress.allSettledCount \(model.stats?.allSettledCards ?? 0)"),
            Text("progress.allGrowingCount \(model.stats?.allGrowingCards ?? 0)")
        )
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
    }

    /// What VoiceOver reads: the area and the same split the caption spells out.
    private func describe(_ area: String) -> Text {
        let stats = model.areaStats(area)
        return Text.joined(
            Text(model.areaTitle(area)),
            Text("progress.allSettledCount \(Int(stats?.allSettled ?? 0))"),
            Text("progress.allGrowingCount \(Int(stats?.allGrowing ?? 0))")
        )
    }
}

/// The trees placed for one (trees, garden, width), kept until any changes:
/// the body re-evaluates far more often than the box or the width moves.
@MainActor
private final class Placement {
    private var trees: [AreaGrowth] = []
    private var garden = ""
    private var width: CGFloat = -1
    private var placed: (marks: [TreeMark], height: CGFloat) = ([], 0)

    func marks(_ trees: [AreaGrowth], garden: String, width: CGFloat) -> (marks: [TreeMark], height: CGFloat) {
        let same = width == self.width && garden == self.garden && trees.count == self.trees.count
            && zip(trees, self.trees).allSatisfy { $0 === $1 }
        if !same {
            self.trees = trees
            self.garden = garden
            self.width = width
            placed = TreeMark.placed(trees, garden: garden, width: width)
        }
        return placed
    }
}
