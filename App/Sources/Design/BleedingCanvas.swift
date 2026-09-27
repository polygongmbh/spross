import SwiftUI

/// A Canvas whose ink may run past the box it is laid out in.
///
/// A Canvas clips to its own frame, and a tree's marks hang off the ends of its wood —
/// so the drawing surface reaches `bleed` points past every side of the layout box
/// while the renderer keeps the box's own coordinates and size.
/// Layout never sees the bleed: neighbors are placed against the box alone.
struct BleedingCanvas: View {
    let bleed: CGFloat
    let renderer: (inout GraphicsContext, CGSize) -> Void

    var body: some View {
        Color.clear.overlay {
            Canvas { context, size in
                context.translateBy(x: bleed, y: bleed)
                renderer(&context, CGSize(width: max(size.width - bleed * 2, 0),
                                          height: max(size.height - bleed * 2, 0)))
            }
            .padding(-bleed)
            .allowsHitTesting(false)
        }
    }
}
