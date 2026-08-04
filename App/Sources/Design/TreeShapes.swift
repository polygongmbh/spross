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
// Each word is exactly one mark. What is still on its way in has no mark at
// all: it is why the tree is as tall as it is.

enum TreeShapes {

    /// Draws `mark`, showing the counts of `showing` — normally the same tree.
    ///
    /// They come apart during a transition: the SKELETON comes from `mark` and
    /// never sees a count, so it is identical before and after; only how many
    /// slots are filled, and with what, comes from `showing`. That is what lets
    /// a leaf turn into a blossom in place while nothing else moves.
    static func draw(_ context: inout GraphicsContext, _ mark: TreeMark,
                     showing: AreaTree? = nil) {
        let shown = showing ?? mark.tree
        // why: an area nobody has opened draws NOTHING — not even ground. A mark
        // on every untouched area turns a catalog the learner did not choose
        // into a list of things they have not done, and its dimmed emoji already
        // says the place exists.
        guard !shown.isBare else { return }
        ground(&context, mark)

        guard shown.canopyCount > 0 else { return seedling(&context, mark) }

        let skeleton = mark.skeleton
        branches(&context, skeleton, mark)
        foliage(&context, skeleton, mark, shown)
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

    /// One segment as a closed shape: both edges bow with the centre line, and
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

    /// The marks, on the slots the twigs offer. Leaves batch into two tones of
    /// the one green — a canopy in a single flat colour has no depth at any
    /// size — and blossom and fruit take the first slots, which the skeleton
    /// shuffled, so they scatter through the crown rather than ringing it.
    private static func foliage(_ context: inout GraphicsContext, _ skeleton: TreeSkeleton,
                                _ mark: TreeMark, _ shown: AreaTree) {
        let size = max(2.8, mark.height * 0.085)
        var light = Path()
        var dark = Path()

        for (rank, slot) in skeleton.slots.prefix(shown.canopyCount).enumerated() {
            // why: leaves point away from the twig and a little upward, which is
            // what makes them read as attached rather than scattered.
            let angle = slot.angle + slot.side * 0.95 - 0.26
            if rank < shown.fruit {
                context.fill(circle(slot.point, size * 0.34), with: .color(.dlAccent))
            } else if rank < shown.fruit + shown.blossoms {
                blossom(&context, at: slot.point, size: size)
            } else if rank.isMultiple(of: 2) {
                light.addPath(leafPath(at: slot.point, size: size, angle: angle))
            } else {
                dark.addPath(leafPath(at: slot.point, size: size, angle: angle))
            }
        }
        context.fill(light, with: .color(.dlSuccess.opacity(0.72)))
        context.fill(dark, with: .color(.dlSuccess))
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
        let leaf = Path(ellipseIn: CGRect(x: 0, y: -size * 0.29, width: size, height: size * 0.58))
        return leaf.applying(
            CGAffineTransform(translationX: point.x, y: point.y)
                .rotated(by: CGFloat(angle))
        )
    }

    private static func leaf(_ context: inout GraphicsContext, at point: CGPoint,
                             size: CGFloat, angle: Double, color: Color) {
        context.fill(leafPath(at: point, size: size, angle: angle), with: .color(color))
    }

    /// A word that has landed. Told apart from a leaf by SHAPE as well as
    /// colour — a rosette against an ellipse — so the canopy still reads where
    /// hue does not.
    private static func blossom(_ context: inout GraphicsContext, at point: CGPoint, size: CGFloat) {
        let petal = size * 0.3
        for index in 0..<5 {
            let angle = Double(index) / 5 * 2 * .pi
            let at = CGPoint(x: point.x + CGFloat(cos(angle)) * petal,
                             y: point.y + CGFloat(sin(angle)) * petal)
            context.fill(circle(at, petal * 0.82), with: .color(.dlDie))
        }
        context.fill(circle(point, petal * 0.5), with: .color(.dlAmber))
    }

    private static func circle(_ centre: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius,
                               width: radius * 2, height: radius * 2))
    }
}
