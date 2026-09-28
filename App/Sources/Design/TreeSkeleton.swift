import CoreGraphics
import Foundation

// MARK: - Tree skeleton
//
// The branch structure, and the slots marks hang on — a pure function of a seed,
// a vigor and a slot count, all taken from the area's FINISHED standing,
// so a tree is identical before and after a round and only the marks move.
//
// A tree grows from its tips and nowhere else.
// Every branch draws its shape from its own seed — its path from the trunk —
// never from how deep the tree has grown, so a branch keeps its angle and length
// for the tree's whole life; more vigor only lengthens the youngest wood
// and, once a twig is full length, forks it.
//
// The rules are the standard ones for procedural trees at icon size
// (Weber & Penn 1995 §5: at 5–20% of screen height the BRANCH STRUCTURE has to be right):
//
//   · Monopodial branching — one child continues the parent's line and keeps most
//     of its length, the other departs sharply and is shorter.
//   · Da Vinci's rule for taper: r_parent² ≈ Σ r_child², so 0.80² + 0.60² = 1.
//   · Every segment bows slightly; a straight line never occurs in a tree.
//   · A bare trunk before the first fork.
//
// Foliage gathers at the tips first — each twig end carries a cluster, filled evenly
// across the crown — and every mark past a tip's own first one runs back along that
// tip's branch toward the trunk instead, biased inward, from the first extra word a
// tip gets onward, so a canopy leafs out along its wood as it grows rather than
// piling every mark on top of the one at the twig's end.

/// One length of branch: a bowed center line that tapers along its length.
struct TreeSegment {
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint
    let startWidth: CGFloat
    let endWidth: CGFloat
    let depth: Int
}

/// Somewhere a mark can hang: a point in a tip's cluster, the way the mark faces,
/// and which cluster it belongs to.
struct LeafSlot {
    let point: CGPoint
    /// Outward from the twig it hangs off.
    let angle: Double
    let tip: Int
    /// The twig's own end — where fruit and blossom belong, on the youngest wood.
    let bearing: Bool
}

struct TreeSkeleton {
    let segments: [TreeSegment]
    /// Slots in a stable order: every tip's first slot, then every tip's second, …
    /// Marks fill from the front, so adding one never moves those already placed.
    let slots: [LeafSlot]
    /// The typical gap between neighboring marks — what a mark is sized against,
    /// so a crown of twenty words and one of sixty cover the same share of themselves.
    let pitch: CGFloat

    /// The deepest generation a tree ever forks to; past it the twigs are under a point.
    static let maxDepth = 5

    /// How far the tree has grown, in generations: 1.3 is a trunk just starting to fork,
    /// `maxDepth` a crown forked all the way out.
    /// From the WORDS, never the height — height comes from stability,
    /// which climbs while a word is merely getting stronger.
    static func vigor(for canopy: Canopy) -> Double {
        1.3 + (Double(maxDepth) - 1.3) * min(1, (Double(canopy.count) / 60).squareRoot())
    }

    /// How many slots the tree hangs out — one per word, with a floor that keeps a
    /// handful of words from each claiming a quarter of the crown.
    static func slots(for canopy: Canopy) -> Int { max(8, canopy.count) }

    /// Grows one tree, fitted into `rect` with its foot on the bottom edge.
    static func grown(seed: UInt64, vigor: Double, slots target: Int, in rect: CGRect) -> TreeSkeleton {
        var growth = Growth(seed: seed, vigor: vigor)
        // Grown in unit space pointing up, then measured and fitted:
        // the shape must not depend on the box it is asked to fill.
        let trunkWidth = 0.014 + 0.011 * vigor
        growth.branch(path: 1, from: .zero, angle: -.pi / 2, length: 0.24, width: trunkWidth,
                      depth: 0, side: 1)
        let hung = growth.hang(target: target)
        return fit(segments: growth.segments, slots: hung, count: target, in: rect)
    }

    // MARK: Growing

    /// One length of a tip's own lineage — the segments from the trunk down to it —
    /// each usable as a place to hang a mark, not only the tip's own last one.
    private struct LineageSegment {
        let base: CGPoint
        let end: CGPoint
        let angle: Double
        let reach: Double
    }

    private struct Tip {
        let path: UInt64
        /// Trunk-first, own segment last — its last entry IS the tip's own segment,
        /// so nothing about the tip itself needs repeating outside this array.
        let lineage: [LineageSegment]
    }

    private struct Growth {
        let seed: UInt64
        let vigor: Double
        var segments: [TreeSegment] = []
        var tips: [Tip] = []

        init(seed: UInt64, vigor: Double) {
            self.seed = seed
            self.vigor = vigor
        }

        /// One branch and everything above it.
        /// `path` names the branch from the trunk up, and seeds everything about it.
        mutating func branch(path: UInt64, from origin: CGPoint, angle: Double, length: Double,
                             width: Double, depth: Int, side: Double,
                             lineage: [LineageSegment] = []) {
            let grown = min(1, max(0, vigor - Double(depth)))
            guard grown > 0 else { return }
            var rng = SplitMix64(seed: SplitMix64.mix(seed ^ SplitMix64.mix(path)))

            let reach = length * grown
            let end = CGPoint(x: origin.x + CGFloat(cos(angle) * reach),
                              y: origin.y + CGFloat(sin(angle) * reach))
            let bow = reach * rng.range(0.04, 0.10) * (rng.next() < 0.5 ? -1 : 1)
            let control = CGPoint(x: (origin.x + end.x) / 2 + CGFloat(cos(angle + .pi / 2) * bow),
                                  y: (origin.y + end.y) / 2 + CGFloat(sin(angle + .pi / 2) * bow))
            // A growing tip narrows to a point; a finished one hands its width on.
            let endWidth = width * (grown < 1 ? 0.45 + 0.35 * grown : 0.80)
            // The trunk flares where it meets the ground.
            let startWidth = depth == 0 ? width * 1.3 : width
            segments.append(TreeSegment(start: origin, control: control, end: end,
                                        startWidth: CGFloat(startWidth), endWidth: CGFloat(endWidth),
                                        depth: depth))
            let ownLineage = lineage + [LineageSegment(base: origin, end: end, angle: angle, reach: reach)]

            // Taken from the seed even when this branch forks no further, so the
            // random sequence — and with it the shape — never depends on how deep
            // the tree has grown.
            let lean = rng.next() < 0.5 ? -1.0 : 1.0
            let dominantTurn = rng.range(0.10, 0.30) * lean
            let dominantLength = rng.range(0.80, 0.92)
            let lateralTurn = rng.range(0.62, 1.08)
            let lateralLength = rng.range(0.66, 0.82)
            let third = depth <= 2 && rng.next() < 0.32
            let thirdTurn = rng.range(0.45, 0.85)

            // A branch whose children have not started yet is a tip, full length or not.
            guard grown >= 1, depth < TreeSkeleton.maxDepth, vigor > Double(depth + 1) else {
                tips.append(Tip(path: path, lineage: ownLineage))
                return
            }
            // Branches reach for the light a little more with every generation.
            let lifted = angle + (-Double.pi / 2 - angle) * 0.06 * Double(depth + 1)
            branch(path: path &* 4 &+ 1, from: end, angle: lifted + dominantTurn,
                   length: length * dominantLength, width: width * 0.80,
                   depth: depth + 1, side: -side, lineage: ownLineage)
            branch(path: path &* 4 &+ 2, from: end, angle: lifted + side * lateralTurn,
                   length: length * lateralLength, width: width * 0.60,
                   depth: depth + 1, side: -side, lineage: ownLineage)
            if third {
                branch(path: path &* 4 &+ 3, from: end, angle: lifted - side * thirdTurn,
                       length: length * 0.55, width: width * 0.45,
                       depth: depth + 1, side: side, lineage: ownLineage)
            }
        }

        /// Every tip's cluster, dealt out tip by tip: each tip's k-th slot comes before
        /// any tip's (k+1)-th, so the crown fills evenly and every cluster thickens together.
        func hang(target: Int) -> [LeafSlot] {
            guard !tips.isEmpty, target > 0 else { return [] }
            let order = tips.indices.sorted {
                SplitMix64.mix(seed ^ tips[$0].path) < SplitMix64.mix(seed ^ tips[$1].path)
            }
            let depth = target / tips.count + 2
            var slots: [LeafSlot] = []
            for k in 0..<depth {
                for index in order { slots.append(slot(k, of: tips[index], index)) }
            }
            return slots
        }

        /// The k-th slot of a tip's cluster, seeded by the tip and k alone —
        /// a cluster growing deeper never moves the slots it already had.
        /// k = 0 pins to the tip itself; every slot past it ranges back along the
        /// tip's whole lineage instead, biased toward the trunk end, so a cluster
        /// that keeps growing spreads leaves out along the branch rather than
        /// piling up on top of the ones already hanging at its edge.
        private func slot(_ k: Int, of tip: Tip, _ index: Int) -> LeafSlot {
            let own = tip.lineage[tip.lineage.count - 1]
            guard k > 0 else {
                return LeafSlot(point: own.end, angle: own.angle, tip: index, bearing: true)
            }
            var rng = SplitMix64(seed: SplitMix64.mix(seed ^ tip.path &+ UInt64(k) &* 0x9E37_79B9))
            let totalReach = tip.lineage.reduce(0.0) { $0 + $1.reach }
            let bias = pow(rng.next(), 1.6)
            var remaining = bias * totalReach
            var chosen = own
            var onTip = true
            var localT = 1.0
            for (i, seg) in tip.lineage.enumerated() {
                if remaining <= seg.reach {
                    chosen = seg
                    localT = seg.reach > 0 ? remaining / seg.reach : 1
                    onTip = i == tip.lineage.count - 1
                    break
                }
                remaining -= seg.reach
            }
            let along = CGPoint(x: chosen.base.x + (chosen.end.x - chosen.base.x) * CGFloat(localT),
                                y: chosen.base.y + (chosen.end.y - chosen.base.y) * CGFloat(localT))
            // why: the offset is the tip's OWN twig size regardless of which segment
            // was chosen — the trunk's segment is by far the tree's longest, and a
            // radius scaled to it flings a mark chosen there way off into empty air.
            let radius = max(own.reach, 0.07) * rng.range(0.25, 0.85)
            let off = chosen.angle + (rng.next() < 0.5 ? -1 : 1) * rng.range(0.6, 1.9)
            let point = CGPoint(x: along.x + CGFloat(cos(off) * radius),
                                y: along.y + CGFloat(sin(off) * radius))
            return LeafSlot(point: point, angle: off, tip: index, bearing: k == 1 && onTip && localT > 0.8)
        }
    }

    // MARK: Fitting

    private static func fit(segments: [TreeSegment], slots: [LeafSlot], count: Int,
                            in rect: CGRect) -> TreeSkeleton {
        var minX = CGFloat.greatestFiniteMagnitude, maxX = -CGFloat.greatestFiniteMagnitude
        var minY = CGFloat.greatestFiniteMagnitude
        for point in segments.flatMap({ [$0.start, $0.control, $0.end] })
            + slots.prefix(count).map(\.point) {
            minX = min(minX, point.x); maxX = max(maxX, point.x); minY = min(minY, point.y)
        }
        let spread = max(maxX - minX, 0.001)
        let rise = max(-minY, 0.001)
        // why: the tighter of the two constraints wins, so a wide crown is
        // narrowed to fit rather than clipped at the cell's edge.
        let scale = min(rect.height / rise, rect.width / spread)
        let foot = CGPoint(x: rect.midX - (minX + maxX) / 2 * scale, y: rect.maxY)

        func place(_ point: CGPoint) -> CGPoint {
            CGPoint(x: foot.x + point.x * scale, y: foot.y + point.y * scale)
        }
        let placed = slots.map {
            LeafSlot(point: place($0.point), angle: $0.angle, tip: $0.tip, bearing: $0.bearing)
        }
        return TreeSkeleton(
            segments: segments.map {
                TreeSegment(start: place($0.start), control: place($0.control), end: place($0.end),
                            startWidth: $0.startWidth * scale, endWidth: $0.endWidth * scale,
                            depth: $0.depth)
            },
            slots: placed,
            pitch: pitch(of: Array(placed.prefix(count))))
    }

    /// Root of (crown area / marks): the side of the square each mark gets if the
    /// crown were shared out evenly.
    private static func pitch(of slots: [LeafSlot]) -> CGFloat {
        guard slots.count > 1 else { return 1 }
        let xs = slots.map(\.point.x), ys = slots.map(\.point.y)
        let spread = (xs.max() ?? 0) - (xs.min() ?? 0)
        let rise = (ys.max() ?? 0) - (ys.min() ?? 0)
        return max(1, sqrt(max(spread, 1) * max(rise, 1) / CGFloat(slots.count)))
    }
}
