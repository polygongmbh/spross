import SwiftUI

// Watch design tokens — dark-mode variants of the phone palette
// (Theme.swift); watchOS renders on black, so only the dark set is needed.

extension Color {
    init(watchHex hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }

    static let wlAccent = Color(watchHex: 0xFF9A62)   // terracotta
    static let wlAmber = Color(watchHex: 0xFFC078)    // "again" — warm, never red
    static let wlTeal = Color(watchHex: 0x3BC9DB)
    static let wlSuccess = Color(watchHex: 0x8CE99A)
    static let wlTextSecondary = Color(watchHex: 0xA79883)

    static let wlDer = Color(watchHex: 0x74C0FC)
    static let wlDie = Color(watchHex: 0xF783AC)
    static let wlDas = Color(watchHex: 0x69DB7C)
}

enum WL {
    /// Article → color (text carries meaning, color reinforces).
    static func articleColor(_ article: String?) -> Color {
        switch article?.lowercased() {
        case "der": return .wlDer
        case "die": return .wlDie
        case "das": return .wlDas
        default: return .white
        }
    }
}
