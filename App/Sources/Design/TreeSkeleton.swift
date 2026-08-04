import CoreGraphics
import Foundation

// MARK: - Tree skeleton
//
// The branch structure, and the slots leaves hang on. A pure function of a
// seed and a size: no counts reach it, which is what makes it identical
// before and after a round so only the leaves move.
//
// The rules are the standard ones for procedural trees, at the values that
// read at icon size (Weber & Penn 1995 §5 is explicit that at 5–20% of screen
// height the BRANCH STRUCTURE has to be right and leaf detail does not):
//
//   · Monopodial branching — one child continues the parent's line and keeps
//     most of its length, the other departs sharply and is much shorter.
//     Equal-angle equal-length splits are what make a drawn tree read as
//     broccoli, and it is the loudest tell after uniform stroke width.
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
    /// Which twig this slot belongs to. Slots are ranked twig by twig, so a
    /// half-full canopy is clumps of foliage with bare twigs and sky between
    /// them — the thing that reads as a tree — rather than an even thin haze
    /// over the whole crown.
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
    /// against. Sized against the tree's HEIGHT instead, a mark was the same
    /// fraction of every tree; but a three-generation crown holds half the slots
    /// of a four-generation one over the same spread, so it drew the same small
    /// marks with twice the sky between them and read as the thin tree whatever
    /// the learner had done to it.
    let pitch: CGFloat

    /// Structural generations. Four is where a deciduous tree stops gaining
    /// anything at this size; past it the segments are under a point.
    static let maxDepth = 4

    /// How many generations an area of this standing has earned.
    ///
    /// A young area used to get a full four-generation crown squeezed into a
    /// dozen points, which read as a scribble rather than as a small tree: the
    /// branch COUNT said "old" while the size said "new". A sapling forks twice,
    /// and a tree earns its next generation by growing.
    ///
    /// Taken from the area's standing rather than from the size it is drawn at,
    /// so the hero and the forest show one tree and a transition cannot grow a
    /// generation halfway through.
    static func generations(for tree: AreaTree) -> Int {
        // why: from the number of WORDS the canopy has to hold, not from the
        // tree's height. Height comes from aggregate stability, which climbs
        // while a word is merely getting stronger — so an area could grow tall
        // enough to earn another generation of twigs without gaining a single
        // leaf to hang on them, which is what left the thin trees twiggy. Tied
        // to the canopy, a tree fills up, earns its next generation, and fills
        // that.
        switch tree.canopyCount {
        case ..<12: return 2
        case ..<30: return 3
        default: return maxDepth
        }
    }

    /// Grows one tree, fitted into `rect` with its foot on the bottom edge.
    /// `seed` and `depth` alone decide the shape, so one area's tree is the same
    /// tree wherever it is drawn and however many words hang on it.
    static func grown(seed: UInt64, depth: Int, in rect: CGRect) -> TreeSkeleton {
        var rng = SplitMix64(seed: seed)
        var segments: [TreeSegment] = []
        var slots: [LeafSlot] = []

        // Grown in unit space pointing up, then measured and fitted: the shape
        // must not depend on the box it is asked to fill.
        branch(from: .zero, angle: -.pi / 2, length: 0.30, width: 0.058,
               depth: 0, limit: depth, side: 1, rng: &rng, segments: &segments, slots: &slots)

        let fitted = fit(segments: segments, slots: slots, in: rect)
        // Outer growth first, and within it twig by twig with the twigs
        // themselves shuffled: a canopy fills in clumps with sky between them,
        // and filling in traversal order would leaf one side at a time.
        // Outer first earns its place twice: it is where fruit and blossom
        // belong and they take the lowest ranks, and a part-full canopy then
        // leaves the limbs near the trunk bare, where a real tree's gaps are.
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
        segments: inout [TreeSegment], slots: inout [LeafSlot]
    ) {
        let twig = segments.count
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

        // Where leaves may hang, PER SEGMENT — weighted to the inner
        // generations, because segment count roughly doubles per generation and
        // loading the terminal twigs put nearly all foliage on the outermost
        // ring, which monopodial branching keeps high. The tree wore its leaves
        // like a cap.
        //
        // The TOTAL matters more than the weighting, though. A tree's slot count
        // grows with its generations while its words grow with the learner, and
        // the two used to grow at the same rate — a mature area filled the same
        // ~37% of its slots as a young one filled of its own, so every tree in
        // the forest was equally sparse and maturity showed only as size. These
        // totals are small enough that a well-worked area comes close to filling
        // its tree, which is what "well worked" should look like. The inner
        // generations keep their slots even though nothing heavy may hang there:
        // ranked last, they are what a nearly-full crown reaches for.
        let carries: [Double]
        switch limit - depth {
        case 0: carries = [0.66]
        case 1: carries = [0.38, 0.76]
        case 2: carries = [0.40, 0.78]
        case 3: carries = [0.62]
        default: carries = []
        }
        // The last two generations are the young wood: thin enough that anything
        // may hang there, and the only place fruit and blossom are allowed.
        let bearing = limit - depth <= 1
        for (index, t) in carries.enumerated() {
            let point = bezier(origin, control, end, t)
            slots.append(LeafSlot(point: point, angle: tangent(origin, control, end, t),
                                  side: index.isMultiple(of: 2) ? side : -side, twig: twig,
                                  bearing: bearing))
        }
        guard depth < limit else { return }

        // Branches reach for the light a little more with every generation:
        // the parent's direction, bent a fraction of the way back toward up.
        let up = -Double.pi / 2
        let lifted = angle + (up - angle) * 0.045 * Double(depth)

        let dominant = lifted + rng.range(0.17, 0.27) * (rng.next() < 0.5 ? -1 : 1)
        let lateral = lifted + side * rng.range(0.70, 0.95)

        branch(from: end, angle: dominant, length: length * 0.80 * rng.range(0.95, 1.05),
               width: width * 0.82, depth: depth + 1, limit: limit, side: -side,
               rng: &rng, segments: &segments, slots: &slots)
        branch(from: end, angle: lateral, length: length * 0.72 * rng.range(0.91, 1.09),
               width: width * 0.57, depth: depth + 1, limit: limit, side: -side,
               rng: &rng, segments: &segments, slots: &slots)
        // A third limb off the trunk, sometimes: perfect two-way forking all the
        // way down reads as a diagram of a tree rather than as one.
        //
        // The FIRST fork only. Allowed at the second as well it fired up to
        // three times, each firing carrying a whole subtree, and a
        // four-generation crown came out anywhere between 42 and 83 slots — two
        // areas with the same forty words drawing at 95% and 48% full, the
        // sparse one sparse only because it had drawn a bushier skeleton. One
        // draw per tree keeps the asymmetry and holds the spread to 42–63.
        if depth == 0, rng.next() < 0.38 {
            branch(from: end, angle: lifted - side * rng.range(0.70, 0.95),
                   length: length * 0.6, width: width * 0.45, depth: depth + 1,
                   limit: limit, side: side,
                   rng: &rng, segments: &segments, slots: &slots)
        }
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
