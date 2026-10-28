import SwiftUI
import UIKit

// MARK: - The hinge
//
// A half-open foldable splits its screen in two; lying flat it is one screen.
// Only `UIHingeInteraction` (iOS 27.1) reports which; older systems and phones read flat.

/// Passes `content` whether the device's hinge is half open.
struct HingeReader<Content: View>: View {
    @ViewBuilder var content: (Bool) -> Content
    @State private var halfOpen = false

    var body: some View {
        content(halfOpen)
            .background { HingeProbe { halfOpen = $0 } }
    }
}

private struct HingeProbe: UIViewRepresentable {
    let report: (Bool) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        if #available(iOS 27.1, *) {
            view.addInteraction(UIHingeInteraction { _, update in
                context.coordinator.report?(update.hinge?.status == .partiallyOpen)
            })
        }
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.report = report
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        var report: ((Bool) -> Void)?
    }
}
