import SprossKern
import SwiftUI

// MARK: - Type on a big window
//
// How much larger type stands is kern's (`TypeScale`), from the window's size. The window's
// root measures it once (`measuresWindowType`); every presentation root applies the app's
// factor and a session's scaffold its own, each counted from the reader's own Dynamic Type
// size, so the steps never stack on one another.

extension EnvironmentValues {
    /// The window's type scale as kern decides it; nil until the window has been measured.
    @Entry var typeScale: TypeScale?
    /// The reader's own Dynamic Type size, before any window scale stepped it up.
    @Entry var readerTypeSize: DynamicTypeSize?
    /// The question's sizes at the scale they stand in.
    @Entry var prompt = Theme.Prompt()
}

extension View {
    /// The window's root measures its size for the type scale everything under it reads.
    func measuresWindowType() -> some View {
        modifier(WindowTypeReader())
    }

    /// Type at the window's app scale, or at its session scale for a session's card.
    func typeScaled(session: Bool = false) -> some View {
        modifier(TypeScaled(session: session))
    }
}

private struct WindowTypeReader: ViewModifier {
    @State private var scale: TypeScale?

    func body(content: Content) -> some View {
        content
            .typeScaled()
            .environment(\.typeScale, scale)
            // why: the proxy's size stops at the safe area, so the window is it and its insets together.
            .onGeometryChange(for: CGSize.self) { proxy in
                let insets = proxy.safeAreaInsets
                return CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            } action: { size in
                scale = TypeScale(windowWidth: Double(size.width), windowHeight: Double(size.height))
            }
    }
}

private struct TypeScaled: ViewModifier {
    let session: Bool
    @Environment(\.typeScale) private var scale
    @Environment(\.readerTypeSize) private var reader
    @Environment(\.dynamicTypeSize) private var current

    func body(content: Content) -> some View {
        let base = reader ?? current
        let factor = scale.map { session ? $0.session : $0.app } ?? TypeScale.companion.NORMAL
        content
            .dynamicTypeSize(base.stepped(by: factor))
            .environment(\.readerTypeSize, base)
            .environment(\.prompt, Theme.Prompt(scale: CGFloat(factor)))
    }
}

private extension DynamicTypeSize {
    /// One step on Apple's ladder enlarges body text by about this much (17 → 19 → 21 → 23 pt).
    static let stepFactor = 1.115

    /// The size `factor` larger, to the nearest step, no larger than the ladder goes.
    func stepped(by factor: Double) -> DynamicTypeSize {
        let steps = Int((log(factor) / log(Self.stepFactor)).rounded())
        guard steps > 0, let index = Self.allCases.firstIndex(of: self) else { return self }
        return Self.allCases[min(index + steps, Self.allCases.count - 1)]
    }
}
