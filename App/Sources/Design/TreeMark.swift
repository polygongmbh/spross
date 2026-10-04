import Foundation
import SprossKern

// MARK: - Tree mark
//
// One tree placed for drawing: kern says where it stands (`TreesLayout`, `AreaTree.solitary`)
// and this carries that in points, with what hangs on it and the wood it hangs on.

struct TreeMark {
    let tree: AreaGrowth
    /// The area's name, read off the kern value once rather than per mark drawn.
    let area: String
    /// What the wood and the marks' scatter grow from (`AreaTree.seed`).
    let seed: String
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
    /// The branches, grown from the seed and the FINISHED count —
    /// never from the height it is drawn at, so a tree rising through a transition
    /// keeps every mark where it hangs and only what hangs on it moves.
    let skeleton: TreeSkeleton

    init(tree: AreaGrowth, garden: String, canopy: Canopy, foot: CGPoint, height: CGFloat, cell: CGRect, baseline: CGFloat) {
        self.tree = tree
        self.area = tree.area
        self.seed = AreaTree.shared.seed(garden: garden, area: tree.area)
        self.canopy = canopy
        self.foot = foot
        self.height = height
        self.cell = cell
        self.baseline = baseline
        self.skeleton = TreeSkeleton.grown(seed: seed, marks: canopy.count, buds: canopy.buds,
                                           foot: foot, height: height)
    }

    /// The shortest a tree is drawn, a seedling.
    static let minHeight = CGFloat(AreaTree.shared.MIN_HEIGHT)

    /// One tree alone, filling a box of its own — what a session summary draws —
    /// at `risen` of its full height.
    static func solitary(_ tree: AreaGrowth, garden: String, canopy: Canopy, in size: CGSize, risen: CGFloat = 1) -> TreeMark {
        let stand = AreaTree.shared.solitary(width: size.width, height: size.height)
        return TreeMark(tree: tree, garden: garden, canopy: canopy,
                        foot: CGPoint(x: stand.footX, y: stand.footY),
                        height: CGFloat(stand.height) * risen,
                        cell: CGRect(origin: .zero, size: size),
                        baseline: CGFloat(stand.footY))
    }

    /// Every tree stood in rows across `width`, back to front, and how tall they stand together.
    static func placed(_ trees: [AreaGrowth], garden: String, width: CGFloat) -> (marks: [TreeMark], height: CGFloat) {
        let plan = TreesLayout.shared.place(trees: trees, width: width)
        let marks = plan.spots.map { spot in
            let tree = trees[Int(spot.index)]
            return TreeMark(tree: tree, garden: garden, canopy: Canopy(tree),
                            foot: CGPoint(x: spot.footX, y: spot.baseline),
                            height: CGFloat(spot.height),
                            cell: CGRect(x: spot.cellX, y: spot.cellY,
                                         width: spot.cellWidth, height: spot.cellHeight),
                            baseline: CGFloat(spot.baseline))
        }
        return (marks, CGFloat(plan.height))
    }
}
