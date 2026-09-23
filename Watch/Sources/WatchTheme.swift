import SwiftUI

// Watch design tokens — the dark column of the canonical table in
// `App/Sources/Design/Theme.swift`, copied because the watch target links neither the
// app's design tokens nor Kotlin. kern's `PaletteParityTest` fails the fast gate when
// this copy drifts from it. watchOS renders on black, so only the dark set is needed.

extension Color {
    init(watchHex hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }
}

enum WatchTheme {

    static let colors = Colors()

    struct Colors {
        let accent = Color(watchHex: 0xFF9A6B)   // clay
        let teal = Color(watchHex: 0x6FCFE8)     // ocean
        let success = Color(watchHex: 0x8AE39B)  // forest
        // why: the phone keeps wrong off its cards, but a glance-long wrist answer
        // has no second face to correct on — so the miss is red here, on the tile
        // and in the wash behind it. Mirrors the phone's dark `Theme.colors.wrong`
        // rather than minting an alarm hue of its own.
        let wrong = Color(watchHex: 0xF08D86)    // brick
        let textSecondary = Color(watchHex: 0xADBBAF)

        let der = Color(watchHex: 0x90CBFF)
        let die = Color(watchHex: 0xFF9EC0)
        let das = Color(watchHex: 0x6FDC85)
    }

    /// The hue an entry's gender wears (text carries meaning, color reinforces);
    /// a box that names no gender renders neutral. Which article marks which
    /// gender the phone already settled (`SnapshotGender`).
    static func genderColor(_ gender: SnapshotGender?) -> Color {
        switch gender {
        case .masculine: return colors.der
        case .feminine: return colors.die
        case .neuter: return colors.das
        case nil: return .white
        }
    }
}
