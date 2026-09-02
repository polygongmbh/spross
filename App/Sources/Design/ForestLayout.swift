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
    /// Words the learner has met that have not settled yet — the smallest mark
    /// the canopy carries. A word gets one from its first answer, so a round in
    /// a new area has something to hang.
    var buds: Int = 0
    /// Words packed and not yet met — why the tree is growing at all, and
    /// nothing that hangs on it.
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
    var isBare: Bool { canopyCount + growing == 0 }

    /// Everything standing in the canopy.
    var canopyCount: Int { leaves + blossoms + fruit + buds }
}

/// One area's tree before and after something happened to it — a finished
/// round, most often. The FINISHED tree is what gets drawn; what the transition
/// says is which of its marks the round itself put there, so the animation can
/// hand those the motion and leave the rest of the crown standing still.
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
                 buds: min(before.buds, after.buds),
                 growing: min(before.growing, after.growing),
                 fallen: min(before.fallen, after.fallen),
                 mass: min(before.mass, after.mass),
                 tendedToday: before.tendedToday)
    }

    /// How many marks were already hanging when the round began. From this rank
    /// on, a mark is one the round itself hung — it has to arrive out of
    /// nothing, where a mark below this rank was already there and only changed.
    var settledCount: Int { start.canopyCount }

    /// The canopy ranks this round moved, in canopy order: a mark that appeared,
    /// and a mark that changed tier where it hangs — a word maturing pushes the
    /// blossom boundary out by one, so the leaf at that rank becomes a blossom
    /// without anything else on the tree shifting.
    var changedRanks: [Int] {
        let was = start
        return (0..<after.canopyCount).filter { tier($0, was) != tier($0, after) }
    }

    /// What a rank draws as: nothing, or one of the four marks.
    private func tier(_ rank: Int, _ tree: AreaTree) -> Int {
        if rank < tree.fruit { return 1 }
        if rank < tree.fruit + tree.blossoms { return 2 }
        if rank < tree.fruit + tree.blossoms + tree.leaves { return 3 }
        if rank < tree.canopyCount { return 4 }
        return 0
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
    ///
    /// Grown with the mark rather than on each read: a `Canvas` redraws more
    /// often than the forest is laid out — a scroll, a size change, a theme
    /// change — and growing a tree is the expensive half of drawing one.
    let skeleton: TreeSkeleton

    init(tree: AreaTree, foot: CGPoint, height: CGFloat, cell: CGRect, baseline: CGFloat) {
        self.tree = tree
        self.foot = foot
        self.height = height
        self.cell = cell
        self.baseline = baseline
        self.skeleton = Self.grown(tree: tree, foot: foot, height: height)
    }

    /// Grown TWICE. `TreeSkeleton` fits the wood to the box it is given, and the
    /// leaves hang off the ends of that wood — so a crown fitted flush reaches
    /// past its own box by up to half a leaf, and at hero size that is a canopy
    /// visibly sliced off along the top of the canvas. The first growing is
    /// there to be measured: it says how far this crown's marks reach, and the
    /// second is grown into a box holding that much back for them.
    private static func grown(tree: AreaTree, foot: CGPoint, height: CGFloat) -> TreeSkeleton {
        // why: both counts come from the TREE — the finished one, whatever
        // moment is being drawn — and never from the height it is drawn at.
        // A transition scales the height every frame, and a crown that grew
        // a generation or a slot halfway through would reshuffle every slot
        // under the marks already hanging on them.
        let depth = TreeSkeleton.generations(for: tree)
        let slots = TreeSkeleton.slots(for: tree)
        let seed = SplitMix64(tree.id).seed
        let box = CGRect(x: foot.x - height * 0.72, y: foot.y - height,
                         width: max(height * 1.44, 1), height: max(height, 1))
        let loose = TreeSkeleton.grown(seed: seed, depth: depth, slots: slots, in: box)
        let spill = CanopyMark.spill(of: loose, tree, in: box)
        guard spill > 0.2 else { return loose }
        // Held back on three sides only: the trunk stands on the bottom edge,
        // and a mark can never hang below the foot.
        return TreeSkeleton.grown(
            seed: seed, depth: depth, slots: slots,
            in: CGRect(x: box.minX + spill, y: box.minY + spill,
                       width: max(box.width - spill * 2, 1),
                       height: max(box.height - spill, 1)))
    }
}

enum ForestLayout {

    /// Cell size — six across a 354pt content width, which is the phone.
    static let minCellWidth: CGFloat = 52
    static let rowHeight: CGFloat = 70
    static let labelHeight: CGFloat = 18
    static let rowGap: CGFloat = Theme.spacing.sm

    /// Tree heights, foot to crown. The floor is a seedling; the ceiling keeps
    /// the tallest area inside its row instead of towering over the others.
    static let minHeight: CGFloat = 9
    static let maxHeight: CGFloat = 58

    /// Lays the trees out in rows across `width`, in the order given.
    ///
    /// Rows, not a grid: every tree in a row stands on ONE baseline, which is
    /// what lets two areas be compared at a glance. Every row is laid from the
    /// very left edge, every second row opens HALF a cell further on, and rows
    /// stand half a row apart — so the trees interleave diagonally, a tree
    /// growing up through the gap between two of the row above and two of the
    /// row below, and the forest reads as one growing mass rather than as
    /// drawers in a wall. That lattice is the only thing held rigid: a tree
    /// claims room in proportion to its own size, a row stands only as tall
    /// as its tallest, and each tree sits a little off its slot's center.
    /// Equal cells in equal columns read as planting rather than as growth.
    static func marks(_ trees: [AreaTree], width: CGFloat) -> [TreeMark] {
        guard width > 0, !trees.isEmpty else { return [] }
        let room = trees.map { max(minCellWidth * 0.62, treeHeight($0) * 1.28 + 12) }

        // One pass: the rows the rooms alone bound. Its tightest row sets the
        // gap every row shares below.
        var bound: [[Int]] = []
        var row: [Int] = []
        var used: CGFloat = 0
        for index in trees.indices {
            if !row.isEmpty, used + room[index] > width {
                bound.append(row)
                row = []
                used = 0
            }
            row.append(index)
            used += room[index]
        }
        if !row.isEmpty { bound.append(row) }

        // ONE gap for the whole forest, taken from the row that can give the
        // least — so that row fills the width and every row walks the same
        // lattice with it. A gap recomputed per row would give each row its
        // own columns, and a half-cell start would stop falling halfway.
        let gap: CGFloat = bound
            .map { row in
                let taken = row.reduce(CGFloat(0)) { $0 + room[$1] }
                return max(0, width - taken) / CGFloat(row.count + 1)
            }
            .min() ?? 0

        // Re-bind with the lattice's spacing and the half-cell lead in
        // account — a row opening a cell on fits one tree less in it.
        var rows: [[Int]] = []
        row = []
        used = 0
        var rank = 0
        for index in trees.indices {
            if !row.isEmpty, used + gap + room[index] > width {
                rows.append(row)
                row = []
                used = 0
                rank += 1
            }
            if row.isEmpty, rank.isMultiple(of: 2) == false {
                used = (room[index] + gap) / 2
            }
            row.append(index)
            used += gap + room[index]
        }
        if !row.isEmpty { rows.append(row) }

        var marks: [TreeMark] = []
        var base: CGFloat = 0
        for (rank, row) in rows.enumerated() {
            let tallest = row.map { treeHeight(trees[$0]) }.max() ?? minHeight
            let band = max(rowHeight, tallest + 10)
            // why: rows stand HALF a row apart — the same half-step the old
            // diagonal tiling used, but as whole rows: a row's trees grow up
            // through the gaps of the rows around them instead of starting
            // under a shelf of air, and the forest reads as one growing mass.
            let pitch = (band + labelHeight + rowGap) / 2

            // why: half of the row's OWN first cell, and the same for every
            // row — so the half steps against the row above it are all the
            // same, and the lattice drifts only where a tree's width differs.
            // Fixed rather than random: an even lattice broken only by the
            // widths and the drift reads as an orchard, where the wave it
            // replaced read as a wave.
            let lead: CGFloat = rank.isMultiple(of: 2) ? 0 : (room[row[0]] + gap) / 2
            var x = lead
            for index in row {
                let drift = CGFloat(noise(trees[index].id, 31) - 0.5) * min(gap, 10)
                let stand = base + band
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


    /// How tall a laid-out forest stands.
    // why: the marks are ordered by depth, not down the page — the lowest
    // edge belongs to whichever tree stands furthest forward.
    static func height(of marks: [TreeMark]) -> CGFloat {
        marks.map(\.cell.maxY).max() ?? 0
    }

    static func height(_ trees: [AreaTree], width: CGFloat) -> CGFloat {
        height(of: marks(trees, width: width))
    }

    /// The mass at which a tree reaches full height — a large area, thoroughly
    /// learned. It has to sit near the top of what a real box produces, or every
    /// worked area saturates and the row stops being a skyline at all.
    static let fullMass = 24.0

    /// How far along the area stands, 0…1 — the one curve every height in the
    /// app is cut from. Square-rooted, because `mass` is a sum over words:
    /// without it the first area worked would dwarf every other for months, and
    /// the skyline would say more about where the learner started than about
    /// where the box now is.
    static func standing(_ tree: AreaTree) -> CGFloat {
        guard !tree.isBare else { return 0 }
        return CGFloat(min(1, sqrt(tree.mass / fullMass)))
    }

    /// How tall the area stands in the forest.
    static func treeHeight(_ tree: AreaTree) -> CGFloat {
        guard !tree.isBare else { return 0 }
        return minHeight + (maxHeight - minHeight) * standing(tree)
    }

    /// The box a session summary gives its one tree, which is the only thing
    /// carrying the area's standing there — `solitary` fills whatever box it is
    /// handed, so a fixed box drew a first-day sprout the full height of a
    /// thoroughly learned area, a bare stem running the length of the screen.
    /// The floor is what a seedling needs to be a seedling and not a smudge.
    static let heroMinHeight: CGFloat = 78
    static let heroMaxHeight: CGFloat = 190

    static func heroHeight(_ tree: AreaTree) -> CGFloat {
        heroMinHeight + (heroMaxHeight - heroMinHeight) * standing(tree)
    }

    /// One tree alone, filling a box of its own — what a session summary draws.
    /// Far bigger than in the forest, where it shares the width with five others.
    ///
    /// The ground line sits a little clear of the bottom edge, because what a
    /// tree puts BELOW it — the day's fresh earth, the leaves it dropped — is
    /// drawn there and would otherwise be shaved off. Above it the tree takes
    /// everything that is left: the crown holds its own marks back from the top
    /// edge (`CanopyMark.spill`), so nothing here has to be guessed at.
    static func solitary(_ tree: AreaTree, in size: CGSize) -> TreeMark {
        let baseline = size.height - 7
        return TreeMark(tree: tree,
                        foot: CGPoint(x: size.width / 2, y: baseline),
                        height: min(baseline, size.width * 0.8),
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
