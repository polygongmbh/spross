import SwiftUI

// MARK: - Drawing one area's tree
//
// The tree is ONE organism its whole life. It is never swapped for another:
// a seedling thickens into a trunk, the canopy fills with the words that have
// landed, and blossom and fruit appear ON that canopy rather than replacing it.
//
// The canopy is NOT a shape. It is wherever the twigs ended up, and every mark
// hangs at a twig's tip (`TreeSkeleton`). A canopy region with
// marks sampled inside it is what makes a procedural tree read as a child's
// drawing: the leaves float, the outline closes into a circle, and there are no
// gaps to see sky through.
//
// What hangs where (`Canopy`), in rank order:
//   fruit    — a word held for months
//   blossom  — a word that has matured
//   leaf     — a word that has landed
//   bud      — a word the learner has met, on its way in
// Each word the learner has MET is exactly one mark, from its first answer on,
// so every round has something on the tree to point at.

enum TreeShapes {

    /// Draws `mark` — always the FINISHED tree, whatever moment is being drawn.
    ///
    /// A transition never changes the counts: the skeleton and the marks it
    /// carries are the same at every moment, and `arriving` says only how far
    /// each of the round's own marks has come.
    static func draw(_ context: inout GraphicsContext, _ mark: TreeMark,
                     arriving: TreeArrival = .settled) {
        let shown = mark.canopy
        ground(&context, mark)

        // why: an area nobody has opened stands as a faded seedling on its own
        // patch of ground — a place to go rather than a chore not done.
        if shown.isBare {
            return seedling(&context, mark,
                            height: max(mark.height, TreeMark.minHeight),
                            color: Theme.colors.success.opacity(0.45))
        }
        guard shown.count > 0 else {
            return seedling(&context, mark, height: mark.height, color: Theme.colors.success)
        }

        let skeleton = mark.skeleton
        branches(&context, skeleton, marks: shown.count)
        foliage(&context, skeleton, mark, shown, arriving)
        fallen(&context, mark, shown)
        if shown.tendedToday { freshEarth(&context, mark) }
    }

    // MARK: Ground

    /// What the tree stands on: a soft shadow under the trunk, never a line.
    private static func ground(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let width = max(9, mark.height * 0.42)
        let shadow = CGRect(x: mark.foot.x - width / 2, y: mark.baseline - 1.6,
                            width: width, height: 3.2)
        context.fill(Path(ellipseIn: shadow), with: .color(Theme.colors.separator.opacity(0.55)))
    }

    /// Answered today — a short line of fresh earth at the foot, on the GROUND:
    /// it says this area was tended today, not that anything in it grew a stage.
    private static func freshEarth(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let half = max(6, mark.height * 0.14)
        var path = Path()
        path.move(to: CGPoint(x: mark.foot.x - half, y: mark.baseline + 3.5))
        path.addLine(to: CGPoint(x: mark.foot.x + half, y: mark.baseline + 3.5))
        context.stroke(path, with: .color(Theme.colors.accent),
                       style: StrokeStyle(lineWidth: max(1.6, mark.height * 0.03), lineCap: .round))
    }

    /// Nothing met yet: a stem and two leaflets. Packing a whole area puts ONE of
    /// these on the plot; an untouched area gets the same seedling faded.
    private static func seedling(_ context: inout GraphicsContext, _ mark: TreeMark,
                                 height: CGFloat, color: Color) {
        let top = CGPoint(x: mark.foot.x, y: mark.baseline - height)
        var stem = Path()
        stem.move(to: mark.foot)
        stem.addQuadCurve(to: top, control: CGPoint(x: mark.foot.x + height * 0.08,
                                                    y: mark.baseline - height * 0.5))
        context.stroke(stem, with: .color(color),
                       style: StrokeStyle(lineWidth: max(1.4, height * 0.055), lineCap: .round))
        let leafSize = max(4, height * 0.34)
        context.fill(leafPath(at: top, size: leafSize, angle: -0.7), with: .color(color))
        context.fill(leafPath(at: top, size: leafSize * 0.85, angle: .pi + 0.7), with: .color(color))
    }

    // MARK: The wood

    /// Every branch as one filled path, tapering as it goes, with its joints rounded
    /// so a fork reads as grown rather than glued.
    /// The trunk carries a shaded side — light from the upper left — which gives the wood a body.
    /// Only wood carrying one of the first `marks` shows, so no twig stands bare.
    private static func branches(_ context: inout GraphicsContext, _ skeleton: TreeSkeleton, marks: Int) {
        var carrying = Set<Int>()
        for slot in skeleton.slots.prefix(marks) {
            var next: Int? = slot.segment
            while let index = next, carrying.insert(index).inserted { next = skeleton.segments[index].parent }
        }
        var wood = Path()
        var joints = Path()
        var shade = Path()
        var twigs = Path()
        for (index, segment) in skeleton.segments.enumerated() where carrying.contains(index) {
            let width = max(segment.startWidth, segment.endWidth)
            if width < 0.9 {
                // Sub-point twigs: a filled taper collapses, so these are hairlines.
                twigs.move(to: segment.start)
                twigs.addQuadCurve(to: segment.end, control: segment.control)
                continue
            }
            wood.addPath(taper(segment, from: -1, to: 1))
            joints.addPath(circle(segment.end, segment.endWidth / 2))
            if segment.depth == 0, width >= 2.4 { shade.addPath(taper(segment, from: 0.3, to: 1)) }
        }
        // why: the joints fill apart from the wood — a circle wound against a
        // taper cancels it under the nonzero rule and cuts a notch in the fork.
        context.fill(wood, with: .color(Theme.colors.borderStrong))
        context.fill(joints, with: .color(Theme.colors.borderStrong))
        context.fill(shade, with: .color(Theme.colors.textPrimary.opacity(0.08)))
        context.stroke(twigs, with: .color(Theme.colors.borderStrong),
                       style: StrokeStyle(lineWidth: 0.7, lineCap: .round))
    }

    /// The band of a segment between two edges, `from`…`to` across its width
    /// (-1 one edge, 1 the other): both edges bow with the center line.
    private static func taper(_ segment: TreeSegment, from: CGFloat, to: CGFloat) -> Path {
        let angle = atan2(segment.end.y - segment.start.y, segment.end.x - segment.start.x)
        let normal = CGVector(dx: CGFloat(cos(Double(angle) + .pi / 2)),
                              dy: CGFloat(sin(Double(angle) + .pi / 2)))
        func offset(_ point: CGPoint, _ width: CGFloat, _ sign: CGFloat) -> CGPoint {
            CGPoint(x: point.x + normal.dx * width / 2 * sign,
                    y: point.y + normal.dy * width / 2 * sign)
        }
        let middle = (segment.startWidth + segment.endWidth) / 2
        var path = Path()
        path.move(to: offset(segment.start, segment.startWidth, to))
        path.addQuadCurve(to: offset(segment.end, segment.endWidth, to),
                          control: offset(segment.control, middle, to))
        path.addLine(to: offset(segment.end, segment.endWidth, from))
        path.addQuadCurve(to: offset(segment.start, segment.startWidth, from),
                          control: offset(segment.control, middle, from))
        path.closeSubpath()
        return path
    }

    // MARK: The canopy

    /// The marks along the twigs: buds and fruit under the leaves, which take four tones
    /// lit from above, then blossom on top.
    private static func foliage(_ context: inout GraphicsContext, _ skeleton: TreeSkeleton,
                                _ mark: TreeMark, _ shown: Canopy, _ arriving: TreeArrival) {
        let base = CanopyMark.base(pitch: skeleton.pitch)
        let hanging = Array(skeleton.slots.prefix(shown.count))
        let top = hanging.map(\.point.y).min() ?? 0
        let depth = max((hanging.map(\.point.y).max() ?? 0) - top, 1)

        var tones = [Path(), Path(), Path(), Path()]
        var buds = Path(), fruit = Path(), laterals = Path(), kings = Path(), eyes = Path()
        for (rank, slot) in hanging.enumerated() {
            // A mark's SIZE is its own word's standing; only its lean is hashed.
            let grain = noise("\(mark.area)-\(rank)", 41)
            let size = CanopyMark.size(base: base, reach: shown.reach(rank)) * arriving.scale(rank)
            guard size > 0.2 else { continue }
            let angle = CanopyMark.lean(slot, grain: grain)
            if rank < shown.fruit {
                fruit.addPath(Self.fruit(at: slot.point, size: size))
            } else if rank < shown.fruit + shown.blossoms {
                blossom(at: slot.point, size: size, angle: angle,
                        laterals: &laterals, kings: &kings, eyes: &eyes)
            } else if rank < shown.count - shown.buds {
                // why: lit from above — the crown's upper leaves take the light
                // tones, its lower and inner ones the deep, with a hashed nudge
                // so the tones fall in patches rather than bands.
                let height = Double((slot.point.y - top) / depth)
                let tone = min(3, max(0, Int((height * 0.75 + grain * 0.55) * 3.2 - 0.2)))
                tones[tone].addPath(sprig(at: slot.point, size: size * CanopyMark.leafStretch,
                                          angle: angle))
            } else {
                buds.addPath(circle(slot.point, size * CanopyMark.budRadius))
            }
        }
        // Ochre, not green: a bud is a scale of wood, the word has not leafed out yet.
        context.fill(buds, with: .color(Theme.colors.amber.opacity(0.8)))
        context.fill(fruit, with: .color(Theme.colors.fruit))
        let leafColors: [Color] = [Theme.colors.das.opacity(0.92), Theme.colors.success,
                                   Theme.colors.success.opacity(0.84), Theme.colors.success.opacity(0.68)]
        for (index, tone) in tones.enumerated() { context.fill(tone, with: .color(leafColors[index])) }
        context.fill(laterals, with: .color(Theme.colors.blossom.opacity(0.85)))
        context.fill(kings, with: .color(Theme.colors.blossom))
        context.fill(eyes, with: .color(Theme.colors.fruit.opacity(0.4)))
    }

    /// Words that lapsed: leaves on the ground beside the trunk. The tree never
    /// shrinks for them — a routine miss is not a smaller tree.
    private static func fallen(_ context: inout GraphicsContext, _ mark: TreeMark,
                               _ shown: Canopy) {
        guard shown.fallen > 0 else { return }
        let clear = max(7, mark.height * 0.2)
        let size = max(3, mark.height * 0.055)
        for index in 0..<min(shown.fallen, 3) {
            let side: CGFloat = index.isMultiple(of: 2) ? -1 : 1
            let spread = clear + CGFloat(noise("\(mark.area)-f\(index)", 13)) * clear * 0.5
            let at = CGPoint(x: mark.foot.x + side * spread, y: mark.baseline + 0.5)
            context.fill(leafPath(at: at, size: size, angle: side > 0 ? 0.2 : .pi - 0.2),
                         with: .color(Theme.colors.amber.opacity(0.85)))
        }
    }

    /// Stable 0..<1 noise for one (id, property) —
    /// the SplitMix64 finish `ConfettiView` uses, over an FNV-1a fold of the id.
    static func noise(_ id: String, _ salt: Int) -> Double {
        var rng = SplitMix64(seed: SplitMix64(id).seed &+ UInt64(bitPattern: Int64(salt)))
        return rng.next()
    }
}
