import SwiftUI

// MARK: - The system's bar
//
// On iOS 27.1 a wide window can show a presentation's toolbar as an upright bar at its trailing edge.
// Each call into those APIs is one helper here behind its `#available`; older systems keep the layout without the bar.

enum SystemBar {
    /// Whether a session's chrome goes into the system's toolbar.
    static func hostsChrome(wide: Bool) -> Bool {
        guard wide else { return false }
        if #available(iOS 27.1, *) { return true }
        return false
    }
}

/// Passes `content` whether its toolbar is upright.
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
    /// A session's chrome as toolbar items: close, the progress (upright in a vertical bar), the read-aloud switch.
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
    /// Places a sheet on the trailing side, beside the system's bar, while the hinge is half open;
    /// a flat window keeps the system's placement.
    func trailingPresentation() -> some View {
        HingeReader { halfOpen in
            if #available(iOS 27.0, *), halfOpen {
                presentationPlacement(.trailing)
            } else {
                self
            }
        }
    }
}

// MARK: - A presentation's actions

/// Symbols allowed in the system's bar: only those clear without a title.
/// An action that needs a title stays a button in the content.
enum BarSymbol: String {
    case close = "xmark"
    case done = "checkmark"
    case start = "play.fill"
    case send = "paperplane"
}

/// A toolbar action: its title, and the symbol it shows in the system's bar, which holds symbols only.
struct BarAction {
    let title: LocalizedStringKey
    let symbol: BarSymbol
    var enabled = true
    let action: () -> Void

    init(_ title: LocalizedStringKey, _ symbol: BarSymbol, enabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.symbol = symbol
        self.enabled = enabled
        self.action = action
    }
}

extension View {
    /// A presentation's close, confirm and go actions as toolbar items in the system's order.
    /// In the system's vertical bar they show as symbols, close on top (HIG, Designing for iPhone Duo);
    /// elsewhere they keep their corners and titles, and `closeAsIcon` draws close as ✕.
    func presentationActions(close: BarAction? = nil, closeAsIcon: Bool = false,
                             confirm: BarAction? = nil, go: BarAction? = nil) -> some View {
        VerticalBarReader { inBar in
            self.modifier(PresentationActions(items: ActionItems(close: close, closeAsIcon: closeAsIcon,
                                                                 confirm: confirm, go: go, inBar: inBar)))
        }
    }
}

private struct PresentationActions: ViewModifier {
    let items: ActionItems

    func body(content: Content) -> some View {
        if #available(iOS 27.1, *) {
            // why: a titled item counts as horizontal-only and stays in the sheet's own bar.
            content.toolbar { items.axisBehavior(.verticalPreferred) }
        } else {
            content.toolbar { items }
        }
    }
}

private struct ActionItems: ToolbarContent {
    let close: BarAction?
    let closeAsIcon: Bool
    let confirm: BarAction?
    let go: BarAction?
    let inBar: Bool

    var body: some ToolbarContent {
        if let close {
            ToolbarItem(placement: .cancellationAction) { button(close, symbol: inBar || closeAsIcon) }
        }
        if let confirm {
            ToolbarItem(placement: .confirmationAction) { button(confirm, symbol: inBar) }
        }
        if let go {
            ToolbarItem(placement: .primaryAction) { button(go, symbol: inBar) }
        }
    }

    private func button(_ item: BarAction, symbol: Bool) -> some View {
        Button(action: item.action) {
            if symbol {
                Label(item.title, systemImage: item.symbol.rawValue).labelStyle(.iconOnly)
            } else {
                Text(item.title)
            }
        }
        .disabled(!item.enabled)
    }
}
