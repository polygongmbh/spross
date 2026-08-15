import SwiftUI

// MARK: - Drawing one area's tree
//
// The tree is ONE organism its whole life. It is never swapped for another:
// a seedling thickens into a trunk, the canopy fills with the words that have
// landed, and blossom and fruit appear ON that canopy rather than replacing it.
// So there is no rung at which the picture starts over, and no top rung at
// which it stops — a tree can always carry more.
//
// The canopy is NOT a shape. It is wherever the twigs ended up, and every mark
// hangs on one of them (`TreeSkeleton`). Drawing a canopy region and sampling
// marks inside it is what makes a procedural tree read as a child's drawing:
// the leaves float, the outline closes into a circle, and there are no gaps to
// see sky through.
//
// What hangs where, outermost slot first:
//   fruit    — a word a month or more out from its next sight
//   blossom  — a word that has landed
//   leaf     — a word that has settled
//   bud      — a word the learner has met, on its way in
// Each word the learner has MET is exactly one mark, from its first answer on,
// so every round has something on the tree to point at. A word merely packed
// has none: it is why the tree is as tall as it is, and nothing more.

enum TreeShapes {

    /// Draws `mark` — always the FINISHED tree, whatever moment is being drawn.
    ///
    /// A transition never changes the counts: the skeleton and the marks it
    /// carries are the same at every moment, and `arriving` says only how far
    /// each of the round's own marks has come. That is what lets a new leaf
    /// arrive, and a leaf turn into a blossom in place, while nothing else moves.
    static func draw(_ context: inout GraphicsContext, _ mark: TreeMark,
                     arriving: TreeArrival = .settled) {
        let shown = mark.tree
        // why: an area nobody has opened draws NOTHING — not even ground. A mark
        // on every untouched area turns a catalog the learner did not choose
        // into a list of things they have not done, and its dimmed emoji already
        // says the place exists.
        guard !shown.isBare else { return }
        ground(&context, mark)

        guard shown.canopyCount > 0 else { return seedling(&context, mark) }

        let skeleton = mark.skeleton
        branches(&context, skeleton, mark)
        foliage(&context, skeleton, mark, shown, arriving)
        fallen(&context, mark, shown)
        if shown.tendedToday { freshEarth(&context, mark) }
    }

    // MARK: Ground

    /// What the tree stands on: a soft shadow under the trunk.
    ///
    /// Deliberately NOT a line — a stroke under every tree read as a shelf, with
    /// the area's emoji beneath it looking like a label stuck to the furniture.
    private static func ground(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let width = max(9, mark.height * 0.42)
        let shadow = CGRect(x: mark.foot.x - width / 2, y: mark.baseline - 1.6,
                            width: width, height: 3.2)
        context.fill(Path(ellipseIn: shadow), with: .color(.dlSeparator.opacity(0.55)))
    }

    /// Answered today — a short line of fresh earth at the foot. A mark on the
    /// GROUND, never on the tree: it says this area was tended today, not that
    /// anything in it grew a stage.
    private static func freshEarth(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let half = max(6, mark.height * 0.14)
        var path = Path()
        path.move(to: CGPoint(x: mark.foot.x - half, y: mark.baseline + 3.5))
        path.addLine(to: CGPoint(x: mark.foot.x + half, y: mark.baseline + 3.5))
        context.stroke(path, with: .color(.dlAccent),
                       style: StrokeStyle(lineWidth: max(1.6, mark.height * 0.03), lineCap: .round))
    }

    /// Nothing has settled here yet: a stem and two leaflets. Packing a whole
    /// area puts ONE of these on the plot — forty words packed is still one
    /// intention, and drawing it as forty objects was a spilled bag of seeds.
    private static func seedling(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let top = CGPoint(x: mark.foot.x, y: mark.baseline - mark.height)
        var stem = Path()
        stem.move(to: mark.foot)
        stem.addLine(to: top)
        context.stroke(stem, with: .color(.dlSuccess),
                       style: StrokeStyle(lineWidth: max(1.4, mark.height * 0.055), lineCap: .round))
        let leafSize = max(4, mark.height * 0.34)
        leaf(&context, at: top, size: leafSize, angle: -0.7, color: .dlSuccess)
        leaf(&context, at: top, size: leafSize, angle: .pi + 0.7, color: .dlSuccess)
    }

    // MARK: The tree

    /// Every branch as one filled path. Filled, not stroked, because a stroke
    /// has one width for its whole length and uniform width is the loudest tell
    /// that a machine drew the tree — a limb has to narrow as it goes.
    private static func branches(_ context: inout GraphicsContext,
                                 _ skeleton: TreeSkeleton, _ mark: TreeMark) {
        var trunkAndLimbs = Path()
        var twigs = Path()
        for segment in skeleton.segments {
            let width = max(segment.startWidth, segment.endWidth)
            if width < 0.9 {
                // Sub-point twigs: a filled taper collapses, so these are hairlines.
                twigs.move(to: segment.start)
                twigs.addQuadCurve(to: segment.end, control: segment.control)
            } else {
                trunkAndLimbs.addPath(taper(segment))
            }
        }
        context.fill(trunkAndLimbs, with: .color(.dlBorderStrong))
        context.stroke(twigs, with: .color(.dlBorderStrong),
                       style: StrokeStyle(lineWidth: 0.7, lineCap: .round))
    }

    /// One segment as a closed shape: both edges bow with the center line, and
    /// the far end is narrower than the near one.
    private static func taper(_ segment: TreeSegment) -> Path {
        let angle = atan2(segment.end.y - segment.start.y, segment.end.x - segment.start.x)
        let normal = CGVector(dx: CGFloat(cos(Double(angle) + .pi / 2)),
                              dy: CGFloat(sin(Double(angle) + .pi / 2)))
        func offset(_ point: CGPoint, _ width: CGFloat, _ sign: CGFloat) -> CGPoint {
            CGPoint(x: point.x + normal.dx * width / 2 * sign,
                    y: point.y + normal.dy * width / 2 * sign)
        }
        var path = Path()
        path.move(to: offset(segment.start, segment.startWidth, 1))
        path.addQuadCurve(to: offset(segment.end, segment.endWidth, 1),
                          control: offset(segment.control, segment.endWidth, 1))
        path.addLine(to: offset(segment.end, segment.endWidth, -1))
        path.addQuadCurve(to: offset(segment.start, segment.startWidth, -1),
                          control: offset(segment.control, segment.endWidth, -1))
        path.closeSubpath()
        return path
    }

    /// The marks, on the slots the twigs offer. Blossom and fruit take the
    /// first slots, which the skeleton shuffled twig by twig, so they scatter
    /// through the crown rather than ringing it.
    private static func foliage(_ context: inout GraphicsContext, _ skeleton: TreeSkeleton,
                                _ mark: TreeMark, _ shown: AreaTree, _ arriving: TreeArrival) {
        // why: a mark is sized against the crown it has to help fill, not
        // against the tree's height — pitch is the crown shared out over the
        // words hanging in it, so thirty marks on a middling tree close into
        // foliage exactly as sixty do on a large one.
        //
        // No term for how full the canopy is any more. The pool is cut to the
        // words, so that fraction is now the same on every tree — and being the
        // one input that moved during the summary's animation, it quietly swelled
        // every mark on the tree while the round's words were still arriving.
        let base = CanopyMark.base(pitch: skeleton.pitch)
        var tones = [Path(), Path(), Path()]

        for (rank, slot) in skeleton.slots.prefix(shown.canopyCount).enumerated() {
            // A mark's SIZE is its own word's standing; only its lean is
            // hashed. A canopy of identical stamps is the other way to look
            // machine-made, and a canopy whose variation means something is
            // better than one whose variation is noise.
            let grain = ForestLayout.noise("\(mark.tree.id)-\(rank)", 41)
            let reach = rank < shown.reaches.count ? shown.reaches[rank] : 0.4
            // The round's own marks arrive; the rest of the crown is settled.
            let size = CanopyMark.size(base: base, reach: reach) * arriving.scale(rank)
            guard size > 0.2 else { continue }
            let angle = CanopyMark.lean(slot, grain: grain)

            if rank < shown.fruit {
                fruit(&context, at: slot.point, size: size)
            } else if rank < shown.fruit + shown.blossoms {
                blossom(&context, at: slot.point, size: size, angle: angle)
            } else if rank < shown.canopyCount - shown.buds {
                tones[Int(grain * 3) % 3].addPath(
                    leafPath(at: slot.point, size: size * CanopyMark.leafStretch, angle: angle))
            } else {
                bud(&context, at: slot.point, size: size)
            }
        }
        // Three tones of the one green rather than two: at a canopy's worth of
        // marks the extra step is the difference between depth and a flat mass.
        for (index, opacity) in [0.74, 0.88, 1.0].enumerated() {
            context.fill(tones[index], with: .color(.dlSuccess.opacity(opacity)))
        }
    }

    /// Words that lapsed: leaves on the ground beside the trunk. The tree never
    /// shrinks for them — the engine expects roughly one review in five to miss,
    /// and a picture that shrank the tree for a routine Tuesday would be lying
    /// about what a lapse costs.
    private static func fallen(_ context: inout GraphicsContext, _ mark: TreeMark,
                               _ shown: AreaTree) {
        guard shown.fallen > 0 else { return }
        let clear = max(7, mark.height * 0.2)
        let size = max(3, mark.height * 0.055)
        for index in 0..<min(shown.fallen, 3) {
            let side: CGFloat = index.isMultiple(of: 2) ? -1 : 1
            let spread = clear + CGFloat(ForestLayout.noise("\(mark.tree.id)-f\(index)", 13)) * clear * 0.5
            let at = CGPoint(x: mark.foot.x + side * spread, y: mark.baseline + 0.5)
            leaf(&context, at: at, size: size, angle: side > 0 ? 0.2 : .pi - 0.2,
                 color: .dlAmber.opacity(0.85))
        }
    }

    // MARK: Marks

    private static func leafPath(at point: CGPoint, size: CGFloat, angle: Double) -> Path {
        let waist = size * CanopyMark.leafWaist
        let leaf = Path(ellipseIn: CGRect(x: 0, y: -waist, width: size, height: waist * 2))
        return leaf.applying(
            CGAffineTransform(translationX: point.x, y: point.y)
                .rotated(by: CGFloat(angle))
        )
    }

    private static func leaf(_ context: inout GraphicsContext, at point: CGPoint,
                             size: CGFloat, angle: Double, color: Color) {
        context.fill(leafPath(at: point, size: size, angle: angle), with: .color(color))
    }

    /// A word the learner has met and not yet settled.
    ///
    /// The smallest mark on the tree, and round where a leaf is long — the two
    /// ways it can be told apart before its color is read. It takes the ranks
    /// nearest the trunk, so what a round sows tucks in among the limbs while
    /// the rim keeps carrying what has grown; a canopy that put its buds at the
    /// edges would be a tree shrinking every time the learner met a word.
    private static func bud(_ context: inout GraphicsContext, at point: CGPoint, size: CGFloat) {
        // Ochre, not green: a bud is a scale of wood, and the whole point of
        // this mark is that the word has not leafed out yet. Told from fruit —
        // the other warm mark — by being a third its size and flat where fruit
        // carries a stalk and a highlight.
        context.fill(circle(point, size * CanopyMark.budRadius),
                     with: .color(.dlAmber.opacity(0.8)))
    }

    /// A word the learner will not see again for months — the furthest thing on
    /// the tree.
    ///
    /// Drawn HEAVIER than a blossom, not lighter. A word promotes from blossom
    /// to fruit, and while it was a small dot that promotion read as a flower
    /// being taken away: the ladder's visual weight was inverted at the top,
    /// so the one unambiguously good thing looked like a loss.
    private static func fruit(_ context: inout GraphicsContext, at point: CGPoint, size: CGFloat) {
        var stalk = Path()
        stalk.move(to: CGPoint(x: point.x, y: point.y - size * 0.62))
        stalk.addLine(to: CGPoint(x: point.x, y: point.y - size * 0.26))
        context.stroke(stalk, with: .color(.dlBorderStrong),
                       style: StrokeStyle(lineWidth: max(0.6, size * 0.1), lineCap: .round))
        context.fill(circle(point, size * 0.56), with: .color(.dlAccent))
        // A highlight: at this size it is the difference between fruit and a dot.
        context.fill(circle(CGPoint(x: point.x - size * 0.17, y: point.y - size * 0.17),
                            size * 0.15),
                     with: .color(.dlSurface.opacity(0.65)))
    }

    /// A word that has landed.
    ///
    /// Two crossed ellipses and an eye, not five separate petals: a rosette is
    /// six shapes and holds its own against a leaf, which is right on a tree
    /// carrying four of them and far too loud on one carrying forty. This keeps
    /// the four-lobed silhouette — so it is still told from a leaf's single
    /// ellipse and a fruit's disc without relying on hue — at a third of the ink.
    private static func blossom(_ context: inout GraphicsContext, at point: CGPoint,
                                size: CGFloat, angle: Double) {
        let span = size * 1.12
        var lobes = Path()
        for turn in [0.0, Double.pi / 2] {
            let petal = Path(ellipseIn: CGRect(x: -span / 2, y: -span * 0.22,
                                               width: span, height: span * 0.44))
            lobes.addPath(petal.applying(
                CGAffineTransform(translationX: point.x, y: point.y)
                    .rotated(by: CGFloat(angle + turn))
            ))
        }
        context.fill(lobes, with: .color(.dlDie))
        context.fill(circle(point, span * 0.17), with: .color(.dlAmber))
    }

    private static func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                               width: radius * 2, height: radius * 2))
    }
}

// MARK: - Mark geometry
//
// How big a mark is drawn and how far it reaches past the slot it hangs on —
// pulled out of the drawing because the FIT needs the same numbers. A skeleton
// fitted flush to its box hangs its outermost marks half outside that box, a
// mark being centered on — or, for a leaf, running outward from — a slot that is
// itself on the box's edge.

enum CanopyMark {
    /// The size a crown of this pitch cuts its marks to.
    static func base(pitch: CGFloat) -> CGFloat { max(2.4, pitch * 0.93) }

    /// One mark's size — its own word's standing, against that base.
    static func size(base: CGFloat, reach: Double) -> CGFloat {
        base * CGFloat(0.74 + 0.62 * reach)
    }

    /// Which way the mark leans off its twig: away from it and a little upward,
    /// which is what makes it read as attached rather than scattered.
    static func lean(_ slot: LeafSlot, grain: Double) -> Double {
        slot.angle + slot.side * (0.78 + 0.34 * grain) - 0.26
    }

    /// A leaf runs BROADER than the base a fruit or a blossom is cut to. It is
    /// the only mark meant to merge with its neighbors — foliage is a mass,
    /// fruit is a countable thing — and the extra reach is what closes the gaps
    /// between twigs. Kept well under the step from leaf to blossom to fruit, so
    /// a word maturing never reads as its mark being taken away.
    static let leafStretch: CGFloat = 1.24
    static let leafWaist: CGFloat = 0.29

    /// A bud, against the base the crown is cut to — well under half a leaf, so
    /// a word settling into one always reads as a gain.
    static let budRadius: CGFloat = 0.30

    /// The most a mark is ever drawn over its settled size: an arriving mark
    /// overshoots before it settles, and room has to be left for the overshoot
    /// too, or the celebration is the part that gets clipped.
    static let maxSwell: CGFloat = 1.25

    /// How far this crown reaches outside `box`, on the three sides a tree can
    /// be clipped on. Zero when it all fits.
    static func spill(of skeleton: TreeSkeleton, _ tree: AreaTree, in box: CGRect) -> CGFloat {
        let base = base(pitch: skeleton.pitch) * maxSwell
        var spill: CGFloat = 0
        for (rank, slot) in skeleton.slots.prefix(tree.canopyCount).enumerated() {
            let reach = rank < tree.reaches.count ? tree.reaches[rank] : 0.4
            let size = size(base: base, reach: reach)
            let ink: CGRect
            if rank < tree.fruit + tree.blossoms || rank >= tree.canopyCount - tree.buds {
                // Fruit, blossom and bud all sit ON their slot; the stalk is the
                // furthest any of them gets from it.
                let radius = size * (rank < tree.fruit + tree.blossoms ? 0.62 : budRadius)
                ink = CGRect(x: slot.point.x - radius, y: slot.point.y - radius,
                             width: radius * 2, height: radius * 2)
            } else {
                ink = leafBounds(at: slot.point, size: size * leafStretch,
                                 angle: lean(slot, grain: ForestLayout.noise("\(tree.id)-\(rank)", 41)))
            }
            spill = max(spill, max(box.minY - ink.minY,
                                   max(box.minX - ink.minX, ink.maxX - box.maxX)))
        }
        return max(0, spill)
    }

    /// The leaf ellipse's bounding box: a leaf runs from its slot OUTWARD along
    /// the lean, so its center is half a leaf from the point it hangs on.
    private static func leafBounds(at point: CGPoint, size: CGFloat, angle: Double) -> CGRect {
        let cosine = CGFloat(cos(angle)), sine = CGFloat(sin(angle))
        let long = size / 2, short = size * leafWaist
        let center = CGPoint(x: point.x + long * cosine, y: point.y + long * sine)
        let wide = sqrt(long * long * cosine * cosine + short * short * sine * sine)
        let tall = sqrt(long * long * sine * sine + short * short * cosine * cosine)
        return CGRect(x: center.x - wide, y: center.y - tall, width: wide * 2, height: tall * 2)
    }
}
