import SwiftUI
import UIKit

// MARK: - DuoLernen design tokens
//
// Warm, playful, card-centric — palette derived from the Sprachposter pages
// (cream paper, terracotta headers, peach tiles, colored article pills).
// Zero dependencies, no asset catalog: every color adapts to light/dark
// through dynamic UIColor providers.

enum DL {

    // MARK: Spacing (pt)

    enum Space {
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let l: CGFloat = 16
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
    }

    // MARK: Corner radius family (one family, three sizes)

    enum Radius {
        /// Hero cards (review card, completion card).
        static let card: CGFloat = 28
        /// Stat tiles, chips, inline panels.
        static let tile: CGFloat = 20
        /// Buttons, text fields, small controls.
        static let control: CGFloat = 14
    }

    // MARK: Type scale — SF Rounded throughout

    enum Fonts {
        static let hero = Font.system(.largeTitle, design: .rounded, weight: .bold)
        static let title = Font.system(.title2, design: .rounded, weight: .bold)
        static let headline = Font.system(.headline, design: .rounded, weight: .semibold)
        static let body = Font.system(.body, design: .rounded)
        static let subheadline = Font.system(.subheadline, design: .rounded)
        static let caption = Font.system(.caption, design: .rounded, weight: .medium)
        static let badge = Font.system(.footnote, design: .rounded, weight: .bold)
        static let statValue = Font.system(.title, design: .rounded, weight: .bold)
    }

    /// Article → color. Text always carries the meaning; color only reinforces.
    static func articleColor(_ article: String) -> Color {
        switch article.lowercased() {
        case "der": return .dlDer
        case "die": return .dlDie
        case "das": return .dlDas
        default: return .dlTextSecondary
        }
    }
}

// MARK: - Adaptive colors

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension Color {
    /// Asset-free adaptive color: hex for light mode, hex for dark mode.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }

    // Surfaces — warm cream, never plain white/gray.
    static let dlBackground = Color(light: 0xFAF3E8, dark: 0x201A15)
    static let dlSurface = Color(light: 0xFFFBF5, dark: 0x2B241D)
    static let dlSurfaceTint = Color(light: 0xFBEDDC, dark: 0x33291F)
    static let dlSeparator = Color(light: 0xE7D9C6, dark: 0x3E362C)

    // Text — warm browns instead of pure black/gray.
    static let dlTextPrimary = Color(light: 0x3D2C23, dark: 0xF2E7D8)
    static let dlTextSecondary = Color(light: 0x8A7767, dark: 0xA79883)

    // Accents.
    static let dlAccent = Color(light: 0xE8590C, dark: 0xFF9A62)   // terracotta
    static let dlTeal = Color(light: 0x0C8599, dark: 0x3BC9DB)
    static let dlSuccess = Color(light: 0x2F9E44, dark: 0x8CE99A)
    static let dlAmber = Color(light: 0xE8890C, dark: 0xFFC078)    // warm "reveal", never red

    // Article colors (poster palette).
    static let dlDer = Color(light: 0x1971C2, dark: 0x74C0FC)
    static let dlDie = Color(light: 0xC2255C, dark: 0xF783AC)
    static let dlDas = Color(light: 0x1E7A32, dark: 0x69DB7C)
}

// MARK: - Shared modifiers & button styles

extension View {
    /// The one card shadow used everywhere.
    func dlCardShadow() -> some View {
        shadow(color: .black.opacity(0.08), radius: 16, x: 0, y: 6)
    }
}

/// Filled terracotta primary action. Never a default gray Button.
struct DLPrimaryButtonStyle: ButtonStyle {
    var color: Color = .dlAccent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(DL.Fonts.headline)
            .foregroundStyle(Color(light: 0xFFFFFF, dark: 0x201A15))
            .padding(.vertical, DL.Space.l)
            .padding(.horizontal, DL.Space.xl)
            .frame(minHeight: 52)
            .background(color, in: RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Soft tinted secondary action (colored text on a translucent tint).
struct DLSoftButtonStyle: ButtonStyle {
    var color: Color = .dlAccent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(DL.Fonts.headline)
            .foregroundStyle(color)
            .padding(.vertical, DL.Space.m)
            .padding(.horizontal, DL.Space.l)
            .frame(minHeight: 44)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous))
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

// MARK: - Preview

#Preview("Palette") {
    ScrollView {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("DuoLernen Tokens")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)

            HStack(spacing: DL.Space.s) {
                ForEach(["der", "die", "das"], id: \.self) { article in
                    Text(article)
                        .font(DL.Fonts.badge)
                        .foregroundStyle(.white)
                        .padding(.horizontal, DL.Space.m)
                        .padding(.vertical, DL.Space.xs + 2)
                        .background(DL.articleColor(article), in: Capsule())
                }
            }

            VStack(spacing: DL.Space.s) {
                swatch("Accent (Terracotta)", .dlAccent)
                swatch("Teal", .dlTeal)
                swatch("Success", .dlSuccess)
                swatch("Amber (Reveal)", .dlAmber)
                swatch("Surface", .dlSurface)
                swatch("Surface Tint", .dlSurfaceTint)
            }

            Button("Weiter") {}
                .buttonStyle(DLPrimaryButtonStyle())
            Button("Überspringen") {}
                .buttonStyle(DLSoftButtonStyle(color: .dlTeal))
        }
        .padding(DL.Space.xl)
    }
    .background(Color.dlBackground)
}

private func swatch(_ name: String, _ color: Color) -> some View {
    HStack {
        RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
            .fill(color)
            .frame(width: 56, height: 36)
        Text(name)
            .font(DL.Fonts.body)
            .foregroundStyle(Color.dlTextPrimary)
        Spacer()
    }
}
