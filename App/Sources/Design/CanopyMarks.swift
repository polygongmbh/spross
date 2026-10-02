import SwiftUI

// MARK: - The marks a canopy carries
//
// One shape per tier, told apart by SHAPE before color:
// a leaf is long and pointed, a bud a small disc, a blossom five petals round an eye,
// fruit a larger disc on a stalk.

extension TreeShapes {

    /// A leaf running from `point` outward along `angle`: pointed at both ends,
    /// broadest a little before its middle.
    static func leafPath(at point: CGPoint, size: CGFloat, angle: Double) -> Path {
        let belly = size * CanopyMark.leafWaist * 1.9
        var leaf = Path()
        leaf.move(to: .zero)
        leaf.addQuadCurve(to: CGPoint(x: size, y: 0), control: CGPoint(x: size * 0.42, y: -belly))
        leaf.addQuadCurve(to: .zero, control: CGPoint(x: size * 0.42, y: belly))
        return leaf.applying(CGAffineTransform(translationX: point.x, y: point.y)
            .rotated(by: CGFloat(angle)))
    }

    /// A word held for months — the furthest thing on the tree, and drawn HEAVIER
    /// than a blossom, so a word promoting from one to the other reads as a gain.
    static func fruit(_ context: inout GraphicsContext, at point: CGPoint, size: CGFloat) {
        var stalk = Path()
        stalk.move(to: CGPoint(x: point.x, y: point.y - size * 0.62))
        stalk.addQuadCurve(to: CGPoint(x: point.x, y: point.y - size * 0.26),
                           control: CGPoint(x: point.x + size * 0.12, y: point.y - size * 0.45))
        context.stroke(stalk, with: .color(Theme.colors.borderStrong),
                       style: StrokeStyle(lineWidth: max(0.6, size * 0.1), lineCap: .round))
        context.fill(circle(point, size * 0.56), with: .color(Theme.colors.accent))
        // A highlight: at this size it is the difference between fruit and a dot.
        context.fill(circle(CGPoint(x: point.x - size * 0.17, y: point.y - size * 0.17),
                            size * 0.15),
                     with: .color(Theme.colors.surface.opacity(0.65)))
    }

    /// A word that has landed: a sprig of three leaflets off one stalk — one mark,
    /// since a word is one mark, but foliage rather than a stamp.
    static func sprig(at point: CGPoint, size: CGFloat, angle: Double) -> Path {
        var sprig = leafPath(at: point, size: size, angle: angle)
        let fork = CGPoint(x: point.x + CGFloat(cos(angle)) * size * 0.28,
                           y: point.y + CGFloat(sin(angle)) * size * 0.28)
        sprig.addPath(leafPath(at: fork, size: size * 0.66, angle: angle - 0.72))
        sprig.addPath(leafPath(at: fork, size: size * 0.6, angle: angle + 0.68))
        return sprig
    }

    /// A word that has matured: five petals round an ochre eye, kept small —
    /// a tree carrying forty of them is still a tree in flower, not a bouquet.
    static func blossom(_ context: inout GraphicsContext, at point: CGPoint,
                        size: CGFloat, angle: Double) {
        let span = size * 0.9
        var petals = Path()
        for petal in 0..<5 {
            let turn = angle + Double(petal) * 2 * .pi / 5
            let center = CGPoint(x: point.x + CGFloat(cos(turn)) * span * 0.25,
                                 y: point.y + CGFloat(sin(turn)) * span * 0.25)
            petals.addPath(circle(center, span * 0.22))
        }
        context.fill(petals, with: .color(Theme.colors.die.opacity(0.9)))
        context.fill(circle(point, span * 0.13), with: .color(Theme.colors.amber))
    }

    static func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                               width: radius * 2, height: radius * 2))
    }
}

// MARK: - Mark geometry
//
// How big a mark is drawn and how far it reaches past its slot — shared by the
// drawing and the FIT, since a skeleton fitted flush to its box hangs its
// outermost marks half outside it.

enum CanopyMark {
    /// The size a crown of this pitch cuts its marks to.
    static func base(pitch: CGFloat) -> CGFloat { max(2.4, pitch * 0.85) }

    /// One mark's size — its own word's standing, against that base.
    static func size(base: CGFloat, reach: Double) -> CGFloat {
        base * CGFloat(0.74 + 0.62 * reach)
    }

    /// Which way the mark faces: out from its wood, a touch upward, with a hashed turn.
    static func lean(_ slot: LeafSlot, grain: Double) -> Double {
        let turned = slot.angle + (grain - 0.5) * 0.9
        return atan2(sin(turned) - 0.2, cos(turned))
    }

    /// A leaf runs longer than the base a fruit or a blossom is cut to: it is the
    /// only mark meant to merge with its neighbors — foliage is a mass.
    static let leafStretch: CGFloat = 1.45
    static let leafWaist: CGFloat = 0.27

    /// A bud, against the base — well under half a leaf.
    static let budRadius: CGFloat = 0.30

}
