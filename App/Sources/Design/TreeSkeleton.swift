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
// Marks are dart-thrown over the forked wood (Bridson 2007's Poisson-disk sampling):
// a candidate lands uniformly along the branches, and is kept only if it clears every
// mark already kept by a minimum spacing, measured in the plane — so no two marks
// overlap wherever the branches cross or crowd.

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
        // A few spare slots past the last word, so a prefix never runs short.
        let hung = hang(on: growth.segments, seed: seed, count: target + 6)
        return fit(segments: growth.segments, slots: hung, count: target, in: rect)
    }

    // MARK: Growing

    private struct Growth {
        let seed: UInt64
        let vigor: Double
        var segments: [TreeSegment] = []

        init(seed: UInt64, vigor: Double) {
            self.seed = seed
            self.vigor = vigor
        }

        /// One branch and everything above it.
        /// `path` names the branch from the trunk up, and seeds everything about it.
        mutating func branch(path: UInt64, from origin: CGPoint, angle: Double, length: Double,
                             width: Double, depth: Int, side: Double) {
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
            guard grown >= 1, depth < TreeSkeleton.maxDepth, vigor > Double(depth + 1) else { return }
            // Branches reach for the light a little more with every generation.
            let lifted = angle + (-Double.pi / 2 - angle) * 0.06 * Double(depth + 1)
            branch(path: path &* 4 &+ 1, from: end, angle: lifted + dominantTurn,
                   length: length * dominantLength, width: width * 0.80,
                   depth: depth + 1, side: -side)
            branch(path: path &* 4 &+ 2, from: end, angle: lifted + side * lateralTurn,
                   length: length * lateralLength, width: width * 0.60,
                   depth: depth + 1, side: -side)
            if third {
                branch(path: path &* 4 &+ 3, from: end, angle: lifted - side * thirdTurn,
                       length: length * 0.55, width: width * 0.45,
                       depth: depth + 1, side: side)
            }
        }
    }

    // MARK: Hanging

    /// `count` slots over the forked wood, in the order they were accepted.
    private static func hang(on segments: [TreeSegment], seed: UInt64, count: Int) -> [LeafSlot] {
        // why: the trunk stays bare below the first fork, as in any tree's structure.
        let forked = segments.filter { $0.depth >= 1 }
        let wood = forked.isEmpty ? segments : forked
        let lengths = wood.map { Double(hypot($0.end.x - $0.start.x, $0.end.y - $0.start.y)) }
        let total = lengths.reduce(0, +)
        guard total > 0, count > 0 else { return [] }
        var rng = SplitMix64(seed: seed ^ SplitMix64.mix(0x5EED_1EAF))
        // why: set by the wood alone, never by `count`, so a longer run replays a shorter
        // one exactly and only appends — adding a word moves no mark already hanging.
        var spacing = total / 8

        // Uniform along the wood: a segment picked by its length, then a point along it,
        // nudged off the center line to either side.
        func candidate() -> LeafSlot {
            var pick = rng.next() * total
            var index = 0
            while index < wood.count - 1, pick > lengths[index] {
                pick -= lengths[index]
                index += 1
            }
            let segment = wood[index]
            let along = CGFloat(rng.next())
            let offset = (rng.next() * 2 - 1) * 0.6 * spacing
            let turn = rng.range(0.6, 1.4)
            let angle = atan2(Double(segment.end.y - segment.start.y), Double(segment.end.x - segment.start.x))
            let point = CGPoint(
                x: segment.start.x + (segment.end.x - segment.start.x) * along + CGFloat(cos(angle + .pi / 2) * offset),
                y: segment.start.y + (segment.end.y - segment.start.y) * along + CGFloat(sin(angle + .pi / 2) * offset))
            return LeafSlot(point: point, angle: angle + (offset < 0 ? -turn : turn))
        }

        // why: the trunk counts as taken, so no mark sits at the fork on top of it.
        let trunk = segments.first { $0.depth == 0 }
        var slots: [LeafSlot] = []
        // why: a spacing the wood can no longer fit shrinks rather than stalls, so the
        // first marks spread over the whole crown and later ones fill the gaps between.
        for _ in 0..<14 where slots.count < count {
            var misses = 0
            while misses < 60, slots.count < count {
                let slot = candidate()
                if clear(slot.point, of: trunk) >= spacing, slots.allSatisfy({
                    Double(hypot($0.point.x - slot.point.x, $0.point.y - slot.point.y)) >= spacing
                }) {
                    slots.append(slot)
                    misses = 0
                } else {
                    misses += 1
                }
            }
            spacing *= 0.65
        }
        while slots.count < count { slots.append(candidate()) }
        return slots
    }

    /// How far `point` stands from the straight line of `segment`.
    private static func clear(_ point: CGPoint, of segment: TreeSegment?) -> Double {
        guard let segment else { return .infinity }
        let dx = segment.end.x - segment.start.x, dy = segment.end.y - segment.start.y
        let t = max(0, min(1, ((point.x - segment.start.x) * dx + (point.y - segment.start.y) * dy)
                              / max(dx * dx + dy * dy, 1e-9)))
        return Double(hypot(point.x - segment.start.x - dx * t, point.y - segment.start.y - dy * t))
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
