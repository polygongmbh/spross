import AppKit
import DuoKern
import SwiftUI

struct RenderSize: Sendable {
    var width: Int
    var height: Int
}

enum FaceGenError: Error, CustomStringConvertible {
    case renderFailed(String)
    case pngEncodeFailed(String)

    var description: String {
        switch self {
        case .renderFailed(let id): "ImageRenderer produced no image for card \(id)"
        case .pngEncodeFailed(let id): "PNG encoding failed for card \(id)"
        }
    }
}

/// Renders a card to a PNG via SwiftUI ImageRenderer.
/// The view is laid out at half the pixel size in points and rendered at
/// scale 2, so the output matches the requested pixel size crisply.
@MainActor
enum FaceRenderer {
    static func renderPNG(card: Card, size: RenderSize, timeSafeTop: Double, to url: URL) throws {
        let points = CGSize(width: Double(size.width) / 2, height: Double(size.height) / 2)
        let view = CardFaceView(card: card, canvas: points, timeSafeTop: timeSafeTop)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        renderer.proposedSize = ProposedViewSize(points)
        guard let cgImage = renderer.cgImage else {
            throw FaceGenError.renderFailed(card.id)
        }
        let rep = NSBitmapImageRep(cgImage: cgImage)
        guard let png = rep.representation(using: .png, properties: [:]) else {
            throw FaceGenError.pngEncodeFailed(card.id)
        }
        try png.write(to: url)
    }
}
