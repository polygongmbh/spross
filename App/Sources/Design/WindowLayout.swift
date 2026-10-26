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

    /// A screen with no two-pane design of its own: one readable column in kern's `single` pane —
    /// centered where the window stays one pane, in the trailing half where it splits.
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
        if let panes, panes.split || panes.single.columnWidth < panes.single.width {
            content
                .frame(maxWidth: panes.single.columnWidth)
                .frame(width: panes.single.width)
                .frame(maxWidth: .infinity, alignment: panes.split ? .trailing : .center)
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
    /// A sheet measured for its own panes; where the system places sheets beside its bar,
    /// it stands on the trailing side, the bar keeping its controls.
    func paneSheet<Sheet: View>(isPresented: Binding<Bool>, onDismiss: (() -> Void)? = nil,
                                @ViewBuilder content: @escaping () -> Sheet) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            content().measuresWindowPanes().trailingPresentation()
        }
    }

    func paneSheet<Item: Identifiable, Sheet: View>(item: Binding<Item?>, onDismiss: (() -> Void)? = nil,
                                                    @ViewBuilder content: @escaping (Item) -> Sheet) -> some View {
        sheet(item: item, onDismiss: onDismiss) {
            content($0).measuresWindowPanes().trailingPresentation()
        }
    }
}
