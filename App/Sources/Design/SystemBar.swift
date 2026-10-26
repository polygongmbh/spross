import SwiftUI

// MARK: - The system's bar
//
// A wide window on iOS 27.1 gives a presentation's toolbar its own bar, standing upright
// at the trailing edge where the window has one. Each call into those APIs sits in one
// helper here behind its `#available`; older systems keep the same layout without the bar.

enum SystemBar {
    /// Whether a session's chrome goes into the system's toolbar rather than its own top bar.
    static func hostsChrome(wide: Bool) -> Bool {
        guard wide else { return false }
        if #available(iOS 27.1, *) { return true }
        return false
    }
}

/// Hands `content` whether the toolbar it stands in runs upright.
struct VerticalBarReader<Content: View>: View {
    @ViewBuilder var content: (Bool) -> Content

    var body: some View {
        if #available(iOS 27.1, *) {
            VerticalEdgeProbe(content: content)
        } else {
            content(false)
        }
    }
}

@available(iOS 27.1, *)
private struct VerticalEdgeProbe<Content: View>: View {
    @Environment(\.toolbarVerticalEdge) private var edge
    var content: (Bool) -> Content

    var body: some View { content(edge != nil) }
}

extension View {
    /// A session's chrome as toolbar items: the way out first, where the bar keeps it,
    /// then the progress, upright where the bar is, then the read-aloud switch.
    @ViewBuilder
    func sessionSystemBar<Progress: View, Trailing: View>(
        enabled: Bool, readsAloud: Bool, onClose: @escaping () -> Void,
        @ViewBuilder progress: @escaping () -> Progress,
        @ViewBuilder readAloud: @escaping () -> Trailing
    ) -> some View {
        if #available(iOS 27.1, *) {
            self
                .toolbar {
                    if enabled {
                        ToolbarItem(placement: .cancellationAction) {
                            Button(action: onClose) { Image(systemName: "xmark") }
                                .accessibilityLabel("a11y.action.endSession")
                        }
                        ToolbarItem(placement: .primaryAction) { progress() }
                            .axisBehavior(.verticalPreferred)
                        if readsAloud {
                            ToolbarItem(placement: .primaryAction) { readAloud() }
                        }
                    }
                }
                .toolbar(enabled ? .automatic : .hidden, for: .navigationBar)
        } else {
            self.toolbar(.hidden, for: .navigationBar)
        }
    }
}

extension View {
    /// A sheet's content asking to stand on the trailing side, where the system's bar is.
    @ViewBuilder
    func trailingPresentation() -> some View {
        if #available(iOS 27.0, *) {
            presentationPlacement(.trailing)
        } else {
            self
        }
    }
}
