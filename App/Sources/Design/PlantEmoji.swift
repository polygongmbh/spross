import SwiftUI

// MARK: - Emoji plants
//
// The same forest drawn with the app's own illustration system — emoji, as
// everywhere else on screen. It reads warmer and ships without a single path,
// but growth lands in discrete steps rather than continuously, and a thousand
// glyphs at 10 pt look like a sticker sheet where drawn shapes look like ground.
// Both styles are kept so the choice can be made on a real box.

enum PlantEmoji {

    static func draw(_ context: inout GraphicsContext, _ mark: PlantMark) {
        guard let glyph = glyph(mark.plant) else { return PlantShapes.soil(&context, mark) }

        var layer = context
        layer.opacity = 0.7 + 0.3 * mark.depth
        layer.translateBy(x: mark.foot.x, y: mark.foot.y)
        layer.rotate(by: .radians(mark.tilt))

        // why: a glyph is drawn from its middle, and every plant here stands on
        // its foot — so it is lifted by half its own height to reach the ground.
        let size = mark.size * 0.8
        let text = Text(verbatim: glyph).font(.system(size: size))
        layer.draw(context.resolve(text), at: CGPoint(x: 0, y: -size * 0.5), anchor: .center)

        if mark.plant.touchedToday { PlantShapes.spark(&layer, mark) }
    }

    /// Nil where the stage has no plant to draw — bare ground falls back to the
    /// drawn speck, since there is no emoji for "nothing has been planted here".
    private static func glyph(_ plant: Plant) -> String? {
        switch plant.stage {
        case .soil: return nil
        case .seed: return "🌰"
        case .sprout: return "🌱"
        case .stem: return "🌿"
        case .leafed: return "☘️"
        case .bloom: return bloom(plant.kind)
        case .tree: return tree(plant.kind)
        case .wilting: return "🥀"
        case .dormant: return "🍂"
        }
    }

    private static func bloom(_ kind: PlantKind) -> String {
        switch kind {
        case .noun: return "🌸"
        case .verb: return "🌻"
        case .modifier: return "🌷"
        case .phrase: return "💐"
        }
    }

    private static func tree(_ kind: PlantKind) -> String {
        switch kind {
        case .noun: return "🌳"
        case .verb: return "🌲"
        case .modifier: return "🌴"
        case .phrase: return "🎋"
        }
    }
}
