import SwiftUI

// MARK: - The marks a canopy carries
//
// One shape per stage, told apart by SHAPE before color:
// a leaf is long and pointed, a bud a small disc, a blossom five butter petals round an eye,
// fruit a round disc hanging under its twig.

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

    /// A word held for months: one round fruit, its top on the slot, so it hangs under its wood.
    static func fruit(at slot: CGPoint, size: CGFloat) -> Path {
        let radius = max(size * 0.4, CanopyMark.fruitFloor)
        return circle(CGPoint(x: slot.x, y: slot.y + radius), radius)
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

    /// A word that has settled: five petals round an eye, kept small —
    /// a tree carrying forty of them is still a tree in flower, not a bouquet.
    /// Among the Trees the petals are one disc: they blur.
    static func blossom(at point: CGPoint, size: CGFloat, angle: Double,
                        petals: inout Path, eyes: inout Path) {
        let span = size * 0.9
        if size < CanopyMark.plain {
            petals.addPath(circle(point, span * 0.42))
        } else {
            for petal in 0..<5 {
                let turn = angle + Double(petal) * 2 * .pi / 5
                petals.addPath(circle(CGPoint(x: point.x + CGFloat(cos(turn)) * span * 0.25,
                                              y: point.y + CGFloat(sin(turn)) * span * 0.25), span * 0.22))
            }
        }
        eyes.addPath(circle(point, span * 0.13))
    }

    static func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                               width: radius * 2, height: radius * 2))
    }
}

// MARK: - Mark geometry
//
// How big a mark is drawn, by its word's strength, and how far it reaches past its slot — shared by the
// drawing and the FIT, since a skeleton fitted flush to its box hangs its
// outermost marks half outside it.

enum CanopyMark {
    /// The size a crown of this pitch cuts its marks to.
    static func base(pitch: CGFloat) -> CGFloat { max(2.4, pitch * 0.85) }

    /// One mark's size — its own word's standing, against that base.
    static func size(base: CGFloat, strength: Double) -> CGFloat {
        base * CGFloat(0.74 + 0.62 * strength)
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
    static let budRadius: CGFloat = 0.11

    /// The smallest a fruit's radius is drawn, so a small tree's fruit stays visible.
    static let fruitFloor: CGFloat = 1.6

    /// Below this size a blossom's petals are one disc: they blur.
    static let plain: CGFloat = 12

}
