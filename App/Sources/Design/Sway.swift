import SwiftUI

// MARK: - Sway
//
// An endless, barely-there rock. It is what keeps a celebration screen from
// settling into a still frame the moment its entrance animation lands.
//
// Only ever applied to decoration — emoji, never text or controls: a thing
// that carries meaning has to hold still to be read. Nothing here changes
// layout, so a swaying piece never nudges its neighbors.

private struct DLSway: ViewModifier {
    let angle: Double
    let period: Double

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Set once and left set: repeatForever needs exactly one change to start.
    @State private var swaying = false

    func body(content: Content) -> some View {
        content
            .rotationEffect(.degrees(swaying ? angle : -angle))
            .animation(
                reduceMotion ? nil
                    : .easeInOut(duration: period).repeatForever(autoreverses: true),
                value: swaying
            )
            .onAppear { swaying = true }
    }
}

extension View {
    /// Rocks between ±`angle` forever. Give neighboring pieces different
    /// periods — one shared clock reads as a single rigid object rocking,
    /// which is the opposite of several light things hanging in the air.
    /// Reduce Motion drops it entirely: it is decoration that never stops.
    func dlSway(angle: Double = 5, period: Double = 2.6) -> some View {
        modifier(DLSway(angle: angle, period: period))
    }
}
