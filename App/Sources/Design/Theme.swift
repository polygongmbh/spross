import SwiftUI
import UIKit
import SprossKern

// MARK: - Spross design tokens
//
// Warm, playful, card-centric — poster-derived, re-grounded on the growing-box
// theme: stone-and-moss paper, clay headline, ocean and forest as the
// secondaries. No asset catalog: every color adapts to light/dark through a
// dynamic UIColor provider, over the hex pairs kern's `Palette` owns.
//
// Groups read as lowercase properties off `Theme` — `Theme.spacing.lg`,
// `Theme.colors.accent` — the shape Material 3 and the W3C design-token format
// both use, so the Android side spells every token identically.
//
// Every pairing below clears WCAG AA — 4.5:1 for text, 3:1 for controls —
// in BOTH schemes. Two rules keep it that way:
//
// 1. Accents are cut at INK strength, not fill strength. The light-mode
//    values are dark enough to read as text on paper AND on their own 14 %
//    wash (the tinted-pill pattern), which is the tightest constraint;
//    a saturated fill of the same value still reads as its hue.
// 2. Text drawn ON an accent fill uses `Theme.colors.onColor`, never `.white` —
//    dark mode's accents are pastels, where white sinks to ~1.8:1.

enum Theme {

    static let spacing = Spacing()
    static let radius = Radius()
    static let reserve = Reserve()
    static let typography = Typography()
    static let prompt = Prompt()
    static let colors = Colors()

    // MARK: Spacing (pt)

    struct Spacing {
        let xs: CGFloat = 4
        let sm: CGFloat = 8
        let md: CGFloat = 12
        let lg: CGFloat = 16
        let xl: CGFloat = 24
    }

    // MARK: Corner radius family (one family, three sizes)

    struct Radius {
        /// Hero cards (review card, completion card).
        let card: CGFloat = 28
        /// Stat tiles, chips, inline panels.
        let tile: CGFloat = 20
        /// Buttons, text fields, small controls.
        let control: CGFloat = 14
    }

    // MARK: Reserved heights (pt)

    /// A session prompt card reserves the height of its own tallest ROUTINE
    /// state, so nothing below it moves as optional content comes and goes —
    /// vertical space is the scarce axis (card, input, button and keyboard
    /// share one screen), and a button that slides under the keyboard costs
    /// more than a card with air in it. The tallest routine state, not the
    /// tallest possible one: a rare face that overflows simply grows the card,
    /// because levelling every card up to an exception buys stillness with air.
    /// The reveal is exempt — it grows the card downward and reserves nothing.
    struct Reserve {
        /// Drill prompt, shared by both drill faces: one 56 pt line of digits
        /// plus the place-value pill (141.3 pt measured), and the listening
        /// card's caption plus replay glyph plus its once-per-run silent-switch
        /// line. A gap word ("Ge l＿") is the exception that grows the card —
        /// levelling every question up to it would buy stillness with air.
        let drillCard: CGFloat = 144
        /// Review prompt (compact): the by-ear prompt's 88 pt replay target,
        /// which is the tallest a prompt side gets — a word asked by ear and
        /// the same word asked by meaning then measure alike.
        let reviewCard: CGFloat = 120
        /// A tappable tile's floor — a glyph over its label, at a size a thumb
        /// finds without aiming. The letter drill's choices and the hub's entry
        /// chips are one size, so a tile never reads as two kinds of target.
        let tile: CGFloat = 72
    }

    // MARK: Type scale — SF Rounded throughout

    struct Typography {
        let hero = Font.system(.largeTitle, design: .rounded, weight: .bold)
        let title = Font.system(.title2, design: .rounded, weight: .bold)
        let headline = Font.system(.headline, design: .rounded, weight: .semibold)
        let body = Font.system(.body, design: .rounded)
        let subheadline = Font.system(.subheadline, design: .rounded)
        let caption = Font.system(.caption, design: .rounded, weight: .medium)
        let badge = Font.system(.footnote, design: .rounded, weight: .bold)

    }

    // MARK: The question's own sizes

    /// The QUESTION itself, set as large as its card can hold.
    ///
    /// Fixed points rather than Dynamic Type: a prompt card reserves a height
    /// (`Theme.reserve.drillCard`), and a prompt that grew with the reader's setting
    /// would push the field below it off the screen that reserve exists to hold
    /// still. WHAT is asked picks the size — there is room for one numeral where
    /// there is none for a whole sentence — and every face steps down to fit
    /// rather than taking a size of its own.
    struct Prompt {
        /// A numeral the whole card is about ("1 978", "14:35").
        let digits = Font.system(size: 56, weight: .bold, design: .rounded)
        /// A picture standing where the name would: a flag that IS the question.
        let glyph = Font.system(size: 64)
        /// One word with a blank in it ("Ge l＿").
        let word = Font.system(size: 40, weight: .bold, design: .rounded)
        /// A name asked about ("Deutschland") — words run longer than numerals.
        let name = Font.system(size: 32, weight: .bold, design: .rounded)
        /// A prompt made of words, laid out like one: wrapped over lines.
        let sentence = Font.system(size: 28, weight: .bold, design: .rounded)
    }

    // MARK: Adaptive colors

    /// The box's own color table, as the SwiftUI colors it paints. The hex pairs
    /// are kern's (`design/Palette.kt`) — the app links it, so it reads the values
    /// rather than keeping a second copy that can drift.
    struct Colors {
        // Surfaces — stone paper with a moss cast, never plain white/gray.
        let background = Color(Palette.shared.background)
        let surface = Color(Palette.shared.surface)
        let surfaceTint = Color(Palette.shared.surfaceTint)
        /// Decorative hairline — card edges, the reveal divider, the ring groove.
        /// Deliberately below 3:1: the card's fill and shadow carry its boundary.
        let separator = Color(Palette.shared.separator)
        /// A line that must be SEEN — the answer field's edge is a control
        /// boundary, so it owes 3:1 where the decorative hairline does not.
        let borderStrong = Color(Palette.shared.borderStrong)

        // Text — deep forest ink instead of pure black/gray.
        let textPrimary = Color(Palette.shared.textPrimary)
        let textSecondary = Color(Palette.shared.textSecondary)
        /// Text/glyphs drawn ON a saturated accent fill (buttons, article pills).
        let onColor = Color(Palette.shared.onColor)

        // Accents — ink strength (see the header note).
        let accent = Color(Palette.shared.accent)    // clay
        let teal = Color(Palette.shared.teal)        // ocean
        let success = Color(Palette.shared.success)  // forest
        let amber = Color(Palette.shared.amber)      // ochre — a near miss, or an answer shown
        let wrong = Color(Palette.shared.wrong)      // brick — a miss
        /// The consolidated/"grown" Sprosse's own color — not `teal`, which sits too close to
        /// `der` on the hue wheel to read as anything but another blue at a badge's size.
        let grown = Color(Palette.shared.grown)      // jade

        // Article colors (poster palette).
        let der = Color(Palette.shared.der)
        let die = Color(Palette.shared.die)
        let das = Color(Palette.shared.das)
    }

    /// Gender → color. Text always carries the meaning; color only reinforces.
    ///
    /// A two-gender language folds onto the SAME two hues rather than minting its
    /// own: masculine reads der-blue, feminine die-berry. Green is German's neuter
    /// alone — a language without one simply never reaches it — and a word whose
    /// gender the box cannot name degrades to neutral, exactly as a genderless
    /// target already does. Widget and watch surfaces mirror this mapping in their
    /// own palettes. WHICH article marks which gender is not decided here:
    /// that list is `kern/model/Article.kt`.
    static func genderColor(_ gender: DLGender?) -> Color {
        switch gender {
        case .masculine: return colors.der
        case .feminine: return colors.die
        case .neuter: return colors.das
        case nil: return colors.textSecondary
        }
    }
}

/// The grammatical gender a word's article marks, as this palette reads it.
/// The Design-local twin of the box's `Gender`, so components stay kern-free.
enum DLGender {
    case masculine, feminine, neuter
}

/// An article as a card face shows it: the word itself and the gender it marks,
/// as ONE value so the two can never disagree. The screen resolves the gender
/// when it builds the face — the design system only paints it.
struct DLArticle {
    let text: String
    let gender: DLGender?

    init(_ text: String, gender: DLGender?) {
        self.text = text
        self.gender = gender
    }
}

// MARK: - Color construction

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

    /// One entry of the box's own color table, as the SwiftUI color it paints.
    /// The hex pairs are kern's (`design/Palette.kt`) — the app links it, so it
    /// reads the values rather than keeping a second copy that can drift; the
    /// `Color` type and the light/dark provider below it stay native.
    init(_ swatch: Swatch) {
        self.init(light: UInt32(bitPattern: swatch.light), dark: UInt32(bitPattern: swatch.dark))
    }
}

// MARK: - Shared modifiers & button styles

extension View {
    /// The one card shadow used everywhere.
    func dlCardShadow() -> some View {
        shadow(color: .black.opacity(0.08), radius: 16, x: 0, y: 6)
    }

    /// The one card FACE: surface fill, hairline, shadow. Every card a session
    /// puts up — vocabulary, drill prompt, listening prompt — wears this, so a
    /// screen never shows two cards cut from different cloth.
    func dlCardSurface() -> some View {
        background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .strokeBorder(Theme.colors.separator.opacity(0.6), lineWidth: 1)
        )
        .dlCardShadow()
    }
}

/// Filled terracotta primary action. Never a default gray Button.
struct DLPrimaryButtonStyle: ButtonStyle {
    var color: Color = Theme.colors.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.typography.headline)
            .foregroundStyle(Theme.colors.onColor)
            .padding(.vertical, Theme.spacing.lg)
            .padding(.horizontal, Theme.spacing.xl)
            .frame(minHeight: 52)
            .background(color, in: RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Soft tinted secondary action (colored text on a translucent tint).
struct DLSoftButtonStyle: ButtonStyle {
    var color: Color = Theme.colors.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.typography.headline)
            .foregroundStyle(color)
            .padding(.vertical, Theme.spacing.md)
            .padding(.horizontal, Theme.spacing.lg)
            .frame(minHeight: 44)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous))
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Compact icon-only action: one glyph on a round tint, sized for a thumb.
struct DLIconButtonStyle: ButtonStyle {
    var color: Color = Theme.colors.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.typography.headline)
            .foregroundStyle(color)
            .frame(width: 40, height: 40)
            .background(color.opacity(0.14), in: Circle())
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.9 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

// MARK: - Preview

#Preview("Palette") {
    ScrollView {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            Text("preview.tokens")
                .font(Theme.typography.hero)
                .foregroundStyle(Theme.colors.textPrimary)

            HStack(spacing: Theme.spacing.sm) {
                let articles: [DLArticle] = [
                    .init("der", gender: .masculine), .init("die", gender: .feminine),
                    .init("das", gender: .neuter), .init("el", gender: .masculine),
                    .init("la", gender: .feminine),
                ]
                ForEach(articles, id: \.text) { article in
                    Text(article.text)
                        .font(Theme.typography.badge)
                        .foregroundStyle(Theme.colors.onColor)
                        .padding(.horizontal, Theme.spacing.md)
                        .padding(.vertical, Theme.spacing.xs + 2)
                        .background(Theme.genderColor(article.gender), in: Capsule())
                }
            }

            VStack(spacing: Theme.spacing.sm) {
                swatch("Accent (Terracotta)", Theme.colors.accent)
                swatch("Teal", Theme.colors.teal)
                swatch("Success", Theme.colors.success)
                swatch("Amber (Reveal)", Theme.colors.amber)
                swatch("Surface", Theme.colors.surface)
                swatch("Surface Tint", Theme.colors.surfaceTint)
            }

            Button("common.next") {}
                .buttonStyle(DLPrimaryButtonStyle())
            Button("preview.skip") {}
                .buttonStyle(DLSoftButtonStyle(color: Theme.colors.teal))
            HStack(spacing: Theme.spacing.md) {
                Button { } label: { Image(systemName: "plus") }
                    .buttonStyle(DLIconButtonStyle())
                Button { } label: { Image(systemName: "speaker.wave.2.fill") }
                    .buttonStyle(DLIconButtonStyle(color: Theme.colors.teal))
            }
        }
        .padding(Theme.spacing.xl)
    }
    .background(Theme.colors.background)
}

private func swatch(_ name: String, _ color: Color) -> some View {
    HStack {
        RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
            .fill(color)
            .frame(width: 56, height: 36)
        Text(name)
            .font(Theme.typography.body)
            .foregroundStyle(Theme.colors.textPrimary)
        Spacer()
    }
}
