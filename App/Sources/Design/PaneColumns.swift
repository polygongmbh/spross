import SprossKern
import SwiftUI

/// Which pane a view stands in where the window splits (`PaneColumns`).
enum PaneSide {
    case leading, trailing
}

private struct PaneSideKey: LayoutValueKey {
    static let defaultValue = PaneSide.trailing
}

extension View {
    /// The pane this view stands in where `PaneColumns` splits; one column ignores it.
    func pane(_ side: PaneSide) -> some View {
        layoutValue(key: PaneSideKey.self, value: side)
    }
}

/// One list of views, stacked in a single column or dealt into kern's two panes by their `pane(_:)`,
/// in the order given. The views stay the same views whichever it is,
/// so their state and presentations survive a fold or a rotation.
struct PaneColumns: Layout {
    let panes: WindowPanes?
    /// The margin around each column's content.
    let margin: CGFloat
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let height = columns(width: width, subviews: subviews)
            .map { stack($0.views, width: $0.width).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for column in columns(width: bounds.width, subviews: subviews) {
            var y = bounds.minY
            for (view, height) in zip(column.views, stack(column.views, width: column.width).heights) {
                view.place(at: CGPoint(x: bounds.minX + column.x, y: y), anchor: .topLeading,
                           proposal: ProposedViewSize(width: column.width, height: height))
                // why: an empty section (a card not offered today) takes no spacing.
                if height > 0 { y += height + spacing }
            }
        }
    }

    private struct Column {
        let x: CGFloat
        let width: CGFloat
        let views: [LayoutSubview]
    }

    /// Each column's content frame: a pane's readable column, centered in the pane and inset by the margin,
    /// the trailing pane ending at the edge — or the whole width, inset, where the window stays one column.
    private func columns(width: CGFloat, subviews: Subviews) -> [Column] {
        guard let panes, panes.split else {
            return [Column(x: margin, width: max(0, width - 2 * margin), views: Array(subviews))]
        }
        let trailingStart = width - panes.trailing.width
        let leadingStart = trailingStart - panes.gutter - panes.leading.width
        return [(PaneSide.leading, panes.leading, leadingStart), (.trailing, panes.trailing, trailingStart)]
            .map { side, pane, start in
                let column = min(pane.columnWidth, pane.width)
                return Column(x: start + (pane.width - column) / 2 + margin,
                              width: max(0, column - 2 * margin),
                              views: subviews.filter { $0[PaneSideKey.self] == side })
            }
    }

    private func stack(_ views: [LayoutSubview], width: CGFloat) -> (heights: [CGFloat], height: CGFloat) {
        let heights = views.map { $0.sizeThatFits(ProposedViewSize(width: width, height: nil)).height }
        let shown = heights.filter { $0 > 0 }
        return (heights, shown.reduce(0, +) + spacing * CGFloat(max(0, shown.count - 1)))
    }
}
