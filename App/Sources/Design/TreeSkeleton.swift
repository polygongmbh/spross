import CoreGraphics
import Foundation

// MARK: - Tree skeleton
//
// The wood and the slots its marks hang on, grown together: a pure function of a seed and a
// mark count, taken from the area's FINISHED standing, so a round's tree only gains marks.
//
// The marks come first and the wood is whatever it takes to carry them (Weber & Penn 1995):
// a branch carrying n marks hands half of them to side branches along its length and the
// rest to the lead that continues from its end, until a branch carrying one mark is a leaf
// twig holding it at its tip. Width follows the pipe model, w ∝ √n, so r_parent² = Σ r_child².

/// One length of branch: a bowed center line that tapers along its length.
struct TreeSegment {
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint
    let startWidth: CGFloat
    let endWidth: CGFloat
    let depth: Int
    /// The segment this one grows from; nil for the trunk.
    let parent: Int?
}

/// Somewhere a mark hangs, and the way the mark faces.
struct LeafSlot {
    let point: CGPoint
    /// Outward from the twig it hangs off.
    let angle: Double
    /// The index of that twig in `segments`.
    let segment: Int
}

struct TreeSkeleton {
    let segments: [TreeSegment]
    /// One slot per mark, in rank order: the render hangs fruit first, then blossom, leaf, bud.
    let slots: [LeafSlot]
    /// The typical gap between neighboring marks — what a mark is sized against,
    /// so a crown of twenty words and one of sixty cover the same share of themselves.
    let pitch: CGFloat

    /// Grows one tree carrying `marks` marks, fitted into `rect` with its foot on the bottom edge.
    static func grown(seed: UInt64, marks: Int, in rect: CGRect) -> TreeSkeleton {
        guard marks > 0 else { return TreeSkeleton(segments: [], slots: [], pitch: 1) }
        // Grown in unit space pointing up, then measured and fitted:
        // the shape must not depend on the box it is asked to fill.
        var growth = Growth(seed: seed)
        growth.branch(marks, path: 1, from: .zero, heading: -.pi / 2, depth: 0, parent: nil, side: 1)
        return fit(segments: growth.segments, slots: ranked(growth.leaves, seed: seed), in: rect)
    }

    // MARK: Growing

    private struct Leaf {
        let slot: LeafSlot
        let path: UInt64
        /// Steeper than about 60° from horizontal.
        let steep: Bool
    }

    private struct Growth {
        let seed: UInt64
        var segments: [TreeSegment] = []
        var leaves: [Leaf] = []

        init(seed: UInt64) { self.seed = seed }

        // why: a branch may dip a little below horizontal, no further —
        // a drooping limb hangs its leaves under the crown.
        static func clamped(_ heading: Double) -> Double { min(0.25, max(-Double.pi - 0.25, heading)) }

        /// One branch carrying `n` marks, and everything beyond it.
        /// `path` names the branch from the trunk up, and seeds everything about it.
        mutating func branch(_ n: Int, path: UInt64, from origin: CGPoint, heading angle: Double,
                             depth: Int, parent: Int?, side: Double) {
            var rng = SplitMix64(seed: SplitMix64.mix(seed ^ SplitMix64.mix(path)))
            // why: a side branch starts out longer than a lead, so the crown spreads wider than it rises.
            let length = n == 1 ? 0.03 : 0.045 * (1 + 0.6 * log(Double(n))) * (path & 3 >= 2 ? 1.6 : 1)
            let width = 0.0075 * Double(n).squareRoot()
            let end = CGPoint(x: origin.x + CGFloat(cos(angle) * length),
                              y: origin.y + CGFloat(sin(angle) * length))
            // why: every branch leaves its parent wide and arches back toward it.
            let bow = length * rng.range(0.04, 0.10) * side
            let control = CGPoint(x: (origin.x + end.x) / 2 + CGFloat(cos(angle + .pi / 2) * bow),
                                  y: (origin.y + end.y) / 2 + CGFloat(sin(angle + .pi / 2) * bow))
            // The trunk flares where it meets the ground.
            let segment = TreeSegment(start: origin, control: control, end: end,
                                      startWidth: CGFloat(width * (depth == 0 ? 1.3 : 1)),
                                      endWidth: CGFloat(width * 0.8), depth: depth, parent: parent)
            segments.append(segment)
            let index = segments.count - 1

            // why: measured from this branch's own heading, so a left-leaning tangent never wraps past ±π.
            func along(_ t: Double) -> Double { angle + remainder(segment.heading(at: t) - angle, 2 * .pi) }
            guard n > 1 else {
                // why: a leaf follows its twig, splayed to one side, and never points below horizontal.
                let slot = LeafSlot(point: end, angle: min(-0.3, max(-Double.pi + 0.3, along(1) + side * 0.9)),
                                    segment: index)
                let dx = abs(end.x - origin.x), dy = abs(end.y - origin.y)
                leaves.append(Leaf(slot: slot, path: path, steep: dx < 0.6 * dy))
                return
            }
            // why: limbs sag under their weight, the more level and the later-born the further.
            let sag = 0.02 * Double(depth + 1) * cos(angle)
            let r = n / 2
            let sides = r == 1 ? [(r, 0.7)] : [((r + 1) / 2, 0.55), (r / 2, 0.8)]
            let headings = sides.indices.map { k in
                Self.clamped(along(sides[k].1) + (k == 0 ? side : -side) * rng.range(1.05, 1.40) + sag)
            }
            // The lead bends a little away from the first side branch.
            var lead = Self.clamped(angle - side * rng.range(0.05, 0.20) + sag)
            // why: a side branch held up by the clamp would run along the lead, so the lead gives way.
            if headings.contains(where: { abs($0 - lead) < 0.45 }) {
                lead = headings.count == 2 ? (headings[0] + headings[1]) / 2 : Self.clamped(headings[0] - side * 0.45)
            }
            for (k, (count, t)) in sides.enumerated() {
                branch(count, path: path &* 4 &+ UInt64(2 + k), from: segment.point(at: t), heading: headings[k],
                       depth: depth + 1, parent: index, side: k == 0 ? side : -side)
            }
            branch(n - r, path: path &* 4 &+ 1, from: end, heading: lead,
                   depth: depth + 1, parent: index, side: -side)
        }
    }

    /// Leaf twigs in a seeded order, the steep ones last so fruit and blossom hang on level wood.
    private static func ranked(_ leaves: [Leaf], seed: UInt64) -> [LeafSlot] {
        let shuffled = leaves.sorted { SplitMix64.mix(seed ^ $0.path) < SplitMix64.mix(seed ^ $1.path) }
        return (shuffled.filter { !$0.steep } + shuffled.filter(\.steep)).map(\.slot)
    }

    // MARK: Fitting

    private static func fit(segments: [TreeSegment], slots: [LeafSlot], in rect: CGRect) -> TreeSkeleton {
        var minX = CGFloat.greatestFiniteMagnitude, maxX = -CGFloat.greatestFiniteMagnitude
        var minY = CGFloat.greatestFiniteMagnitude
        for point in segments.flatMap({ [$0.start, $0.control, $0.end] }) + slots.map(\.point) {
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
        let placed = slots.map { LeafSlot(point: place($0.point), angle: $0.angle, segment: $0.segment) }
        return TreeSkeleton(
            segments: segments.map {
                TreeSegment(start: place($0.start), control: place($0.control), end: place($0.end),
                            startWidth: $0.startWidth * scale, endWidth: $0.endWidth * scale,
                            depth: $0.depth, parent: $0.parent)
            },
            slots: placed,
            pitch: pitch(of: placed, height: rise * scale))
    }

    /// Root of (crown area / marks): the side of the square each mark gets if the crown were
    /// shared out evenly — counted as at least eight, so a handful of words stay small.
    private static func pitch(of slots: [LeafSlot], height: CGFloat) -> CGFloat {
        let xs = slots.map(\.point.x), ys = slots.map(\.point.y)
        let floor = max(1, height / 4)
        let spread = max(floor, (xs.max() ?? 0) - (xs.min() ?? 0))
        let rise = max(floor, (ys.max() ?? 0) - (ys.min() ?? 0))
        return sqrt(spread * rise / CGFloat(max(8, slots.count)))
    }
}

extension TreeSegment {
    /// The point `t` of the way along the bowed center line.
    func point(at t: Double) -> CGPoint {
        let t = CGFloat(t), u = 1 - t
        return CGPoint(x: u * u * start.x + 2 * u * t * control.x + t * t * end.x,
                       y: u * u * start.y + 2 * u * t * control.y + t * t * end.y)
    }

    /// The direction the center line runs `t` of the way along.
    func heading(at t: Double) -> Double {
        let t = CGFloat(t), u = 1 - t
        return atan2(Double(u * (control.y - start.y) + t * (end.y - control.y)),
                     Double(u * (control.x - start.x) + t * (end.x - control.x)))
    }
}
