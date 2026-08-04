import SwiftUI

// MARK: - Drawing one area's tree
//
// The tree is ONE organism its whole life. It is never swapped for another:
// a seedling thickens into a trunk, the canopy fills with the words that have
// landed, and blossom and fruit appear ON that canopy rather than replacing it.
// So there is no rung at which the picture starts over, and no top rung at
// which it stops — a tree can always carry more.
//
// Marks are drawn outward-first, so the furthest-grown words read at the rim:
//   fruit    — a word a month or more out from its next sight
//   blossom  — a word that has landed
//   leaf     — a word that has settled
// Each word is exactly one mark. What is still on its way in has no mark at
// all: it is why the trunk is as tall as it is.

enum TreeShapes {

    static func draw(_ context: inout GraphicsContext, _ mark: TreeMark) {
        // why: an area nobody has opened draws NOTHING — not even ground. A mark
        // on every untouched area turns a catalog the learner did not choose
        // into a list of things they have not done, and its dimmed emoji already
        // says the place exists.
        guard !mark.tree.isBare else { return }
        ground(&context, mark)

        if mark.tree.canopyCount == 0 {
            seedling(&context, mark)
        } else {
            trunk(&context, mark)
            canopy(&context, mark)
        }
        fallen(&context, mark)
        if mark.tree.tendedToday { freshEarth(&context, mark) }
    }

    // MARK: Ground

    /// What the tree stands on: a soft shadow under the trunk, sized with the
    /// canopy above it.
    ///
    /// Deliberately NOT a line — a stroke under every tree read as a shelf, with
    /// the area's emoji beneath it looking like a label stuck to the furniture.
    /// A shadow grounds the tree without drawing anything.
    private static func ground(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let width = max(9, mark.canopyRadius * 1.5)
        let shadow = CGRect(x: mark.foot.x - width / 2, y: mark.baseline - 1.6,
                            width: width, height: 3.2)
        context.fill(Path(ellipseIn: shadow), with: .color(.dlSeparator.opacity(0.6)))
    }

    /// Answered today — a short line of fresh earth at the foot. A mark on the
    /// GROUND, never on the tree: it says this area was tended today, not that
    /// anything in it grew a stage.
    private static func freshEarth(_ context: inout GraphicsContext, _ mark: TreeMark) {
        var path = Path()
        path.move(to: CGPoint(x: mark.foot.x - 7, y: mark.baseline + 3.5))
        path.addLine(to: CGPoint(x: mark.foot.x + 7, y: mark.baseline + 3.5))
        context.stroke(path, with: .color(.dlAccent),
                       style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }

    // MARK: The tree

    /// Nothing has settled here yet: a stem and two leaflets. Packing a whole
    /// area puts ONE of these on the plot — forty words packed is still one
    /// intention, and drawing it as forty objects was the old spilled bag.
    private static func seedling(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let top = CGPoint(x: mark.foot.x, y: mark.baseline - ForestLayout.minTrunk)
        var stem = Path()
        stem.move(to: mark.foot)
        stem.addLine(to: top)
        context.stroke(stem, with: .color(.dlSuccess),
                       style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        leaf(&context, at: top, size: 5.5, angle: -0.7, color: .dlSuccess)
        leaf(&context, at: top, size: 5.5, angle: .pi + 0.7, color: .dlSuccess)
    }

    /// A tapered trunk that forks into the canopy's lobes. Both halves matter:
    /// an untapered stroke reads as a stick, and a canopy that does not grow out
    /// of branches reads as a ball balanced on one.
    private static func trunk(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let base = 1.5 + 2.4 * (mark.canopyRadius / 15.5)
        let lean = CGFloat(ForestLayout.noise(mark.tree.id, 21) - 0.5) * 2.2
        let top = CGPoint(x: mark.crown.x + lean, y: mark.crown.y)

        // Branches first, so the trunk's own taper covers where they meet it.
        let lobes = ForestLayout.lobes(mark)
        let fork = CGPoint(x: mark.foot.x + lean * 0.5,
                           y: mark.foot.y - (mark.foot.y - top.y) * 0.55)
        for lobe in lobes.dropFirst() {
            var branch = Path()
            branch.move(to: fork)
            branch.addQuadCurve(
                to: CGPoint(x: lobe.centre.x, y: lobe.centre.y + lobe.radius * 0.2),
                control: CGPoint(x: (fork.x + lobe.centre.x) / 2, y: fork.y - (fork.y - lobe.centre.y) * 0.7)
            )
            context.stroke(branch, with: .color(.dlBorderStrong),
                           style: StrokeStyle(lineWidth: max(0.8, base * 0.42), lineCap: .round))
        }

        // The trunk as a filled taper: wide at the foot, narrow at the crown.
        var path = Path()
        path.move(to: CGPoint(x: mark.foot.x - base / 2, y: mark.foot.y))
        path.addQuadCurve(to: CGPoint(x: top.x - base * 0.16, y: top.y),
                          control: CGPoint(x: mark.foot.x - base * 0.4,
                                           y: (mark.foot.y + top.y) / 2))
        path.addLine(to: CGPoint(x: top.x + base * 0.16, y: top.y))
        path.addQuadCurve(to: CGPoint(x: mark.foot.x + base / 2, y: mark.foot.y),
                          control: CGPoint(x: mark.foot.x + base * 0.4,
                                           y: (mark.foot.y + top.y) / 2))
        path.closeSubpath()
        context.fill(path, with: .color(.dlBorderStrong))
    }

    /// The canopy IS the area's settled words. Mark size falls as the count
    /// rises, so a full canopy reads as foliage rather than as counted objects —
    /// a leaf is a thing you believe there are many of without counting them.
    private static func canopy(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let tree = mark.tree
        // why: a soft mass behind the marks is what makes a scatter of leaves
        // read as foliage rather than as confetti hanging in the air.
        for lobe in ForestLayout.lobes(mark) {
            context.fill(circle(lobe.centre, lobe.radius * 1.05),
                         with: .color(.dlSuccess.opacity(0.16)))
        }

        let points = ForestLayout.canopy(mark, count: tree.canopyCount)
        let size = max(2.4, min(5.2, mark.canopyRadius * 1.6 / sqrt(Double(tree.canopyCount))))

        for (index, point) in points.enumerated() {
            // Outward-first: the rim carries the words that have come furthest.
            if index < tree.fruit {
                context.fill(circle(point, size * 0.5), with: .color(.dlAccent))
            } else if index < tree.fruit + tree.blossoms {
                blossom(&context, at: point, size: size)
            } else {
                let seed = ForestLayout.noise("\(tree.id)-\(index)", 11)
                // Two tones of the one green: a canopy in a single flat colour
                // has no depth at any size.
                leaf(&context, at: point, size: size * 1.45, angle: seed * .pi,
                     color: .dlSuccess.opacity(seed > 0.55 ? 1 : 0.72))
            }
        }
    }

    /// Words that lapsed: leaves on the ground beside the trunk. The tree never
    /// shrinks for them — the engine expects roughly one review in five to miss,
    /// and a picture that shrank the tree for a routine Tuesday would be lying
    /// about what a lapse costs.
    private static func fallen(_ context: inout GraphicsContext, _ mark: TreeMark) {
        guard mark.tree.fallen > 0 else { return }
        // why: clear of the shadow and small — clustered at the trunk they read
        // as dirt at the base rather than as leaves that came down.
        let clear = max(7, mark.canopyRadius * 0.9)
        for index in 0..<min(mark.tree.fallen, 3) {
            let side: CGFloat = index.isMultiple(of: 2) ? -1 : 1
            let spread = clear + CGFloat(ForestLayout.noise("\(mark.tree.id)-f\(index)", 13) * 6)
            let at = CGPoint(x: mark.foot.x + side * spread, y: mark.baseline + 0.5)
            leaf(&context, at: at, size: 3.6, angle: side > 0 ? 0.2 : .pi - 0.2,
                 color: .dlAmber.opacity(0.85))
        }
    }

    // MARK: Marks

    private static func leaf(_ context: inout GraphicsContext, at point: CGPoint,
                             size: CGFloat, angle: Double, color: Color) {
        var layer = context
        layer.translateBy(x: point.x, y: point.y)
        layer.rotate(by: .radians(angle))
        layer.fill(Path(ellipseIn: CGRect(x: -size * 0.5, y: -size * 0.22,
                                          width: size, height: size * 0.44)),
                   with: .color(color))
    }

    /// A word that has landed. Told apart from a leaf by SHAPE as well as
    /// colour — a rosette against an ellipse — so the canopy still reads where
    /// hue does not.
    private static func blossom(_ context: inout GraphicsContext, at point: CGPoint, size: CGFloat) {
        let petal = size * 0.4
        for index in 0..<5 {
            let angle = Double(index) / 5 * 2 * .pi
            let at = CGPoint(x: point.x + CGFloat(cos(angle)) * petal,
                             y: point.y + CGFloat(sin(angle)) * petal)
            context.fill(circle(at, petal * 0.8), with: .color(.dlDie))
        }
        context.fill(circle(point, petal * 0.55), with: .color(.dlAmber))
    }

    private static func circle(_ centre: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius,
                               width: radius * 2, height: radius * 2))
    }
}
