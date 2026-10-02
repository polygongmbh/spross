import Foundation
import SprossKern

// MARK: - Orchard layout
//
// One tree per area, laid out in rows across a width.
// Plain values: no SwiftUI, no kern, no drawing —
// so a preview can fabricate a box at any age.
//
// `TreeMark` is a placed tree (position, cell, skeleton) —
// a layout artifact over kern's `AreaTree`.

/// One tree placed: where it stands and how tall,
/// plus the branches its marks hang on.
struct TreeMark {
    let tree: AreaTree
    /// What hangs on it, by mark.
    let canopy: Canopy
    /// Where the trunk meets the ground.
    let foot: CGPoint
    /// Foot to the top of the crown.
    let height: CGFloat
    /// The cell the label and the tap target fill.
    let cell: CGRect
    /// The ground line this tree's whole row shares.
    let baseline: CGFloat

    /// The branches, grown from the area's name and its standing —
    /// so the very same tree stands before and after a round
    /// and only what hangs on it moves.
    ///
    /// Grown with the mark rather than on each read:
    /// a `Canvas` redraws more often than the orchard is laid out —
    /// a scroll, a size change, a theme change —
    /// and growing a tree is the expensive half of drawing one.
    let skeleton: TreeSkeleton

    init(tree: AreaTree, foot: CGPoint, height: CGFloat, cell: CGRect, baseline: CGFloat) {
        self.tree = tree
        self.canopy = Canopy(tree)
        self.foot = foot
        self.height = height
        self.cell = cell
        self.baseline = baseline
        self.skeleton = Self.grown(tree: tree, canopy: canopy, foot: foot, height: height)
    }

    /// Grown flush into its box: the marks that hang off the ends of the wood
    /// reach past it, and the canvas bleeds past the box to draw them (`BleedingCanvas`).
    private static func grown(tree: AreaTree, canopy: Canopy, foot: CGPoint,
                               height: CGFloat) -> TreeSkeleton {
        // why: the count comes from the TREE —
        // the finished one, whatever moment is being drawn —
        // and never from the height it is drawn at.
        // A transition scales the height every frame,
        // and a crown regrown halfway through would move every mark already hanging.
        let seed = SplitMix64(tree.area).seed
        let box = CGRect(x: foot.x - height * 0.72, y: foot.y - height,
                         width: max(height * 1.44, 1), height: max(height, 1))
        return TreeSkeleton.grown(seed: seed, marks: canopy.count, in: box)
    }
}

enum OrchardLayout {

    /// What a crown spans, in tree heights, and the narrowest a tree ever stands:
    /// a sapling's label and tap target need room a stem does not.
    static let crownSpan: CGFloat = 1.4
    static let minTreeWidth: CGFloat = 32
    /// The closest two crowns in a row stand to each other,
    /// and the half column every second row gives up at its start.
    static let minGap: CGFloat = 6
    static let rowShift: CGFloat = 32
    static let rowHeight: CGFloat = 72
    static let labelHeight: CGFloat = 18
    static let rowGap: CGFloat = Theme.spacing.sm

    /// Tree heights, foot to crown.
    /// The floor is a seedling;
    /// the ceiling keeps the tallest area inside its row
    /// instead of towering over the others.
    static let minHeight: CGFloat = 9
    static let maxHeight: CGFloat = 42

    /// The shortest a tap target is ever made, label strip included —
    /// a seedling is a few points of ink and a thumb is not.
    static let minTapHeight: CGFloat = 44
    /// The clear air a tap target keeps above the crown it belongs to.
    static let tapMargin: CGFloat = 6

    /// Lays the trees out in rows across `width`, in the order given.
    ///
    /// Rows, not a grid:
    /// every tree in a row stands on ONE baseline,
    /// which is what lets two areas be compared at a glance.
    /// A tree takes the width of its own crown,
    /// a row takes trees while they fit,
    /// and the spare width is shared out evenly between them.
    /// Rows alternate: one touches the left side and leaves half a column free on the right,
    /// the next the other way round, and rows stand half a row apart —
    /// a checkerboard, so a tree grows up through the gap between two of the row above
    /// and the orchard reads as one growing mass rather than as drawers in a wall.
    static func marks(_ trees: [AreaTree], width: CGFloat) -> [TreeMark] {
        guard width > 0, !trees.isEmpty else { return [] }
        let span = width - rowShift
        let widths = trees.map { max(minTreeWidth, treeHeight($0) * crownSpan) }
        var rows: [[Int]] = [[]]
        var used: CGFloat = 0
        for index in trees.indices {
            let add = widths[index] + (rows[rows.count - 1].isEmpty ? 0 : minGap)
            if !rows[rows.count - 1].isEmpty, used + add > span {
                rows.append([])
                used = 0
            }
            used += widths[index] + (rows[rows.count - 1].isEmpty ? 0 : minGap)
            rows[rows.count - 1].append(index)
        }

        var marks: [TreeMark] = []
        var base: CGFloat = 0
        for (rank, row) in rows.enumerated() {
            let tallest = row.map { treeHeight(trees[$0]) }.max() ?? minHeight
            let band = max(rowHeight, tallest + 10)
            // why: rows stand HALF a row apart —
            // a row's trees grow up through the gaps of the rows around them
            // instead of starting under a shelf of air,
            // and the orchard reads as one growing mass.
            let pitch = (band + labelHeight + rowGap) / 2
            // why: the last row stays as packed as it is.
            let spare = span - row.reduce(0) { $0 + widths[$1] }
            let gap = rank == rows.count - 1 || row.count < 2 ? minGap : spare / CGFloat(row.count - 1)
            var left = rank.isMultiple(of: 2) ? 0 : rowShift
            for index in row {
                let tree = trees[index]
                let x = left + widths[index] / 2
                left += widths[index] + gap
                let stand = base + band
                // why: the cell follows THIS tree's own crown, never the row's band —
                // a band is as tall as the tallest tree in the row, and giving every
                // tree in it that height handed a seedling a tap target reaching up
                // into the open air a whole row above where it is drawn.
                let crown = max(treeHeight(tree), minHeight) + tapMargin
                let reach = max(crown, minTapHeight - labelHeight)
                let cell = CGRect(x: x - widths[index] / 2, y: stand - reach,
                                  width: widths[index], height: reach + labelHeight)
                marks.append(TreeMark(tree: tree,
                                      foot: CGPoint(x: x, y: stand),
                                      height: treeHeight(tree),
                                      cell: cell,
                                      baseline: stand))
            }
            base += pitch
        }
        // why: back to front across the WHOLE orchard,
        // not within a row —
        // once rows interleave, the tree in front of you may well belong
        // to another one,
        // and only a global order layers them correctly.
        return marks.sorted { $0.baseline < $1.baseline }
    }


    /// How tall a laid-out orchard stands.
    // why: the marks are ordered by depth, not down the page —
    // the lowest edge belongs to whichever tree stands furthest forward.
    static func height(of marks: [TreeMark]) -> CGFloat {
        marks.map(\.cell.maxY).max() ?? 0
    }

    static func height(_ trees: [AreaTree], width: CGFloat) -> CGFloat {
        height(of: marks(trees, width: width))
    }

    /// The mass at which a tree reaches full height —
    /// a large area, thoroughly learned.
    /// It has to sit near the top of what a real box produces,
    /// or every worked area saturates
    /// and the row stops being a skyline at all.
    static let fullMass = 24.0

    /// How far along the area stands, 0…1 —
    /// the one curve every height in the app is cut from.
    /// Square-rooted, because `mass` is a sum over words:
    /// without it the first area worked would dwarf every other for months,
    /// and the skyline would say more about where the learner started
    /// than about where the box now is.
    static func standing(_ tree: AreaTree) -> CGFloat {
        guard !tree.isBare else { return 0 }
        return CGFloat(min(1, sqrt(tree.mass / fullMass)))
    }

    /// How tall the area stands in the orchard.
    static func treeHeight(_ tree: AreaTree) -> CGFloat {
        guard !tree.isBare else { return 0 }
        return minHeight + (maxHeight - minHeight) * standing(tree)
    }

    /// The box a session summary gives its one tree,
    /// which is the only thing carrying the area's standing there —
    /// `solitary` fills whatever box it is handed,
    /// so a fixed box drew a first-day sprout the full height
    /// of a thoroughly learned area,
    /// a bare stem running the length of the screen.
    /// The floor is what a seedling needs to be a seedling
    /// and not a smudge.
    static let heroMinHeight: CGFloat = 78
    static let heroMaxHeight: CGFloat = 190

    /// `ceiling` lifts the grown tree's box on a screen with room to give,
    /// the seedling's floor rising in proportion so standing still reads as height.
    static func heroHeight(_ tree: AreaTree, ceiling: CGFloat = heroMaxHeight) -> CGFloat {
        let top = max(ceiling, heroMaxHeight)
        let floor = top * heroMinHeight / heroMaxHeight
        return floor + (top - floor) * standing(tree)
    }

    /// One tree alone, filling a box of its own —
    /// what a session summary draws.
    /// Far bigger than in the orchard,
    /// where it shares the width with five others.
    ///
    /// The ground line sits a little clear of the bottom edge,
    /// because what a tree puts BELOW it —
    /// the day's fresh earth, the leaves it dropped —
    /// is drawn there and would otherwise be shaved off.
    /// Above it the tree takes everything that is left,
    /// its crown's marks drawn past the top edge by the canvas bleed.
    static func solitary(_ tree: AreaTree, in size: CGSize) -> TreeMark {
        let baseline = size.height - 7
        return TreeMark(tree: tree,
                        foot: CGPoint(x: size.width / 2, y: baseline),
                        height: min(baseline, size.width * 0.8),
                        cell: CGRect(origin: .zero, size: size),
                        baseline: baseline)
    }

    /// Stable 0..<1 noise for one (id, property) —
    /// the SplitMix64 finish `ConfettiView` uses,
    /// over an FNV-1a fold of the id.
    static func noise(_ id: String, _ salt: Int) -> Double {
        var rng = SplitMix64(seed: SplitMix64(id).seed &+ UInt64(bitPattern: Int64(salt)))
        return rng.next()
    }
}
