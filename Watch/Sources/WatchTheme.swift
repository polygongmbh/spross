import SwiftUI

// Watch design tokens — dark-mode variants of the phone palette
// (Theme.swift); watchOS renders on black, so only the dark set is needed.

extension Color {
    init(watchHex hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }

    static let wlAccent = Color(watchHex: 0xFF9A6B)   // clay
    static let wlAmber = Color(watchHex: 0xF2C078)    // ochre "again" — warm, never red
    static let wlTeal = Color(watchHex: 0x6FCFE8)     // ocean
    static let wlSuccess = Color(watchHex: 0x8AE39B)  // forest
    static let wlTextSecondary = Color(watchHex: 0xADBBAF)

    static let wlDer = Color(watchHex: 0x90CBFF)
    static let wlDie = Color(watchHex: 0xFF9EC0)
    static let wlDas = Color(watchHex: 0x6FDC85)
}

enum WL {
    /// Snapshot `articleTint` string → color (text carries meaning, color
    /// reinforces). The tint is the TARGET grammar gender pre-resolved by the
    /// phone; unknown/absent tints render neutral (genderless targets).
    /// A two-gender language folds onto the phone's two hues — masculine
    /// der-blue, feminine die-berry, plural and indefinite articles following
    /// the gender they inflect (`Theme.swift` holds the canonical list), so the
    /// watch never has to know which language the tint came from.
    static func articleColor(_ tint: String?) -> Color {
        switch tint?.lowercased() {
        case "der", "el", "los", "un": return .wlDer
        case "die", "la", "las", "una": return .wlDie
        case "das": return .wlDas
        default: return .white
        }
    }
}
