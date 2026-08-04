import CoreGraphics
import Foundation

// MARK: - Tree skeleton
//
// The branch structure, and the slots leaves hang on. A pure function of a
// seed, a generation count and a slot count — all three taken from the area's
// FINISHED standing, never from the moment being drawn, which is what makes it
// identical before and after a round so only the leaves move.
//
// The rules are the standard ones for procedural trees, at the values that
// read at icon size (Weber & Penn 1995 §5 is explicit that at 5–20% of screen
// height the BRANCH STRUCTURE has to be right and leaf detail does not):
//
//   · Monopodial branching — one child continues the parent's line and keeps
//     most of its length, the other departs sharply and is much shorter.
//     Equal-angle equal-length splits read as broccoli, the loudest tell after
//     uniform stroke width.
//   · Da Vinci's rule for taper: the children's cross-sections sum to the
//     parent's, r_parent² = Σ r_child², so 0.82² + 0.57² ≈ 1. Measured against
//     ARTISTIC depictions the exponent sits near 2 rather than the ~3 real
//     trees show, which is the one to draw with.
//   · Every segment bows slightly; a straight line never occurs in a tree.
//   · A bare trunk before the first fork — at 50pt that is most of what says
//     "tree" at all.
//
// The random ranges are deliberately narrow. Every one of them is a shape
// decision, and a seed that draws from the tails of several at once is how a
// generator that is right on average still produces the occasional ugly tree —
// and there is no art director between the hash and the screen.

/// One length of branch: a bowed centre line that tapers along its length.
struct TreeSegment {
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint
    let startWidth: CGFloat
    let endWidth: CGFloat
    let depth: Int
}

/// Somewhere a leaf can hang: a point on a twig, the twig's direction there,
/// and which side of it the leaf sits on.
struct LeafSlot {
    let point: CGPoint
    let angle: Double
    let side: Double
    /// Which twig this slot belongs to. Slots are ranked twig by twig, so what
    /// the canopy leaves over falls on whole twigs — sky between clumps of
    /// foliage, the thing that reads as a tree — rather than thinning every
    /// twig into an even haze.
    let twig: Int
    /// Whether this slot sits on new outer growth — the last two generations.
    ///
    /// A tree flowers and fruits on its youngest wood; nothing heavy hangs off a
    /// structural limb, let alone off the trunk. These rank ahead of the rest
    /// and fruit and blossom take the lowest ranks, so the heaviest marks can
    /// only reach a twig that could actually hold them.
    let bearing: Bool
}

struct TreeSkeleton {
    let segments: [TreeSegment]
    /// Slots in a stable, shuffled order. Marks fill from the front, so adding
    /// one never moves those already placed — the property the summary's
    /// before/after animation is built on.
    let slots: [LeafSlot]
    /// The typical gap between neighbouring slots — what a mark is sized
    /// against, and with the pool cut to the canopy, what carries the fullness:
    /// crown over words, so twenty marks on a small tree and sixty on a large
    /// one cover the same share of their own crown. Sized against the tree's
    /// HEIGHT instead, a mark is the same fraction of every tree however much
    /// the learner has done to it.
    let pitch: CGFloat

    /// Structural generations. Four is where a deciduous tree stops gaining
    /// anything at this size; past it the segments are under a point.
    static let maxDepth = 4

    /// How many generations an area of this standing has earned — how often the
    /// tree FORKS, and nothing else. How much foliage it can carry is `slots`.
    ///
    /// A sapling forks twice, and a tree earns its next generation by growing: a
    /// four-generation crown squeezed into a dozen points reads as a scribble,
    /// its branch COUNT saying "old" while its size says "new". Taken from the
    /// area's standing rather than the size it is drawn at, so the hero and the
    /// forest show one tree.
    static func generations(for tree: AreaTree) -> Int {
        // why: from the number of WORDS, not the tree's height. Height comes
        // from aggregate stability, which climbs while a word is merely getting
        // stronger — so an area could grow tall enough to earn another
        // generation of twigs without gaining a single leaf to hang on them.
        //
        // Both thresholds are only about how fine the wood gets now, since
        // crossing one no longer changes how full the crown is. A generation
        // DOUBLES the twigs, so the fourth waits until 38: at thirty words the
        // marks were spread one to a twig over twenty-four of them and read as
        // scattered leaves, where the three-generation tree beside it carried
        // two or three to a twig and read as foliage.
        switch tree.canopyCount {
        case ..<12: return 2
        case ..<38: return 3
        default: return maxDepth
        }
    }

    /// How many slots the tree hangs out for a canopy of this size.
    ///
    /// Cut to the WORDS. Left to fall out of the branch count it was a step
    /// function — one crown offering its forty-odd slots to thirty words and to
    /// sixty alike, so the first drew a third of its twigs bare and the second
    /// could not draw eighteen of its words at all.
    ///
    /// The headroom is what keeps a full canopy from reading as upholstery: at
    /// 15% the marks come out just short of the young wood, so the gaps land on
    /// the limbs nearest the trunk, which is where a real tree's gaps are. The
    /// floor keeps a handful of words from each claiming a quarter of the crown.
    static func slots(for tree: AreaTree) -> Int {
        max(8, Int((Double(tree.canopyCount) * 1.15).rounded(.up)))
    }

    /// Grows one tree, fitted into `rect` with its foot on the bottom edge.
    /// `seed`, `depth` and `slots` alone decide it, so one area's tree is the
    /// same tree wherever it is drawn.
    static func grown(seed: UInt64, depth: Int, slots target: Int, in rect: CGRect) -> TreeSkeleton {
        var rng = SplitMix64(seed: seed)
        var segments: [TreeSegment] = []
        var sides: [Double] = []

        // Grown in unit space pointing up, then measured and fitted: the shape
        // must not depend on the box it is asked to fill.
        branch(from: .zero, angle: -.pi / 2, length: 0.30, width: 0.058,
               depth: 0, limit: depth, side: 1, rng: &rng, segments: &segments, sides: &sides)

        let hung = hang(on: segments, sides: sides, limit: depth, target: target)
        let fitted = fit(segments: segments, slots: hung, in: rect)
        // Outer growth first, and within it twig by twig with the twigs
        // themselves shuffled: what the canopy leaves over falls in clumps, and
        // filling in traversal order would leaf one side at a time. Outer first
        // earns its place twice: it is where fruit and blossom belong and they
        // take the lowest ranks, and the leftovers land near the trunk.
        let ranked = fitted.slots.enumerated()
            .sorted { left, right in
                if left.element.bearing != right.element.bearing { return left.element.bearing }
                let a = SplitMix64.mix(seed &+ UInt64(left.element.twig))
                let b = SplitMix64.mix(seed &+ UInt64(right.element.twig))
                return a == b ? left.offset < right.offset : a < b
            }
            .map(\.element)
        return TreeSkeleton(segments: fitted.segments, slots: ranked,
                            pitch: pitch(of: ranked))
    }

    /// Root of (crown area / slots): the side of the square each slot gets if
    /// the crown were shared out evenly — a stand-in for the mean distance to a
    /// neighbour that, unlike the real thing, is O(n).
    private static func pitch(of slots: [LeafSlot]) -> CGFloat {
        guard slots.count > 1 else { return 1 }
        let xs = slots.map(\.point.x), ys = slots.map(\.point.y)
        let spread = (xs.max() ?? 0) - (xs.min() ?? 0)
        let rise = (ys.max() ?? 0) - (ys.min() ?? 0)
        return max(1, sqrt(spread * rise / CGFloat(slots.count)))
    }

    // MARK: Growing

    private static func branch(
        from origin: CGPoint, angle: Double, length: Double, width: Double,
        depth: Int, limit: Int, side: Double, rng: inout SplitMix64,
        segments: inout [TreeSegment], sides: inout [Double]
    ) {
        let end = CGPoint(x: origin.x + CGFloat(cos(angle) * length),
                          y: origin.y + CGFloat(sin(angle) * length))
        let bow = length * 0.10 * (rng.next() < 0.5 ? -1 : 1)
        let mid = CGPoint(x: (origin.x + end.x) / 2, y: (origin.y + end.y) / 2)
        let control = CGPoint(x: mid.x + CGFloat(cos(angle + .pi / 2) * bow),
                              y: mid.y + CGFloat(sin(angle + .pi / 2) * bow))

        let endWidth = width * 0.82
        segments.append(TreeSegment(start: origin, control: control, end: end,
                                    startWidth: CGFloat(width), endWidth: CGFloat(endWidth),
                                    depth: depth))
        sides.append(side)
        guard depth < limit else { return }

        // Branches reach for the light a little more with every generation:
        // the parent's direction, bent a fraction of the way back toward up.
        let up = -Double.pi / 2
        let lifted = angle + (up - angle) * 0.045 * Double(depth)

        let dominant = lifted + rng.range(0.17, 0.27) * (rng.next() < 0.5 ? -1 : 1)
        let lateral = lifted + side * rng.range(0.70, 0.95)

        branch(from: end, angle: dominant, length: length * 0.80 * rng.range(0.95, 1.05),
               width: width * 0.82, depth: depth + 1, limit: limit, side: -side,
               rng: &rng, segments: &segments, sides: &sides)
        branch(from: end, angle: lateral, length: length * 0.72 * rng.range(0.91, 1.09),
               width: width * 0.57, depth: depth + 1, limit: limit, side: -side,
               rng: &rng, segments: &segments, sides: &sides)
        // A third limb off the trunk, sometimes: perfect two-way forking all the
        // way down reads as a diagram of a tree rather than as one.
        //
        // The FIRST fork only. Allowed at the second as well it fired up to
        // three times, each firing carrying a whole subtree, and two areas of
        // the same standing came out one twice as bushy as the other. One draw
        // per tree keeps the asymmetry without the spread.
        if depth == 0, rng.next() < 0.38 {
            branch(from: end, angle: lifted - side * rng.range(0.70, 0.95),
                   length: length * 0.6, width: width * 0.45, depth: depth + 1,
                   limit: limit, side: side,
                   rng: &rng, segments: &segments, sides: &sides)
        }
    }

    // MARK: Hanging

    /// What a segment's share of the canopy is worth, by distance from the tip.
    /// Weighted INWARD, because the tip ring holds twice as many segments as the
    /// one inside it: at equal weight per segment nearly all the foliage lands
    /// on the outermost ring, which monopodial branching keeps high, and the
    /// tree wears its leaves like a cap. Past the young wood the share falls
    /// away — those are the slots the headroom leaves empty.
    private static let share: [Double] = [1.6, 2.2, 1.2, 0.5]

    /// Shares `target` slots out over the wood and spaces them along it. Largest
    /// remainder, so the count is EXACTLY the target however the shares round.
    /// Spacing is even along whatever count a segment drew, so one twig carries
    /// a leaf at its middle or three down its length as the area grows, rather
    /// than the extras piling onto a fixed pair of points.
    private static func hang(on segments: [TreeSegment], sides: [Double],
                             limit: Int, target: Int) -> [LeafSlot] {
        let weights = segments.map { segment -> Double in
            let fromTip = limit - segment.depth
            return fromTip < share.count ? share[fromTip] : 0
        }
        let total = weights.reduce(0, +)
        guard total > 0, target > 0 else { return [] }

        let exact = weights.map { Double(target) * $0 / total }
        var counts = exact.map { Int($0) }
        let short = target - counts.reduce(0, +)
        if short > 0 {
            let byRemainder = exact.indices.sorted {
                let left = exact[$0] - Double(counts[$0]), right = exact[$1] - Double(counts[$1])
                return left == right ? $0 < $1 : left > right
            }
            for index in byRemainder.prefix(short) { counts[index] += 1 }
        }

        var slots: [LeafSlot] = []
        for (index, segment) in segments.enumerated() where counts[index] > 0 {
            let bearing = limit - segment.depth <= 1
            for step in 0..<counts[index] {
                let t = 0.30 + 0.58 * (Double(step) + 0.5) / Double(counts[index])
                slots.append(LeafSlot(
                    point: bezier(segment.start, segment.control, segment.end, t),
                    angle: tangent(segment.start, segment.control, segment.end, t),
                    side: step.isMultiple(of: 2) ? sides[index] : -sides[index],
                    twig: index, bearing: bearing))
            }
        }
        return slots
    }

    // MARK: Fitting

    private static func fit(segments: [TreeSegment], slots: [LeafSlot],
                            in rect: CGRect) -> (segments: [TreeSegment], slots: [LeafSlot]) {
        var minX = CGFloat.greatestFiniteMagnitude, maxX = -CGFloat.greatestFiniteMagnitude
        var minY = CGFloat.greatestFiniteMagnitude
        for segment in segments {
            for point in [segment.start, segment.control, segment.end] {
                minX = min(minX, point.x); maxX = max(maxX, point.x); minY = min(minY, point.y)
            }
        }
        let spread = max(maxX - minX, 0.001)
        let rise = max(-minY, 0.001)
        // why: the taller of the two constraints wins, so a wide crown is
        // narrowed to fit rather than clipped at the cell's edge.
        let scale = min(rect.height / rise, rect.width / spread)
        let foot = CGPoint(x: rect.midX - (minX + maxX) / 2 * scale, y: rect.maxY)

        func place(_ point: CGPoint) -> CGPoint {
            CGPoint(x: foot.x + point.x * scale, y: foot.y + point.y * scale)
        }
        return (
            segments.map {
                TreeSegment(start: place($0.start), control: place($0.control), end: place($0.end),
                            startWidth: $0.startWidth * scale, endWidth: $0.endWidth * scale,
                            depth: $0.depth)
            },
            slots.map { LeafSlot(point: place($0.point), angle: $0.angle,
                                 side: $0.side, twig: $0.twig, bearing: $0.bearing) }
        )
    }

    // MARK: Curve helpers

    static func bezier(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ t: Double) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: CGFloat(u * u) * a.x + CGFloat(2 * u * t) * b.x + CGFloat(t * t) * c.x,
                       y: CGFloat(u * u) * a.y + CGFloat(2 * u * t) * b.y + CGFloat(t * t) * c.y)
    }

    private static func tangent(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ t: Double) -> Double {
        let dx = CGFloat(2 * (1 - t)) * (b.x - a.x) + CGFloat(2 * t) * (c.x - b.x)
        let dy = CGFloat(2 * (1 - t)) * (b.y - a.y) + CGFloat(2 * t) * (c.y - b.y)
        return atan2(Double(dy), Double(dx))
    }
}
