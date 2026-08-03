import SwiftUI

// MARK: - Drawn plants
//
// One plant, drawn into a Canvas context at its mark. Everything is built from
// the mark: no view, no state, no per-plant animation. A plant is drawn from
// its FOOT upward, so plants on one band stand on one line however tall they
// grew.
//
// Species differ by silhouette first and colour second — a canopy the size of a
// grain of rice cannot carry a hue difference, and colour alone would say
// nothing to a colourblind eye anyway. The grove label below the patch is what
// actually names the area; this is texture.

enum PlantShapes {

    /// Ground colour, and what a never-started word leaves behind.
    static func soil(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let radius = max(0.8, mark.size * 0.09)
        let dot = CGRect(
            x: mark.foot.x - radius, y: mark.foot.y - radius,
            width: radius * 2, height: radius * 2
        )
        context.fill(Path(ellipseIn: dot), with: .color(.dlSeparator))
    }

    static func draw(_ context: inout GraphicsContext, _ mark: PlantMark) {
        guard mark.plant.stage != .soil else { return soil(&context, mark) }

        var layer = context
        // why: the back bands sit behind the front ones in the same light —
        // fading them is the only depth cue left once everything is this small.
        layer.opacity = 0.62 + 0.38 * mark.depth
        layer.translateBy(x: mark.foot.x, y: mark.foot.y)
        layer.rotate(by: .radians(mark.tilt))

        switch mark.plant.stage {
        case .soil: return
        case .seed: seed(&layer, mark)
        case .sprout: sprout(&layer, mark)
        case .stem: stalk(&layer, mark, leaves: 0)
        case .leafed: stalk(&layer, mark, leaves: 2)
        case .bloom: bloom(&layer, mark)
        case .tree: tree(&layer, mark)
        case .wilting: wilting(&layer, mark)
        case .dormant: dormant(&layer, mark)
        }

        if mark.plant.touchedToday { spark(&layer, mark) }
    }

    // MARK: Stages

    private static func seed(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let w = mark.size * 0.34
        let h = mark.size * 0.26
        let seed = CGRect(x: -w / 2, y: -h, width: w, height: h)
        context.fill(Path(ellipseIn: seed), with: .color(.dlBorderStrong))
    }

    private static func sprout(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let h = mark.size * 0.5
        context.stroke(stem(height: h, bend: 0), with: .color(.dlSuccess), lineWidth: lineWidth(mark))
        leaf(&context, at: CGPoint(x: 0, y: -h), size: mark.size * 0.3, angle: -0.7, color: .dlSuccess)
        leaf(&context, at: CGPoint(x: 0, y: -h), size: mark.size * 0.3, angle: 0.7, color: .dlSuccess)
    }

    /// The upright plant every grown stage is built on.
    private static func stalk(_ context: inout GraphicsContext, _ mark: PlantMark, leaves: Int) {
        let h = mark.size * (leaves == 0 ? 0.66 : 0.78)
        context.stroke(stem(height: h, bend: mark.tilt * 1.6), with: .color(.dlSuccess), lineWidth: lineWidth(mark))
        for index in 0..<leaves {
            let at = CGPoint(x: 0, y: -h * (0.5 + 0.28 * Double(index)))
            leaf(&context, at: at, size: mark.size * 0.26,
                 angle: index.isMultiple(of: 2) ? -1.0 : 1.0, color: .dlSuccess)
        }
    }

    /// In flower: the stalk, plus petals whose count is the species.
    private static func bloom(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let h = mark.size * 0.8
        context.stroke(stem(height: h, bend: mark.tilt * 1.6), with: .color(.dlSuccess), lineWidth: lineWidth(mark))
        leaf(&context, at: CGPoint(x: 0, y: -h * 0.5), size: mark.size * 0.24, angle: -1.0, color: .dlSuccess)

        let head = CGPoint(x: 0, y: -h)
        let petals = petalCount(mark.plant.kind)
        let radius = mark.size * 0.2
        let colour = kindColour(mark.plant.kind)
        for index in 0..<petals {
            let angle = Double(index) / Double(petals) * 2 * .pi + mark.tilt
            let centre = CGPoint(x: head.x + cos(angle) * radius * 0.9, y: head.y + sin(angle) * radius * 0.9)
            let petal = CGRect(
                x: centre.x - radius * 0.6, y: centre.y - radius * 0.6,
                width: radius * 1.2, height: radius * 1.2
            )
            context.fill(Path(ellipseIn: petal), with: .color(colour))
        }
        let eye = CGRect(x: head.x - radius * 0.4, y: head.y - radius * 0.4, width: radius * 0.8, height: radius * 0.8)
        context.fill(Path(ellipseIn: eye), with: .color(.dlAmber))
    }

    /// Full grown: a trunk and a canopy whose shape is the species.
    private static func tree(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let h = mark.size
        context.stroke(stem(height: h * 0.56, bend: 0), with: .color(.dlBorderStrong),
                       lineWidth: lineWidth(mark) * 1.7)
        let canopy = mark.size * (0.38 + 0.1 * mark.plant.growth)
        let centre = CGPoint(x: 0, y: -h * 0.62 - canopy * 0.3)

        switch mark.plant.kind {
        case .noun:
            // Round crown.
            context.fill(circle(centre, canopy * 0.62), with: .color(.dlSuccess))
        case .verb:
            // Conifer — three stacked tiers.
            for index in 0..<3 {
                let tier = canopy * (0.66 - 0.16 * Double(index))
                let top = centre.y - canopy * (0.1 + 0.34 * Double(index))
                var path = Path()
                path.move(to: CGPoint(x: centre.x, y: top - tier))
                path.addLine(to: CGPoint(x: centre.x - tier * 0.8, y: top + tier * 0.35))
                path.addLine(to: CGPoint(x: centre.x + tier * 0.8, y: top + tier * 0.35))
                path.closeSubpath()
                context.fill(path, with: .color(.dlSuccess))
            }
        case .modifier:
            // Two lobes — a broad, low crown.
            context.fill(circle(CGPoint(x: centre.x - canopy * 0.28, y: centre.y), canopy * 0.48),
                         with: .color(.dlSuccess))
            context.fill(circle(CGPoint(x: centre.x + canopy * 0.28, y: centre.y), canopy * 0.48),
                         with: .color(.dlSuccess))
        case .phrase:
            // A crown carrying fruit: a phrase is its component words, grown together.
            context.fill(circle(centre, canopy * 0.62), with: .color(.dlSuccess))
            for index in 0..<3 {
                let angle = Double(index) / 3 * 2 * .pi + mark.tilt * 3
                let fruit = CGPoint(
                    x: centre.x + cos(angle) * canopy * 0.34,
                    y: centre.y + sin(angle) * canopy * 0.34
                )
                context.fill(circle(fruit, canopy * 0.13), with: .color(.dlAccent))
            }
        }
    }

    private static func wilting(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let h = mark.size * 0.5
        context.stroke(stem(height: h, bend: 0.5), with: .color(.dlAmber), lineWidth: lineWidth(mark))
        leaf(&context, at: CGPoint(x: h * 0.2, y: -h * 0.9), size: mark.size * 0.26, angle: 2.2, color: .dlAmber)
    }

    private static func dormant(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let h = mark.size * 0.34
        context.stroke(stem(height: h, bend: 0), with: .color(.dlSeparator), lineWidth: lineWidth(mark))
    }

    /// Answered today — a light ring at the plant's foot, so the day's work
    /// shows where it happened rather than as a number somewhere else.
    private static func spark(_ context: inout GraphicsContext, _ mark: PlantMark) {
        let radius = mark.size * 0.3
        let ring = CGRect(x: -radius, y: -radius * 0.34, width: radius * 2, height: radius * 0.68)
        context.stroke(Path(ellipseIn: ring), with: .color(.dlAccent), lineWidth: max(0.6, mark.size * 0.05))
    }

    // MARK: Pieces

    private static func lineWidth(_ mark: PlantMark) -> CGFloat {
        max(0.7, mark.size * 0.075)
    }

    /// A stem rising from the origin, bending by `bend` at the tip.
    private static func stem(height: CGFloat, bend: Double) -> Path {
        var path = Path()
        path.move(to: .zero)
        path.addQuadCurve(
            to: CGPoint(x: height * CGFloat(bend), y: -height),
            control: CGPoint(x: height * CGFloat(bend) * 0.2, y: -height * 0.55)
        )
        return path
    }

    private static func leaf(_ context: inout GraphicsContext, at point: CGPoint,
                             size: CGFloat, angle: Double, color: Color) {
        var layer = context
        layer.translateBy(x: point.x, y: point.y)
        layer.rotate(by: .radians(angle))
        let leaf = CGRect(x: 0, y: -size * 0.22, width: size, height: size * 0.44)
        layer.fill(Path(ellipseIn: leaf), with: .color(color))
    }

    private static func circle(_ centre: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius,
                               width: radius * 2, height: radius * 2))
    }

    /// Species by petal count — the one difference legible at this size.
    private static func petalCount(_ kind: PlantKind) -> Int {
        switch kind {
        case .noun: return 5
        case .verb: return 4
        case .modifier: return 6
        case .phrase: return 8
        }
    }

    /// Bloom hues, borrowed from the article palette so the app keeps one set
    /// of colours. They separate species at a glance; they never carry the
    /// meaning on their own — the silhouette does.
    private static func kindColour(_ kind: PlantKind) -> Color {
        switch kind {
        case .noun: return .dlDie
        case .verb: return .dlDer
        case .modifier: return .dlDas
        case .phrase: return .dlTeal
        }
    }
}
