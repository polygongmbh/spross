import SprossKern
import SwiftUI

// MARK: - The window's panes
//
// Kern decides one column or two panes (`WindowPanes`) from a presentation's width.
// Each presentation root measures once (`measuresWindowPanes`) and passes the result down the environment.

extension EnvironmentValues {
    /// Kern's layout for the presentation; nil until measured.
    @Entry var windowPanes: WindowPanes?
}

extension WindowPanes {
    var leading: Pane { panes[0] }
    var trailing: Pane { panes[panes.count - 1] }
    /// The gap between the panes; zero in a single column.
    var gutter: CGFloat { CGFloat(trailing.frame.start - leading.frame.end) }
}

extension WindowPanes.Pane {
    var width: CGFloat { CGFloat(frame.width) }
    var columnWidth: CGFloat { CGFloat(column.width) }
}

/// Measures its width and the hinge, and passes kern's panes to `content` and the environment.
struct PanesReader<Content: View>: View {
    @ViewBuilder var content: (WindowPanes?) -> Content
    @State private var measure: PaneMeasure?

    var body: some View {
        HingeReader { halfOpen in
            let panes = measure.map {
                WindowPanes(windowWidth: Double($0.width + $0.leading + $0.trailing),
                            leadingInset: Double($0.leading), trailingInset: Double($0.trailing),
                            halfOpen: halfOpen)
            }
            content(panes)
                .environment(\.windowPanes, panes)
                .typeScaled()
        }
        // The proxy's size stops at the safe area, so the window is its width plus the insets.
        .onGeometryChange(for: PaneMeasure.self) { proxy in
            PaneMeasure(width: proxy.size.width,
                        leading: proxy.safeAreaInsets.leading, trailing: proxy.safeAreaInsets.trailing)
        } action: { measure = $0 }
    }
}

// Outside the generic reader, so the geometry closure captures no `Content` metatype.
private struct PaneMeasure: Equatable {
    var width: CGFloat
    var leading: CGFloat
    var trailing: CGFloat
}

extension View {
    /// Measures the panes for everything under a presentation root (a tab, a cover, a sheet).
    func measuresWindowPanes() -> some View {
        PanesReader { _ in self }
    }

    /// One readable column in kern's `single` pane, for a screen without a two-pane layout:
    /// centered, or in the trailing half of a half-open foldable. Unchanged on a phone.
    func readableColumn() -> some View {
        modifier(ReadableColumn())
    }

    /// `readableColumn` for a presentation root, which measures itself.
    func measuredReadableColumn() -> some View {
        PanesReader { _ in self.readableColumn() }
    }
}

private struct ReadableColumn: ViewModifier {
    @Environment(\.windowPanes) private var panes

    func body(content: Content) -> some View {
        let inHalf = panes.map { $0.split && $0.single.width == $0.trailing.width } ?? false
        if let panes, inHalf || panes.single.columnWidth < panes.single.width {
            // Max widths, so a measurement lagging the presentation's size never widens the column past its space.
            content
                .frame(maxWidth: panes.single.columnWidth)
                .frame(maxWidth: panes.single.width)
                .frame(maxWidth: .infinity, alignment: inHalf ? .trailing : .center)
        } else {
            content
        }
    }
}

// MARK: - Two panes

/// The two panes side by side, each with its readable column; only where `WindowPanes.split` holds.
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
    /// A sheet that measures its own panes; on a half-open foldable it stands on the trailing side, beside the system's bar.
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
    /// Pads a sheet's content clear of the window's bars that the system leaves out of the sheet's safe area:
    /// a sheet moved to the trailing side keeps the insets of where it was first laid out.
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
            // why: this runs in a layout pass, where SwiftUI state must not change.
            DispatchQueue.main.async { [report] in report?(safeArea) }
        }
    }
}
