import Foundation
import SprossKern

// MARK: - Tree mark
//
// One tree placed for drawing: kern says where it stands (`TreesLayout`, `AreaTree.solitary`)
// and this carries that in points, with kern's picture of it (`TreePicture`) ready to draw.

struct TreeMark {
    let tree: AreaGrowth
    /// The area's name, read off the kern value once rather than per mark drawn.
    let area: String
    /// Where the trunk meets the ground.
    let foot: CGPoint
    /// Foot to the top of the crown.
    let height: CGFloat
    /// The cell the label and the tap target fill.
    let cell: CGRect
    /// The ground line this tree's whole row shares.
    let baseline: CGFloat
    /// The tree as it is drawn, grown from its seed (`AreaTree.seed`) and converted once.
    let art: TreeArt

    init(tree: AreaGrowth, garden: String, foot: CGPoint, height: CGFloat, cell: CGRect, baseline: CGFloat) {
        self.tree = tree
        self.area = tree.area
        self.foot = foot
        self.height = height
        self.cell = cell
        self.baseline = baseline
        art = TreeArt(TreePicture.companion.of(tree: tree, seed: AreaTree.shared.seed(garden: garden, area: tree.area),
                                               footX: foot.x, footY: foot.y, height: height))
    }

    /// One tree alone, filling a box of its own — what a session summary draws.
    static func solitary(_ tree: AreaGrowth, garden: String, in size: CGSize) -> TreeMark {
        let stand = AreaTree.shared.solitary(width: size.width, height: size.height)
        return TreeMark(tree: tree, garden: garden,
                        foot: CGPoint(x: stand.footX, y: stand.footY),
                        height: CGFloat(stand.height),
                        cell: CGRect(origin: .zero, size: size),
                        baseline: CGFloat(stand.footY))
    }

    /// Every tree stood in rows across `width`, back to front, and how tall they stand together.
    static func placed(_ trees: [AreaGrowth], garden: String, width: CGFloat) -> (marks: [TreeMark], height: CGFloat) {
        let plan = TreesLayout.shared.place(trees: trees, width: width)
        let marks = plan.spots.map { spot in
            let tree = trees[Int(spot.index)]
            return TreeMark(tree: tree, garden: garden,
                            foot: CGPoint(x: spot.footX, y: spot.baseline),
                            height: CGFloat(spot.height),
                            cell: CGRect(x: spot.cellX, y: spot.cellY,
                                         width: spot.cellWidth, height: spot.cellHeight),
                            baseline: CGFloat(spot.baseline))
        }
        return (marks, CGFloat(plan.height))
    }
}
