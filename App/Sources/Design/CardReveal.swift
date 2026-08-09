import SwiftUI

// MARK: - DLCardReveal
//
// What a card grows when the answer comes out: a short rule, the answer, and
// an optional note under it. The reveal is always BELOW the prompt and always
// looks the same — a vocabulary card and a drill card reveal alike, so the two
// never drift into two different ideas of "the answer".

struct DLCardReveal<Content: View>: View {
    /// Literal gloss ("wörtlich: …") or the sentence's meaning — post-reveal
    /// only, and always the last line.
    var note: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: DL.Space.m) {
            RoundedRectangle(cornerRadius: 1)
                .fill(Color.dlSeparator)
                .frame(width: 44, height: 2)
            content
            if let note {
                // why: subheadline, not caption — post-reveal lines are meant to
                // be read, and 12 pt secondary text is where legibility broke.
                Text(note)
                    .font(DL.Fonts.subheadline)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }
}

// MARK: - DLCardEmoji
//
// The picture on a card, and it always sits BESIDE the words, never above
// them: vertical space is the scarce axis (card + input + button + keyboard
// share one screen), and a fixed side slot means a reveal can fade one in
// without moving a thing. One definition, so the review card and the drill
// cards cannot drift into two ideas of what a card's picture looks like.

struct DLCardEmoji: View {
    enum Size {
        /// A session or drill card, where the keyboard shares the screen.
        case compact
        /// The full-height review card (previews, big type).
        case hero

        var diameter: CGFloat { self == .compact ? 52 : 96 }
        var glyph: CGFloat { self == .compact ? 28 : 52 }
    }

    let emoji: String
    var size: Size = .compact

    init(_ emoji: String, size: Size = .compact) {
        self.emoji = emoji
        self.size = size
    }

    var body: some View {
        Text(emoji)
            .font(.system(size: size.glyph))
            .frame(width: size.diameter, height: size.diameter)
            .background(Circle().fill(Color.dlSurfaceTint))
            .accessibilityHidden(true) // why: decorative; the headword carries the content
    }

    /// The mirror of the slot on the card's other edge, so the words stay
    /// centred in the card rather than in what is left of it. One point tall:
    /// it owes the layout a width, never a height.
    static func balance(_ size: Size = .compact) -> some View {
        Color.clear.frame(width: size.diameter, height: 1)
    }
}

extension View {
    /// The line an amber hold pauses on — a typo's proper spelling, the word
    /// that was heard instead, the other word the answer turned out to be.
    /// Read, not glanced at, so it carries the same weight everywhere.
    func dlPauseLine() -> some View {
        font(DL.Fonts.subheadline)
            .italic()
            .foregroundStyle(Color.dlTextSecondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}

// MARK: - Previews

#Preview("Reveal") {
    VStack(spacing: DL.Space.l) {
        DLCardReveal(note: "wörtlich: kleines Bratgefäß") {
            Text(verbatim: "kikaango")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlAccent)
        }
        Text(verbatim: "Fast! Richtig geschrieben: cuatro")
            .dlPauseLine()
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
