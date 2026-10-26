import SprossKern
import SwiftUI

// MARK: - The window's panes
//
// Whether a screen stands in one column or two panes is kern's (`WindowPanes`), from the width
// a presentation is given. Each presentation root measures itself once (`measuresWindowPanes`)
// and hands the decision down; the screens read it and lay their parts out.

extension EnvironmentValues {
    /// The presentation's layout as kern decides it; nil until it has been measured.
    @Entry var windowPanes: WindowPanes?
}

extension WindowPanes {
    var leading: Pane { panes[0] }
    var trailing: Pane { panes[panes.count - 1] }
    /// The space between the two panes; nothing in a single column.
    var gutter: CGFloat { CGFloat(trailing.frame.start - leading.frame.end) }
}

extension WindowPanes.Pane {
    var width: CGFloat { CGFloat(frame.width) }
    var columnWidth: CGFloat { CGFloat(column.width) }
}

/// Measures the width it is laid out in and hands kern's decision to `content` and the environment.
struct PanesReader<Content: View>: View {
    @ViewBuilder var content: (WindowPanes?) -> Content
    @State private var panes: WindowPanes?

    private struct Measure: Equatable {
        var width: CGFloat
        var leading: CGFloat
        var trailing: CGFloat
    }

    var body: some View {
        content(panes)
            .environment(\.windowPanes, panes)
            // why: the proxy's size already stops at the safe area and its insets lie outside it,
            // so the window is the two together.
            .onGeometryChange(for: Measure.self) { proxy in
                Measure(width: proxy.size.width,
                        leading: proxy.safeAreaInsets.leading, trailing: proxy.safeAreaInsets.trailing)
            } action: { measure in
                panes = WindowPanes(windowWidth: Double(measure.width + measure.leading + measure.trailing),
                                    leadingInset: Double(measure.leading),
                                    trailingInset: Double(measure.trailing))
            }
    }
}

extension View {
    /// The root of a presentation — a tab, a cover, a sheet — measures the panes for everything under it.
    func measuresWindowPanes() -> some View {
        PanesReader { _ in self }
    }

    /// A screen with no two-pane design of its own: one readable column —
    /// centered where the window stays one pane, in the leading pane where it splits.
    /// A phone passes through untouched.
    func readableColumn() -> some View {
        modifier(ReadableColumn())
    }

    /// `readableColumn` for a root that is its own presentation, measured where it stands.
    func measuredReadableColumn() -> some View {
        PanesReader { _ in self.readableColumn() }
    }
}

private struct ReadableColumn: ViewModifier {
    @Environment(\.windowPanes) private var panes

    func body(content: Content) -> some View {
        if let panes, panes.split || panes.leading.columnWidth < panes.leading.width {
            content
                .frame(maxWidth: panes.leading.columnWidth)
                .frame(width: panes.leading.width)
                .frame(maxWidth: .infinity, alignment: panes.split ? .leading : .center)
        } else {
            content
        }
    }
}

// MARK: - Two panes

/// The two panes side by side, each holding its readable column. Only for a split window;
/// a screen keeps its phone layout where `WindowPanes.split` is false.
struct PaneSplit<Leading: View, Trailing: View>: View {
    let panes: WindowPanes
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .top, spacing: panes.gutter) {
            leading
                .frame(maxWidth: panes.leading.columnWidth, maxHeight: .infinity)
                .frame(width: panes.leading.width)
            trailing
                .frame(maxWidth: panes.trailing.columnWidth, maxHeight: .infinity)
                .frame(width: panes.trailing.width)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Sheets

extension View {
    /// A sheet on a phone; a full-screen page in the leading pane where the window splits —
    /// a form sheet centers on the window, which on a foldable is the hinge.
    /// Every such sheet carries its own way out, so the cover loses nothing the swipe gave.
    /// `ownsPanes`: the sheet lays out both panes itself (`OnboardingView`) and gets the whole window.
    func paneSheet<Sheet: View>(isPresented: Binding<Bool>, onDismiss: (() -> Void)? = nil, ownsPanes: Bool = false,
                                @ViewBuilder content: @escaping () -> Sheet) -> some View {
        modifier(PaneSheet(isPresented: isPresented, onDismiss: onDismiss, ownsPanes: ownsPanes, sheet: content))
    }

    func paneSheet<Item: Identifiable, Sheet: View>(item: Binding<Item?>, onDismiss: (() -> Void)? = nil,
                                                    @ViewBuilder content: @escaping (Item) -> Sheet) -> some View {
        let shown = Binding(get: { item.wrappedValue != nil },
                            set: { if !$0 { item.wrappedValue = nil } })
        return paneSheet(isPresented: shown, onDismiss: onDismiss) {
            if let open = item.wrappedValue { content(open) }
        }
    }
}

private struct PaneSheet<Sheet: View>: ViewModifier {
    let isPresented: Binding<Bool>
    let onDismiss: (() -> Void)?
    let ownsPanes: Bool
    let sheet: () -> Sheet
    @Environment(\.windowPanes) private var panes

    func body(content: Content) -> some View {
        let split = panes?.split == true
        // why: both presentations always attached, each gated — swapping modifiers would
        // rebuild the presenting screen and drop its state the moment the window is measured.
        // Neither opens before that measurement: a sheet already up is never moved into the cover.
        content
            .sheet(isPresented: gated(panes != nil && !split), onDismiss: onDismiss) {
                sheet().measuresWindowPanes()
            }
            .fullScreenCover(isPresented: gated(split), onDismiss: onDismiss) {
                Group {
                    if ownsPanes { sheet() } else { sheet().readableColumn() }
                }
                .measuresWindowPanes()
                    .background(Theme.colors.background.ignoresSafeArea())
            }
    }

    private func gated(_ open: Bool) -> Binding<Bool> {
        Binding(get: { open && isPresented.wrappedValue }, set: { isPresented.wrappedValue = $0 })
    }
}
