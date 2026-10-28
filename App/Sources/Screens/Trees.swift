import SwiftUI
import SprossKern

/// The bottom of Home: the 14-day strip,
/// the box as one picture of one tree per area in rows,
/// and the settled and growing split under it.
/// Tapping a tree opens the Box screen at that area.
///
/// Drawn as one Canvas, and never animated, since a box grows over weeks.
/// The canvas is hidden from accessibility;
/// each tree's button stands on the cell kern gave it.
struct Trees: View {
    let model: AppModel
    let open: (String) -> Void

    /// The picture's height follows its width.
    @State private var width: CGFloat = 0
    @State private var placement = Placement()

    var body: some View {
        // No section title: the strip and the caption name both pieces.
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            strip
            picture
            caption
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    private var strip: some View {
        ActivityStripView(days: model.activity,
                          streakDays: model.stats?.streakDays ?? 0,
                          health: model.stats?.streakHealth ?? .noRun)
    }

    private var picture: some View {
        let placed = placement.marks(model.trees, garden: model.garden, width: width)
        return ZStack(alignment: .topLeading) {
            BleedingCanvas(bleed: 24) { context, _ in
                for mark in placed.marks { mark.art.draw(&context) }
                // why: labels draw after every tree, so no crown covers one.
                for mark in placed.marks { label(&context, mark) }
            }
            .accessibilityHidden(true)
            ForEach(placed.marks, id: \.area) { mark in
                tapTarget(mark)
            }
        }
        .frame(width: width, height: placed.height, alignment: .topLeading)
    }

    /// The area's emoji on the ground under its tree.
    private func label(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let text = Text(verbatim: model.areaEmoji(mark.area))
            .font(.system(size: 13)) // card-parity: a mark under a 58pt cell, below every type role
        let at = CGPoint(x: mark.foot.x, y: mark.baseline + Self.labelHeight / 2)
        guard mark.tree.isBare else { return context.draw(text, at: at, anchor: .center) }
        context.drawLayer { faded in
            faded.opacity = TreesLayout.shared.UNOPENED_LABEL_OPACITY
            faded.draw(text, at: at, anchor: .center)
        }
    }

    private static let labelHeight = CGFloat(TreesLayout.shared.LABEL_HEIGHT)

    /// The tree's button: an invisible element on its cell, named by its area.
    private func tapTarget(_ mark: TreeMark) -> some View {
        Color.clear
            .frame(width: mark.cell.width, height: mark.cell.height)
            .contentShape(Rectangle())
            .offset(x: mark.cell.minX, y: mark.cell.minY)
            .onTapGesture { open(mark.area) }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(model.areaTitle(mark.area)))
            .accessibilityAddTraits(.isButton)
    }

    /// The settled and growing counts under the picture.
    private var caption: some View {
        Text.joined(
            Text("progress.allSettledCount \(model.stats?.allSettledCards ?? 0)"),
            Text("progress.allGrowingCount \(model.stats?.allGrowingCards ?? 0)")
        )
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
        .accessibilityHidden(true)
    }
}

/// The trees placed for one (trees, garden, width), cached because the body re-evaluates far more often than these change.
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
