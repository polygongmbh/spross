import SwiftUI
import UIKit

// MARK: - DuoLernen design tokens
//
// Warm, playful, card-centric — poster-derived, re-grounded on the growing-box
// theme: stone-and-moss paper, clay headline, ocean and forest as the
// secondaries. Zero dependencies, no asset catalog: every color adapts to
// light/dark through dynamic UIColor providers.
//
// Every pairing below clears WCAG AA — 4.5:1 for text, 3:1 for controls —
// in BOTH schemes. Two rules keep it that way:
//
// 1. Accents are cut at INK strength, not fill strength. The light-mode
//    values are dark enough to read as text on paper AND on their own 14 %
//    wash (the tinted-pill pattern), which is the tightest constraint;
//    a saturated fill of the same value still reads as its hue.
// 2. Text drawn ON an accent fill uses `dlOnColor`, never `.white` — dark
//    mode's accents are pastels, where white sinks to ~1.8:1.

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

    // Surfaces — stone paper with a moss cast, never plain white/gray.
    static let dlBackground = Color(light: 0xF2F1EA, dark: 0x121714)
    static let dlSurface = Color(light: 0xFBFBF6, dark: 0x1C231E)
    static let dlSurfaceTint = Color(light: 0xE5E8DE, dark: 0x27302A)
    /// Decorative hairline — card edges, the reveal divider, the ring groove.
    /// Deliberately below 3:1: the card's fill and shadow carry its boundary.
    static let dlSeparator = Color(light: 0xD3D6CA, dark: 0x3A443D)
    /// A line that must be SEEN — the answer field's edge is a control
    /// boundary, so it owes 3:1 where the decorative hairline does not.
    static let dlBorderStrong = Color(light: 0x868D7C, dark: 0x707C72)

    // Text — deep forest ink instead of pure black/gray.
    static let dlTextPrimary = Color(light: 0x1E2620, dark: 0xE9F0EA)
    static let dlTextSecondary = Color(light: 0x4F584E, dark: 0xADBBAF)
    /// Text/glyphs drawn ON a saturated accent fill (buttons, article pills).
    static let dlOnColor = Color(light: 0xFBFBF6, dark: 0x121714)

    // Accents — ink strength (see the header note).
    static let dlAccent = Color(light: 0xA23B0B, dark: 0xFF9A6B)   // clay
    static let dlTeal = Color(light: 0x0D566E, dark: 0x6FCFE8)     // ocean
    static let dlSuccess = Color(light: 0x256232, dark: 0x8AE39B)  // forest
    static let dlAmber = Color(light: 0x87510A, dark: 0xF2C078)    // ochre "reveal", never red
    // why: progress segments show wrong answers on explicit user request —
    // a muted brick, only in the aggregate bar, never as card feedback.
    static let dlWrong = Color(light: 0x99322E, dark: 0xF08D86)

    // Article colors (poster palette).
    static let dlDer = Color(light: 0x134E85, dark: 0x90CBFF)
    static let dlDie = Color(light: 0x9A2050, dark: 0xFF9EC0)
    static let dlDas = Color(light: 0x18602C, dark: 0x6FDC85)
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
            .foregroundStyle(Color.dlOnColor)
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
            Text("preview.tokens")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)

            HStack(spacing: DL.Space.s) {
                ForEach(["der", "die", "das"], id: \.self) { article in
                    Text(article)
                        .font(DL.Fonts.badge)
                        .foregroundStyle(Color.dlOnColor)
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

            Button("common.next") {}
                .buttonStyle(DLPrimaryButtonStyle())
            Button("preview.skip") {}
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
