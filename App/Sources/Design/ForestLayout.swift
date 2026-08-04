import Foundation

// MARK: - Forest layout
//
// One tree per area, standing in rows. Plain values: no SwiftUI, no kern, no
// drawing — so a preview can fabricate a box at any age.
//
// The unit is the AREA, not the card. A word is a leaf, not a character: five
// hundred individual plants can only ever be read as texture, and drawing them
// as objects made a freshly packed area look like a spilled bag rather than
// like sowing. Sixteen trees can each be looked at.
//
// Every tree in a row stands on ONE baseline, so their heights compare
// directly. That comparison is the picture: a row is a skyline, and unlike the
// old per-card patches — sized by how many words the CATALOG holds, and so
// identical on install day and a year in — a skyline changes shape as the box
// grows.

/// One area's tree, in the terms the drawing needs: counts of marks, not cards.
struct AreaTree: Identifiable {
    let id: String
    let emoji: String
    let title: String
    /// Words that have reached the canopy, split by how far each has come.
    /// A word is exactly one mark — leaf, then blossom, then fruit — so the
    /// canopy is made of the words themselves and nothing is counted twice.
    let leaves: Int
    let blossoms: Int
    let fruit: Int
    /// Words started but not yet in the canopy — why the tree is growing at all.
    let growing: Int
    /// Words that lapsed: a couple of leaves on the ground, never a smaller tree.
    let fallen: Int
    /// The area's aggregate growth, in words-worth-of-stability.
    let mass: Double
    /// Something here was answered today.
    let tendedToday: Bool

    /// Whether anything at all has happened here. A bare area draws as bare
    /// ground, NOT as empty slots: a catalog the learner never chose must not
    /// read as a list of things they have failed to do.
    var isBare: Bool { leaves + blossoms + fruit + growing == 0 }

    /// Everything standing in the canopy.
    var canopyCount: Int { leaves + blossoms + fruit }
}

/// One area's tree before and after something happened to it — a finished
/// round, most often. What is drawn is the whole tree at some point between,
/// so the learner sees what they already had and watches this round land on it.
struct TreeTransition {
    let before: AreaTree
    let after: AreaTree

    /// The tree partway between. Counts round rather than truncate, so a single
    /// new leaf arrives halfway through rather than only at the very end.
    func at(_ progress: Double) -> AreaTree {
        let t = min(1, max(0, progress))
        func step(_ from: Int, _ to: Int) -> Int {
            Int((Double(from) + (Double(to) - Double(from)) * t).rounded())
        }
        return AreaTree(
            id: after.id, emoji: after.emoji, title: after.title,
            leaves: step(before.leaves, after.leaves),
            blossoms: step(before.blossoms, after.blossoms),
            fruit: step(before.fruit, after.fruit),
            growing: step(before.growing, after.growing),
            fallen: step(before.fallen, after.fallen),
            mass: before.mass + (after.mass - before.mass) * t,
            tendedToday: after.tendedToday
        )
    }
}

/// One tree placed: where it stands and how tall, plus the branches its marks
/// hang on.
struct TreeMark {
    let tree: AreaTree
    /// Where the trunk meets the ground.
    let foot: CGPoint
    /// Foot to the top of the crown.
    let height: CGFloat
    /// The cell the label and the tap target fill.
    let cell: CGRect
    /// The ground line this tree's whole row shares.
    let baseline: CGFloat

    /// The branches, grown from the area's name and this size alone — never
    /// from a count, so the very same tree stands before and after a round and
    /// only what hangs on it moves.
    var skeleton: TreeSkeleton {
        TreeSkeleton.grown(
            seed: SplitMix64(tree.id).seed,
            in: CGRect(x: foot.x - height * 0.6, y: foot.y - height,
                       width: max(height * 1.2, 1), height: max(height, 1))
        )
    }
}

enum ForestLayout {

    /// Cell size — six across a 354pt content width, which is the phone.
    static let minCellWidth: CGFloat = 52
    static let rowHeight: CGFloat = 70
    static let labelHeight: CGFloat = 18
    static let rowGap: CGFloat = DL.Space.s

    /// Tree heights, foot to crown. The floor is a seedling; the ceiling keeps
    /// the tallest area inside its row instead of towering over the others.
    static let minHeight: CGFloat = 9
    static let maxHeight: CGFloat = 58

    /// Lays the trees out in rows across `width`, in the order given.
    static func marks(_ trees: [AreaTree], width: CGFloat) -> [TreeMark] {
        guard width > 0, !trees.isEmpty else { return [] }
        let columns = max(2, Int(width / minCellWidth))
        let cellWidth = width / CGFloat(columns)

        return trees.enumerated().map { index, tree in
            let cell = CGRect(
                x: CGFloat(index % columns) * cellWidth,
                y: CGFloat(index / columns) * (rowHeight + labelHeight + rowGap),
                width: cellWidth,
                height: rowHeight + labelHeight
            )
            // why: one baseline for the whole row — a height only says something
            // against a ground line its neighbours share.
            let baseline = cell.minY + rowHeight
            return TreeMark(tree: tree,
                            foot: CGPoint(x: cell.midX, y: baseline),
                            height: treeHeight(tree),
                            cell: cell,
                            baseline: baseline)
        }
    }

    static func height(_ trees: [AreaTree], width: CGFloat) -> CGFloat {
        marks(trees, width: width).last?.cell.maxY ?? 0
    }

    /// The mass at which a tree reaches full height — a large area, thoroughly
    /// learned. It has to sit near the top of what a real box produces, or every
    /// worked area saturates and the row stops being a skyline at all.
    static let fullMass = 24.0

    /// How tall the area stands. Square-rooted, because `mass` is a sum over
    /// words: without it the first area worked would dwarf every other for
    /// months, and the skyline would say more about where the learner started
    /// than about where the box now is.
    static func treeHeight(_ tree: AreaTree) -> CGFloat {
        guard !tree.isBare else { return 0 }
        return minHeight + (maxHeight - minHeight) * CGFloat(min(1, sqrt(tree.mass / fullMass)))
    }

    /// One tree alone, filling a box of its own — what a session summary draws.
    /// Far bigger than in the forest, where it shares the width with five others.
    static func solitary(_ tree: AreaTree, in size: CGSize) -> TreeMark {
        let baseline = size.height - 4
        return TreeMark(tree: tree,
                        foot: CGPoint(x: size.width / 2, y: baseline),
                        height: min(size.height - 12, size.width * 0.8),
                        cell: CGRect(origin: .zero, size: size),
                        baseline: baseline)
    }

    /// Stable 0..<1 noise for one (id, property) — the SplitMix64 finish
    /// `ConfettiView` uses, over an FNV-1a fold of the id.
    static func noise(_ id: String, _ salt: Int) -> Double {
        var rng = SplitMix64(seed: SplitMix64(id).seed &+ UInt64(bitPattern: Int64(salt)))
        return rng.next()
    }
}
