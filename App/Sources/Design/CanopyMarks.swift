import SwiftUI

// MARK: - The marks a canopy carries
//
// One shape per tier, told apart by SHAPE before color:
// a leaf is long and pointed, a bud a small disc, a blossom a pale rosette of five petals,
// fruit a pair of cherries on a forked stalk.

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

    /// A word held for months: a pair of cherries hanging from the slot on a forked stalk.
    static func fruit(at slot: CGPoint, size: CGFloat, cherries: inout Path, stalks: inout Path) {
        let small = size < CanopyMark.plain
        // why: the stalks start on the slot, so the pair hangs under its wood.
        let hang = size * (small ? 0.36 : 0.72)
        let pair = [CGPoint(x: slot.x - size * 0.27, y: slot.y + hang + size * 0.06),
                    CGPoint(x: slot.x + size * 0.25, y: slot.y + hang - size * 0.04)]
        for cherry in pair {
            cherries.addPath(circle(cherry, size * 0.25))
            guard !small else { continue }
            stalks.move(to: slot)
            stalks.addQuadCurve(to: CGPoint(x: cherry.x, y: cherry.y - size * 0.24),
                                control: CGPoint(x: slot.x + (cherry.x - slot.x) * 0.2,
                                                 y: slot.y + hang * 0.45))
        }
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

    /// A word that has matured: a pale rosette of five petals, kept small —
    /// a tree carrying forty of them is still a tree in flower, not a bouquet.
    /// Among the Trees the rosette is one disc, and only a large one shows an eye.
    static func blossom(at point: CGPoint, size: CGFloat, angle: Double,
                        petals: inout Path, eyes: inout Path) {
        guard size >= CanopyMark.plain else { return petals.addPath(circle(point, size * 0.38)) }
        for petal in 0..<5 {
            let turn = angle + Double(petal) * 2 * .pi / 5
            petals.addPath(circle(CGPoint(x: point.x + CGFloat(cos(turn)) * size * 0.2,
                                          y: point.y + CGFloat(sin(turn)) * size * 0.2),
                                  size * 0.24))
        }
        if size >= CanopyMark.eyed { eyes.addPath(circle(point, size * 0.06)) }
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
    static let budRadius: CGFloat = 0.22

    /// Below this size a blossom is one disc and fruit two dots: petals and stalks blur.
    static let plain: CGFloat = 12
    /// From this size on a blossom shows its eye.
    static let eyed: CGFloat = 20

}
