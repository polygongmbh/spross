import SwiftUI

// MARK: - The marks a canopy carries
//
// One shape per tier, told apart by SHAPE before color:
// a leaf is long and pointed, a bud a small disc, a blossom a spur of three pale flowers,
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
        let radius = max(size * 0.25, CanopyMark.fruitFloor)
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

    /// A word that has matured: a spur of three flowers based on the slot, so the twig runs
    /// into it — two laterals and the larger king flower beyond them, along the mark's lean.
    /// Among the Trees each flower is one disc, and only large ones show an eye.
    static func blossom(at point: CGPoint, size: CGFloat, angle: Double,
                        laterals: inout Path, kings: inout Path, eyes: inout Path) {
        let u = CGPoint(x: cos(angle), y: sin(angle)), v = CGPoint(x: -u.y, y: u.x)
        func flower(_ along: CGFloat, _ across: CGFloat, _ radius: CGFloat, _ turn: Double,
                    _ into: inout Path) {
            let center = CGPoint(x: point.x + (u.x * along + v.x * across) * size,
                                 y: point.y + (u.y * along + v.y * across) * size)
            let r = radius * size
            guard size >= CanopyMark.plain else { return into.addPath(circle(center, r)) }
            for petal in 0..<5 {
                let spin = turn + Double(petal) * 2 * .pi / 5
                into.addPath(circle(CGPoint(x: center.x + CGFloat(cos(spin)) * r * 0.45,
                                            y: center.y + CGFloat(sin(spin)) * r * 0.45), r * 0.55))
            }
            if size >= CanopyMark.eyed { eyes.addPath(circle(center, r * 0.12)) }
        }
        // why: each flower turned its own way, so the three do not look stamped.
        flower(0.12, 0.20, 0.17, angle, &laterals)
        flower(0.10, -0.22, 0.15, angle + 1.3, &laterals)
        flower(0.30, 0, 0.22, angle + 2.1, &kings)
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
    static let budRadius: CGFloat = 0.11

    /// The smallest a fruit's radius is drawn, so a small tree's fruit stays visible.
    static let fruitFloor: CGFloat = 1.6

    /// Below this size each of a blossom's flowers is one disc: petals blur.
    static let plain: CGFloat = 12
    /// From this size on a blossom's flowers show their eyes.
    static let eyed: CGFloat = 20

}
