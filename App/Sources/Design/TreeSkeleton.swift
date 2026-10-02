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
// Leaves are the level past the last stems (Weber & Penn §4.6): they grow along every
// segment but the trunk, spread evenly and alternating sides, clear of each fork.

/// One length of branch: a bowed center line that tapers along its length.
struct TreeSegment {
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint
    let startWidth: CGFloat
    let endWidth: CGFloat
    let depth: Int
}

/// Somewhere a mark can hang, and the way the mark faces.
struct LeafSlot {
    let point: CGPoint
    /// Outward from the branch it hangs off.
    let angle: Double
}

struct TreeSkeleton {
    let segments: [TreeSegment]
    /// Slots in the order they were hung.
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
        1.3 + (Double(maxDepth) - 1.3) * min(1, (Double(canopy.count) / 30).squareRoot())
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
        growth.branch(path: 1, from: .zero, angle: -.pi / 2, length: 0.16, width: trunkWidth,
                      depth: 0, side: 1)
        let hung = hang(on: growth.segments, twigs: growth.twigs, seed: seed, count: target)
        return fit(segments: growth.segments, slots: hung, count: target, in: rect)
    }

    // MARK: Growing

    /// A segment marks hang on, with the path that seeds it; `tip` if it forks no further.
    private typealias Carrier = (segment: Int, path: UInt64, tip: Bool)

    private struct Growth {
        let seed: UInt64
        let vigor: Double
        var segments: [TreeSegment] = []
        /// The wood marks hang on: every segment but the trunk.
        var twigs: [Carrier] = []

        init(seed: UInt64, vigor: Double) {
            self.seed = seed
            self.vigor = vigor
        }

        /// One branch and everything above it.
        /// `path` names the branch from the trunk up, and seeds everything about it.
        mutating func branch(path: UInt64, from origin: CGPoint, angle heading: Double, length: Double,
                             width: Double, depth: Int, side: Double) {
            let grown = min(1, max(0, vigor - Double(depth)))
            guard grown > 0 else { return }
            // why: a branch may dip a little below horizontal, no further — turns add up
            // over the generations, and a drooping limb hangs its leaves under the crown.
            let angle = min(0.25, max(-Double.pi - 0.25, heading))
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

            // Taken from the seed even when this branch forks no further, so the
            // random sequence — and with it the shape — never depends on how deep
            // the tree has grown.
            let lean = rng.next() < 0.5 ? -1.0 : 1.0
            // why: the lead bends little and the others turn well away, so no two siblings
            // part at less than about 30° and run side by side.
            let dominantTurn = rng.range(0.05, 0.20) * lean
            let dominantLength = rng.range(0.86, 0.95)
            let lateralTurn = rng.range(0.75, 1.10)
            let lateralLength = rng.range(0.76, 0.90)
            // why: the trunk always forks three ways, so the crown has low limbs on both sides.
            let third = rng.next() < 0.5 || depth == 0
            let thirdTurn = rng.range(0.80, 1.10)

            // A branch whose children have not started yet is a tip, full length or not.
            // why: a twig sprouts at half length, never as a stub — a stub's marks would
            // all sit on the fork it grows from.
            let forks = grown >= 1 && depth < TreeSkeleton.maxDepth && vigor >= Double(depth + 1) + 0.5
            if depth >= 1 { twigs.append((segments.count - 1, path, !forks)) }
            guard forks else { return }
            // why: a short trunk under long first limbs — a low, bushy crown that fits an
            // orchard row instead of a tall stem with a tuft on top.
            let next = depth == 0 ? length * 1.2 : length
            // why: limbs sag under their weight, the more level and the later-born the further.
            let sagged = angle + 0.02 * Double(depth + 1) * cos(angle)
            branch(path: path &* 4 &+ 1, from: end, angle: sagged + dominantTurn,
                   length: next * dominantLength, width: width * 0.80,
                   depth: depth + 1, side: -side)
            branch(path: path &* 4 &+ 2, from: end, angle: sagged + side * lateralTurn,
                   length: next * lateralLength, width: width * 0.60,
                   depth: depth + 1, side: -side)
            if third {
                branch(path: path &* 4 &+ 3, from: end, angle: sagged - side * thirdTurn,
                       length: next * 0.7, width: width * 0.45,
                       depth: depth + 1, side: side)
            }
        }
    }

    // MARK: Hanging

    /// `count` slots on the wood, the levelest and oldest wood dealt first so the first marks,
    /// fruit and blossom, hang as spur fruit does; once every carrier holds one, the next goes
    /// to whichever holds the fewest for its weighted length. The sequence never depends on
    /// `count`, so hanging another word moves none already hanging.
    private static func hang(on segments: [TreeSegment], twigs: [Carrier], seed: UInt64,
                             count: Int) -> [LeafSlot] {
        guard count > 0, !twigs.isEmpty else { return [] }
        func chord(_ c: Carrier) -> Double {
            let s = segments[c.segment]
            return max(Double(hypot(s.end.x - s.start.x, s.end.y - s.start.y)), 1e-6)
        }
        func rank(_ c: Carrier) -> Double {
            let s = segments[c.segment]
            return Double(abs(s.end.x - s.start.x)) / chord(c) + (c.tip ? 0 : 1)
        }
        let order = twigs.map { (carrier: $0, rank: rank($0), hash: SplitMix64.mix(seed ^ $0.path)) }
            .sorted { $0.rank != $1.rank ? $0.rank > $1.rank : $0.hash < $1.hash }
            .map(\.carrier)
        // why: inner wood counts half its length, so the twigs carry most of the foliage.
        let weights = order.map { chord($0) * ($0.tip ? 1 : 0.5) }
        var held = [Int](repeating: 0, count: order.count)
        var slots: [LeafSlot] = []
        while slots.count < count {
            let i = slots.count < order.count
                ? slots.count
                : held.indices.min { Double(held[$0]) / weights[$0] < Double(held[$1]) / weights[$1] }!
            slots.append(slot(held[i], on: segments[order[i].segment], flip: order[i].path & 1 == 0))
            held[i] += 1
        }
        return slots
    }

    /// A carrier's k-th mark, where the base-2 van der Corput sequence puts it within
    /// [0.15, 0.95] of the way along: each new mark halves a gap the earlier ones left.
    /// Marks alternate sides and sit on the bark, leaning away from the wood.
    private static func slot(_ k: Int, on twig: TreeSegment, flip: Bool) -> LeafSlot {
        var spread = 0.0, step = 0.5, n = k
        while n > 0 { spread += Double(n & 1) * step; n >>= 1; step /= 2 }
        let t = 0.95 - 0.8 * spread, u = 1 - t
        let point = CGPoint(
            x: u * u * twig.start.x + 2 * u * t * twig.control.x + t * t * twig.end.x,
            y: u * u * twig.start.y + 2 * u * t * twig.control.y + t * t * twig.end.y)
        let along = atan2(Double(2 * u * (twig.control.y - twig.start.y) + 2 * t * (twig.end.y - twig.control.y)),
                          Double(2 * u * (twig.control.x - twig.start.x) + 2 * t * (twig.end.x - twig.control.x)))
        let side: Double = (k % 2 == 1) != flip ? 1 : -1
        let bark = (Double(twig.startWidth) * u + Double(twig.endWidth) * t) / 2 * side
        // why: a leaf follows its wood, splayed to one side, and never points below horizontal.
        return LeafSlot(point: CGPoint(x: point.x + CGFloat(cos(along + .pi / 2) * bark),
                                       y: point.y + CGFloat(sin(along + .pi / 2) * bark)),
                        angle: min(-0.3, max(-Double.pi + 0.3, along + side * 0.9)))
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
            LeafSlot(point: place($0.point), angle: $0.angle)
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
