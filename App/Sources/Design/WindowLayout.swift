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
            .typeScaled()
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
            // why: a max, not a fixed width — a measurement a step behind the presentation's size
            // never lays the column out wider than it stands.
            content
                .frame(maxWidth: panes.single.columnWidth)
                .frame(maxWidth: panes.single.width)
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
        // why: the trailing pane ends at the safe area's edge, so the leading one keeps kern's margin.
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
    }
}

// MARK: - Sheets

extension View {
    /// A sheet measured for its own panes; where the system places sheets beside its bar,
    /// it stands on the trailing side, the bar keeping its controls.
    func paneSheet<Sheet: View>(isPresented: Binding<Bool>, onDismiss: (() -> Void)? = nil,
                                @ViewBuilder content: @escaping () -> Sheet) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            content().measuresWindowPanes().clearsWindowBars().trailingPresentation()
        }
    }

    func paneSheet<Item: Identifiable, Sheet: View>(item: Binding<Item?>, onDismiss: (() -> Void)? = nil,
                                                    @ViewBuilder content: @escaping (Item) -> Sheet) -> some View {
        sheet(item: item, onDismiss: onDismiss) {
            content($0).measuresWindowPanes().clearsWindowBars().trailingPresentation()
        }
    }
}

extension View {
    /// Keeps a sheet's content out of the window's own bars where the sheet stands over them
    /// without the system reporting them as its safe area — a trailing sheet moved beside the system's bar
    /// keeps the insets of where it was first laid out.
    fileprivate func clearsWindowBars() -> some View {
        modifier(ClearsWindowBars())
    }
}

private struct ClearsWindowBars: ViewModifier {
    /// The window's safe area, in the window's coordinates.
    @State private var windowSafeArea: CGRect?
    /// The sheet's own safe area, in the window's coordinates.
    @State private var safeArea: CGRect?

    private var covered: EdgeInsets {
        guard let windowSafeArea, let safeArea else { return EdgeInsets() }
        return EdgeInsets(top: 0, leading: max(0, windowSafeArea.minX - safeArea.minX),
                          bottom: 0, trailing: max(0, safeArea.maxX - windowSafeArea.maxX))
    }

    func body(content: Content) -> some View {
        content
            .safeAreaPadding(covered)
            // why: the sheet's move to its placement lays nothing out again, so only its geometry sees it.
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { safeArea = $0 }
            .background { WindowSafeAreaProbe { windowSafeArea = $0 }.ignoresSafeArea() }
    }
}

/// Reports the safe area of the window the view stands in.
private struct WindowSafeAreaProbe: UIViewRepresentable {
    let report: (CGRect) -> Void

    func makeUIView(context: Context) -> Probe { Probe() }

    func updateUIView(_ view: Probe, context: Context) {
        view.report = report
    }

    final class Probe: UIView {
        var report: ((CGRect) -> Void)?
        private var last: CGRect?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            measure()
        }

        override func safeAreaInsetsDidChange() {
            super.safeAreaInsetsDidChange()
            measure()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            measure()
        }

        private func measure() {
            guard let window else { return }
            let safeArea = window.bounds.inset(by: window.safeAreaInsets)
            guard safeArea != last else { return }
            last = safeArea
            // why: this runs inside a layout pass, where SwiftUI must not change state; it lands a turn later.
            DispatchQueue.main.async { [report] in report?(safeArea) }
        }
    }
}
