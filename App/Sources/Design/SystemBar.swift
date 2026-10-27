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

// MARK: - A presentation's actions

/// The symbols the system's bar may hold: only those that say what they do without a word.
/// An action that needs its words stays a titled button in the content.
enum BarSymbol: String {
    case close = "xmark"
    case done = "checkmark"
    case start = "play.fill"
    case send = "paperplane"
}

/// One action a presentation's toolbar offers: its title, and the symbol it is drawn as
/// in the system's bar, which holds symbols rather than words.
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
    /// A presentation's way out, its way through, its way on and its main way to finish, as toolbar items.
    /// Where the window has the system's vertical bar they stand in it as symbols, the way out on top
    /// and `finish` at the foot, prominent; elsewhere they keep their corners and titles —
    /// `closeAsIcon` draws the way out as a ✕ there too, and `finish` stays out, the content holding it.
    func presentationActions(close: BarAction? = nil, closeAsIcon: Bool = false,
                             confirm: BarAction? = nil, go: BarAction? = nil,
                             finish: BarAction? = nil) -> some View {
        VerticalBarReader { inBar in
            self.modifier(PresentationActions(items: ActionItems(close: close, closeAsIcon: closeAsIcon,
                                                                 confirm: confirm, go: go,
                                                                 finish: inBar ? finish : nil, inBar: inBar)))
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
    let finish: BarAction?
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
        if let finish {
            ToolbarItem(placement: .bottomBar) { prominent(button(finish, symbol: true)) }
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

    @ViewBuilder
    private func prominent(_ button: some View) -> some View {
        if #available(iOS 26.0, *) {
            button.buttonStyle(.glassProminent)
        } else {
            button.buttonStyle(.borderedProminent)
        }
    }
}
