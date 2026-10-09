import SwiftUI
import SprossKern

// MARK: - Drawing one area's tree
//
// Kern draws the tree (`TreePicture`): what hangs where, every outline, each layer's ink and opacity.
// This turns its outlines into paths once and inks them in the theme's colors.

struct TreeArt {
    private struct Piece {
        let rank: Int
        let pivot: CGPoint
        let path: Path
    }

    private struct Layer {
        let color: Color
        let stroke: CGFloat
        let whole: Path
        let pieces: [Piece]
    }

    private let layers: [Layer]

    init(_ picture: TreePicture) {
        layers = picture.layers.map { layer in
            let pieces = layer.shapes.map {
                Piece(rank: Int($0.rank), pivot: CGPoint(x: $0.pivotX, y: $0.pivotY), path: Self.path($0.path))
            }
            var whole = Path()
            for piece in pieces { whole.addPath(piece.path) }
            return Layer(color: Self.color(layer.ink).opacity(layer.opacity), stroke: CGFloat(layer.stroke),
                         whole: whole, pieces: pieces)
        }
    }

    /// Draws every layer; `scales` sizes the marks at those ranks against their settled size.
    func draw(_ context: inout GraphicsContext, scales: [Int: CGFloat] = [:]) {
        for layer in layers {
            var path = layer.whole
            if !scales.isEmpty, layer.pieces.contains(where: { scales[$0.rank] != nil }) {
                path = Path()
                for piece in layer.pieces {
                    guard let scale = scales[piece.rank] else { path.addPath(piece.path); continue }
                    path.addPath(piece.path, transform: CGAffineTransform(translationX: piece.pivot.x, y: piece.pivot.y)
                        .scaledBy(x: scale, y: scale).translatedBy(x: -piece.pivot.x, y: -piece.pivot.y))
                }
            }
            if layer.stroke > 0 {
                context.stroke(path, with: .color(layer.color),
                               style: StrokeStyle(lineWidth: layer.stroke, lineCap: .round))
            } else {
                context.fill(path, with: .color(layer.color))
            }
        }
    }

    private static func color(_ ink: TreeInk) -> Color {
        switch ink {
        case .ground: Theme.colors.ground
        case .wood: Theme.colors.wood
        case .woodShade: Theme.colors.woodShade
        case .leaf: Theme.colors.leaf
        case .leafDeep: Theme.colors.leafDeep
        case .bud: Theme.colors.bud
        case .fruit: Theme.colors.fruit
        case .blossom: Theme.colors.blossom
        case .fallen: Theme.colors.fallen
        case .accent: Theme.colors.accent
        }
    }

    /// Kern's outline commands (`TreePath`) as a path.
    private static func path(_ data: KotlinDoubleArray) -> Path {
        var path = Path()
        var i: Int32 = 0
        func at(_ k: Int32) -> CGFloat { CGFloat(data.get(index: i + k)) }
        while i < data.size {
            switch Int32(data.get(index: i)) {
            case TreePath.shared.MOVE:
                path.move(to: CGPoint(x: at(1), y: at(2))); i += 3
            case TreePath.shared.LINE:
                path.addLine(to: CGPoint(x: at(1), y: at(2))); i += 3
            case TreePath.shared.QUAD:
                path.addQuadCurve(to: CGPoint(x: at(3), y: at(4)), control: CGPoint(x: at(1), y: at(2))); i += 5
            case TreePath.shared.OVAL:
                path.addEllipse(in: CGRect(x: at(1), y: at(2), width: at(3), height: at(4))); i += 5
            default:
                path.closeSubpath(); i += 1
            }
        }
        return path
    }
}
