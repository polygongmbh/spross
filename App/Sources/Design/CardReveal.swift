import SprossKern
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
                Text(note).dlNoteLine()
            }
        }
    }
}

// MARK: - DLCardEmoji
//
// The picture on a card. WHERE it sits is the card's own call, worked out from
// what the surface is (`VocabCardView.Arrangement`) rather than passed in as a
// size; the slot only knows how big it is. Either way it is a fixed size held
// for the card's whole life, so a reveal can fade a picture in without moving a
// thing. One definition, so the review card and the drill cards cannot drift
// into two ideas of what a card's picture looks like.

struct DLCardEmoji: View {
    enum Size {
        /// A card that shares the screen — the picture rides beside the words,
        /// so it takes as little of their width as it can.
        case compact
        /// A card that owns the screen — the picture stands above the words at
        /// full size, with nothing to make room for.
        case hero

        var diameter: CGFloat { self == .compact ? 52 : 96 }
        var glyph: CGFloat { self == .compact ? 28 : 52 }
    }

    /// WHEN the picture appears. `upfront` from the first frame; `onReveal`
    /// holds it back while the answer is still owed, which is what a picture
    /// that would ANSWER the question has to do — the word's own emoji on a
    /// produce card, a country's flag on a reversed atlas card.
    ///
    /// There is deliberately no third case. A slot handed a picture always ends
    /// up showing it: withholding one PAST the reveal would leave the learner
    /// never seeing the thing they were asked about, and that is the card's
    /// rule to keep rather than each caller's to remember (`docs/design.md`).
    ///
    /// It is KERN's enum, not a copy of it. A copy meant every call site
    /// translated one into the other, which is a mapping two platforms can spell
    /// differently — and did, until listening made the difference visible. The
    /// design layer may name it because only the app target compiles this file;
    /// the watch and the widgets read finished snapshots and never ask.
    typealias Cue = EmojiCue

    let emoji: String
    var size: Size = .compact
    var cue: Cue = .upfront
    /// Whether the card has given its answer — `onReveal`'s other half.
    var revealed: Bool = false

    init(_ emoji: String, size: Size = .compact, cue: Cue = .upfront, revealed: Bool = false) {
        self.emoji = emoji
        self.size = size
        self.cue = cue
        self.revealed = revealed
    }

    var body: some View {
        Text(emoji)
            .font(.system(size: size.glyph))
            .frame(width: size.diameter, height: size.diameter)
            .background(Circle().fill(Color.dlSurfaceTint))
            .accessibilityHidden(true) // why: decorative; the headword carries the content
            // why: faded rather than removed — the slot is already the right
            // size, so a held-back picture arrives without moving the words.
            .opacity(shows ? 1 : 0)
            .animation(.easeOut(duration: 0.25), value: shows)
    }

    /// The invariant the slot exists to keep: held back only until the reveal.
    private var shows: Bool { cue == .upfront || revealed }

    /// The mirror of the slot on the card's other edge, so the words stay
    /// centered in the card rather than in what is left of it. One point tall:
    /// it owes the layout a width, never a height.
    static func balance(_ size: Size = .compact) -> some View {
        Color.clear.frame(width: size.diameter, height: 1)
    }
}

extension View {
    /// The gloss under a reveal. Subheadline, not caption — post-reveal lines are
    /// meant to be read, and 12 pt secondary text is where legibility broke. It is
    /// a modifier because the card may place the note away from the reveal it
    /// belongs to (beside a picture there is no room for a long line), and one
    /// definition is what keeps the two placements the same line.
    func dlNoteLine() -> some View {
        font(DL.Fonts.subheadline)
            .italic()
            .foregroundStyle(Color.dlTextSecondary)
            .multilineTextAlignment(.center)
            // why: the card's emoji slots are fixed points and do not grow with
            // the type size, so a note left to report its own ideal width can
            // push the row past the card it is drawn on.
            .fixedSize(horizontal: false, vertical: true)
    }

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
