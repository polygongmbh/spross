import SwiftUI
import SprossKern

// MARK: - Shared modifiers & button styles

extension View {
    /// The one card FACE: the surface fill on the page, flat as iOS draws grouped content.
    /// Every card a session puts up — vocabulary, drill prompt, listening prompt — wears this.
    func cardSurface() -> some View {
        background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
    }

    /// A field's or input row's surface: the control corner, filled, with a hairline edge.
    func controlSurface() -> some View {
        background(
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .strokeBorder(Theme.colors.separator, lineWidth: 1)
        )
    }

    /// The one tinted capsule a standing wears: a word — never a color alone —
    /// over that color's own wash (kern's `Palette.WASH`),
    /// so a badge reads the same on a card as on a recessed row.
    /// The wash is what makes it a standing rather than a control:
    /// a saturated fill is what a button wears, and a row of solid slabs beside
    /// each other is unreadable.
    func pill(_ color: Color) -> some View {
        font(Theme.typography.caption)
            .foregroundStyle(color)
            .padding(.horizontal, Theme.spacing.md)
            .padding(.vertical, Theme.spacing.xs + 1)
            .background(color.opacity(Palette.shared.WASH), in: Capsule())
    }

    /// The one inline PANEL: a block of the page — a table's rows, a ladder, a
    /// list of notes — set on the surface color at the tile radius, full width,
    /// with its own inset. A card is a question, a panel is the page around it,
    /// so it sits at the tile radius rather than the card's.
    func panelSurface(_ fill: Color = Theme.colors.surface,
                      vertical: CGFloat = Theme.spacing.lg) -> some View {
        padding(.horizontal, Theme.spacing.lg)
            .padding(.vertical, vertical)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(fill)
            )
    }
}

/// Filled terracotta primary action. Never a default gray Button.
struct PrimaryButtonStyle: ButtonStyle {
    var color: Color = Theme.colors.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.typography.headline)
            .foregroundStyle(Theme.colors.onColor)
            .padding(.vertical, Theme.spacing.lg)
            .padding(.horizontal, Theme.spacing.xl)
            .frame(minHeight: 52) // card-parity: the button's own height, not a card reserve
            .background(color, in: RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Soft tinted secondary action (colored text on a translucent tint).
struct SoftButtonStyle: ButtonStyle {
    var color: Color = Theme.colors.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.typography.headline)
            .foregroundStyle(color)
            .padding(.vertical, Theme.spacing.md)
            .padding(.horizontal, Theme.spacing.lg)
            .frame(minHeight: 44)
            .background(color.opacity(Palette.shared.WASH), in: RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Compact icon-only action: one glyph on a round tint, sized for a thumb.
struct IconButtonStyle: ButtonStyle {
    var color: Color = Theme.colors.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.typography.headline)
            .foregroundStyle(color)
            .frame(width: 40, height: 40)
            .background(color.opacity(Palette.shared.WASH), in: Circle())
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Pressed-state feedback for every chip-shaped control (mirrors the shared button springs):
/// the hub's chips, a drill's choice tiles, a scramble's tile bank and the listening row.
struct ChipButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
