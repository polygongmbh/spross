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
    /// How far each canopy word has come, 0…1, most-grown first — one entry per
    /// mark, in the order the marks are drawn. The canopy's marks take their
    /// SIZE from these, so a leaf is the size of the word it stands for rather
    /// than of a hash: within one tier a word held for a week and one held for
    /// a month stop drawing identically.
    var reaches: [Double] = []

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

    /// Where the animation starts: `before`, but with no tier holding more than
    /// it ends with.
    ///
    /// why: a round can take a mark off the tree — a word that lapsed leaves the
    /// canopy, and a word that matures moves from one tier to the next. Played
    /// forward those read as marks being removed, and a summary that takes
    /// something away in front of the learner is the wrong screen for it. A tier
    /// that shrank simply starts where it ends; what the round ADDED still
    /// animates, and what it cost is still there in the finished picture.
    private var start: AreaTree {
        AreaTree(id: before.id, emoji: before.emoji, title: before.title,
                 leaves: min(before.leaves, after.leaves),
                 blossoms: min(before.blossoms, after.blossoms),
                 fruit: min(before.fruit, after.fruit),
                 growing: min(before.growing, after.growing),
                 fallen: min(before.fallen, after.fallen),
                 mass: min(before.mass, after.mass),
                 tendedToday: before.tendedToday)
    }

    /// The tree partway between. Counts round rather than truncate, so a single
    /// new leaf arrives halfway through rather than only at the very end.
    func at(_ progress: Double) -> AreaTree {
        let before = start
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
            tendedToday: after.tendedToday,
            // why: the FINISHED tree's, always. Both lists run most-grown first,
            // so a mark keeps its word as the count climbs; interpolating the
            // list itself would slide every word's size along the canopy.
            reaches: after.reaches
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

    /// The branches, grown from the area's name and its standing — so the very
    /// same tree stands before and after a round and only what hangs on it moves.
    var skeleton: TreeSkeleton {
        TreeSkeleton.grown(
            seed: SplitMix64(tree.id).seed,
            // why: both counts come from the TREE — the finished one, whatever
            // moment is being drawn — and never from the height it is drawn at.
            // A transition scales the height every frame, and a crown that grew
            // a generation or a slot halfway through would reshuffle every slot
            // under the marks already hanging on them.
            depth: TreeSkeleton.generations(for: tree),
            slots: TreeSkeleton.slots(for: tree),
            in: CGRect(x: foot.x - height * 0.72, y: foot.y - height,
                       width: max(height * 1.44, 1), height: max(height, 1))
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
    ///
    /// Rows, not a grid: every tree in a row stands on ONE baseline, which is
    /// what lets two areas be compared at a glance, and that is the only thing
    /// held rigid. Everything else gives — a tree claims room in proportion to
    /// its own size, a row is only as tall as its tallest, the slack left over
    /// is spread between them, and each stands a little off its slot's centre.
    /// Equal cells in equal columns read as planting rather than as growth.
    static func marks(_ trees: [AreaTree], width: CGFloat) -> [TreeMark] {
        guard width > 0, !trees.isEmpty else { return [] }
        let room = trees.map { max(minCellWidth * 0.62, treeHeight($0) * 1.28 + 12) }

        var rows: [[Int]] = []
        var row: [Int] = []
        var used: CGFloat = 0
        for index in trees.indices {
            if !row.isEmpty, used + room[index] > width {
                rows.append(row)
                row = []
                used = 0
            }
            row.append(index)
            used += room[index]
        }
        if !row.isEmpty { rows.append(row) }

        var marks: [TreeMark] = []
        var base: CGFloat = 0
        for (rank, row) in rows.enumerated() {
            let taken = row.reduce(CGFloat(0)) { $0 + room[$1] }
            // Slack goes between the trees AND to the margins, so a short row
            // spreads out rather than crowding against the left edge.
            let gap = max(0, width - taken) / CGFloat(row.count + 1)
            let tallest = row.map { treeHeight(trees[$0]) }.max() ?? minHeight
            let band = max(rowHeight, tallest + 10)
            let pitch = band + labelHeight + rowGap

            var x = gap
            for (seat, index) in row.enumerated() {
                let drift = CGFloat(noise(trees[index].id, 31) - 0.5) * min(gap, 10)
                // why: every second tree drops half a row, and which half a row
                // starts on flips with the row — so the forest tiles diagonally
                // and a tree always has neighbours above and below it as well as
                // beside it. Fixed rather than random: an even lattice broken
                // only by the widths and the drift reads as an orchard, where
                // the wave it replaces read as a wave.
                let dropped = (seat + rank).isMultiple(of: 2)
                let stand = base + band + (dropped ? 0 : pitch * 0.5)
                let cell = CGRect(x: x + drift, y: stand - band,
                                  width: room[index], height: band + labelHeight)
                marks.append(TreeMark(tree: trees[index],
                                      foot: CGPoint(x: cell.midX, y: stand),
                                      height: treeHeight(trees[index]),
                                      cell: cell,
                                      baseline: stand))
                x += room[index] + gap
            }
            base += pitch
        }
        // why: back to front across the WHOLE forest, not within a row — once
        // rows interleave, the tree in front of you may well belong to another
        // one, and only a global order layers them correctly.
        return marks.sorted { $0.baseline < $1.baseline }
    }


    static func height(_ trees: [AreaTree], width: CGFloat) -> CGFloat {
        // why: the marks are ordered by depth, not down the page — the lowest
        // edge belongs to whichever tree stands furthest forward.
        marks(trees, width: width).map(\.cell.maxY).max() ?? 0
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
