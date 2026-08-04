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

/// One tree placed: where it stands, how big, and where its canopy sits.
struct TreeMark {
    let tree: AreaTree
    /// Where the trunk meets the ground.
    let foot: CGPoint
    /// Trunk top, and the canopy's centre.
    let crown: CGPoint
    let canopyRadius: CGFloat
    /// The cell the label and the tap target fill.
    let cell: CGRect
    /// The ground line this tree's whole row shares.
    let baseline: CGFloat
}

enum ForestLayout {

    /// Cell size — six across a 354pt content width, which is the phone.
    static let minCellWidth: CGFloat = 52
    static let rowHeight: CGFloat = 70
    static let labelHeight: CGFloat = 18
    static let rowGap: CGFloat = DL.Space.s

    /// Trunk heights, foot to crown. The floor is a seedling; the ceiling keeps
    /// the tallest area inside its row instead of towering over the others.
    static let minTrunk: CGFloat = 9
    static let maxTrunk: CGFloat = 40

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
            let foot = CGPoint(x: cell.midX, y: baseline)
            return TreeMark(
                tree: tree,
                foot: foot,
                crown: CGPoint(x: foot.x, y: baseline - trunkHeight(tree)),
                canopyRadius: canopyRadius(tree),
                cell: cell,
                baseline: baseline
            )
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
    static func trunkHeight(_ tree: AreaTree) -> CGFloat {
        guard !tree.isBare else { return 0 }
        return minTrunk + (maxTrunk - minTrunk) * CGFloat(min(1, sqrt(tree.mass / fullMass)))
    }

    /// The canopy grows with what is IN it, and saturates: past a couple of
    /// dozen words the marks pack tighter rather than the tree spreading wider.
    static func canopyRadius(_ tree: AreaTree) -> CGFloat {
        guard tree.canopyCount > 0 else { return 0 }
        return 5 + 13 * CGFloat(min(1, sqrt(Double(tree.canopyCount) / 26)))
    }

    /// The canopy is built from a few overlapping lobes, one per branch, rather
    /// than from one disc: a circle on a stick is the shape of a drawn lollipop,
    /// and an irregular outline is most of what separates the two.
    ///
    /// Returns each lobe's centre and radius, the first being the crown itself.
    static func lobes(_ mark: TreeMark) -> [(centre: CGPoint, radius: CGFloat)] {
        let radius = mark.canopyRadius
        guard radius > 0 else { return [] }
        let count = radius > 10 ? 4 : 3
        return (0..<count).map { index in
            guard index > 0 else {
                return (CGPoint(x: mark.crown.x, y: mark.crown.y - radius * 0.1), radius * 0.66)
            }
            let spread = ForestLayout.noise(mark.tree.id, 20 + index)
            // Lobes fan upward and outward from the crown, never below it.
            let angle = .pi + (Double(index) - 0.5) / Double(count - 1) * .pi + (spread - 0.5) * 0.5
            let reach = radius * CGFloat(0.42 + spread * 0.22)
            return (CGPoint(x: mark.crown.x + CGFloat(cos(angle)) * reach,
                            y: mark.crown.y + CGFloat(sin(angle)) * reach * 0.85),
                    radius * CGFloat(0.46 + spread * 0.16))
        }
    }

    /// Where the canopy's marks sit: a golden-angle spiral WITHIN each lobe,
    /// which fills a disc evenly at any count with no collision test and no
    /// rejection loop — so a canopy of three and a canopy of sixty are both
    /// even, while the lobes keep the outline from closing into a circle.
    ///
    /// Ordered outward within its lobe, and the drawing spends that order:
    /// fruit and blossom take the rim where they read, leaves fill in behind.
    static func canopy(_ mark: TreeMark, count: Int) -> [CGPoint] {
        guard count > 0 else { return [] }
        let lobes = lobes(mark)
        guard !lobes.isEmpty else { return [] }
        let goldenAngle = 2.399963229728653
        let turn = noise(mark.tree.id, 7) * .pi * 2
        var perLobe = [Int](repeating: 0, count: lobes.count)

        return (0..<count).map { index in
            // why: the outermost marks are drawn first and must land on the
            // OUTER lobes, so blossom and fruit sit at the canopy's edge.
            let lobeIndex = lobes.count - 1 - (index % lobes.count)
            let lobe = lobes[lobeIndex]
            let seat = perLobe[lobeIndex]
            perLobe[lobeIndex] += 1
            let capacity = max(1, count / lobes.count + 1)
            let ratio = (Double(seat) + 0.5) / Double(capacity)
            let radius = Double(lobe.radius) * sqrt(min(1, ratio))
            let angle = Double(index) * goldenAngle + turn
            return CGPoint(
                x: lobe.centre.x + CGFloat(cos(angle) * radius),
                y: lobe.centre.y + CGFloat(sin(angle) * radius * 0.86)
            )
        }
    }

    /// Stable 0..<1 noise for one (id, property) — the SplitMix64 finish
    /// `ConfettiView` uses, over an FNV-1a fold of the id.
    static func noise(_ id: String, _ salt: Int) -> Double {
        var hash: UInt64 = 0xCBF2_9CE4_8422_2325
        for byte in id.utf8 {
            hash = (hash ^ UInt64(byte)) &* 0x1000_0000_01B3
        }
        var x = hash &+ UInt64(bitPattern: Int64(salt)) &* 0x9E37_79B9_7F4A_7C15
        x = (x ^ (x >> 33)) &* 0xFF51_AFD7_ED55_8CCD
        x = (x ^ (x >> 33)) &* 0xC4CE_B9FE_1A85_EC53
        x ^= x >> 33
        return Double(x >> 11) / Double(1 << 53)
    }
}
