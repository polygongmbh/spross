import SwiftUI

/// The underline that marks accent-colored, background-less text as tappable —
/// the same affordance iOS's own "Show Borders" (née Button Shapes) accessibility
/// setting draws automatically, but only for SOME controls: `Button` and `Menu`
/// labels of identical shape get it inconsistently, since the system's own
/// heuristic doesn't treat the two alike. Reading the trait once here and
/// applying it by hand keeps every such label in step with the setting AND
/// with each other.
extension View {
    func linkAffordance() -> some View {
        modifier(LinkAffordance())
    }
}

private struct LinkAffordance: ViewModifier {
    @Environment(\.accessibilityShowButtonShapes) private var showButtonShapes

    func body(content: Content) -> some View {
        content.underline(showButtonShapes)
    }
}
