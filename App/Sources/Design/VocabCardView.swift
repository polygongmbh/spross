import SwiftUI

// MARK: - VocabCardView
//
// The hero review card. The prompt is COMPACT (no space reserved for the
// answer); the reveal expands the card downward, animated — existing
// content never moves or flips, growth is strictly below it.
//
// Language-symmetric: the card renders a PROMPT side and an ANSWER side —
// which language plays which role is the caller's business (alternating
// presentation). Styling keys off ROLE, never language: the prompt is
// neutral, the reveal pops in accent.

struct VocabCardView: View {

    /// One face of the card, pre-resolved by the caller. `article` renders
    /// inline in its color before the word (poster style); `plural` and
    /// `alternates` are small secondary lines; `femMarker` adds the labeled
    /// ♀ badge (never graded).
    struct Side {
        var text: String
        var article: String?
        var plural: String?
        var alternates: String?
        /// Disambiguating label ABOVE the headword — the area, set only on an ambiguous
        /// PRODUCE prompt (`Card.promptAmbiguous`). Never on a reveal or a recognition
        /// prompt, where a cue that identifies the concept would give the answer away.
        var context: String?
        var femMarker: Bool = false

        init(text: String, article: String? = nil, plural: String? = nil,
             alternates: String? = nil, context: String? = nil, femMarker: Bool = false) {
            self.text = text
            self.article = article
            self.plural = plural
            self.alternates = alternates
            self.context = context
            self.femMarker = femMarker
        }
    }

    /// Per-word illustration; nil for verbs/phrases with no seed emoji —
    /// the card then drops the circle and centers on the word itself.
    let emoji: String?
    let prompt: Side
    let answer: Side
    /// Optional literal gloss ("wörtlich: …"), shown only post-reveal.
    let note: String?
    var revealed: Bool = false
    /// Session style: tighter card so card + input + button + keyboard
    /// all fit on screen without scrolling. Previews keep the big card.
    var compact: Bool = false

    var body: some View {
        VStack(spacing: compact ? DL.Space.s : DL.Space.l) {
            if let emoji, !emoji.isEmpty {
                emojiIllustration(emoji)
            }
            sideBlock(prompt, emphasized: false)
            if revealed {
                revealSection
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(compact ? DL.Space.l : DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .strokeBorder(Color.dlSeparator.opacity(0.6), lineWidth: 1)
        )
        .dlCardShadow()
        .animation(.easeOut(duration: 0.25), value: revealed)
    }

    // MARK: Pieces

    private func emojiIllustration(_ emoji: String) -> some View {
        Text(emoji)
            .font(.system(size: compact ? 40 : 76))
            .padding(compact ? DL.Space.s + 2 : DL.Space.l)
            .background(Circle().fill(Color.dlSurfaceTint))
            .accessibilityHidden(true) // why: decorative; the headword carries the content
    }

    @ViewBuilder
    private var revealSection: some View {
        VStack(spacing: DL.Space.m) {
            RoundedRectangle(cornerRadius: 1)
                .fill(Color.dlSeparator)
                .frame(width: 44, height: 2)
            sideBlock(answer, emphasized: true)
            if let note {
                Text(note)
                    .font(DL.Fonts.caption)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    /// "der Kühlschrank" as ONE line — article inline in its color before
    /// the word (poster style), plural/alternates as small lines below.
    /// `emphasized` marks the REVEAL side (the answer pops in accent); it has
    /// nothing to do with which language this is — either side can be either
    /// role depending on the card's presentation role.
    private func sideBlock(_ side: Side, emphasized: Bool) -> some View {
        VStack(spacing: DL.Space.xs) {
            // why: ABOVE the headword, so it reads as a label on the prompt and never
            // sits in the plural/alternates region that belongs to the reveal.
            if let context = side.context {
                Text(context)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
            headline(side, emphasized: emphasized)
                .multilineTextAlignment(.center)
                // why: a gentle floor keeps a long answer the same size as a
                // short one (it wraps rather than shrinking); the factor is only
                // overflow insurance for the rare word too long to wrap.
                .minimumScaleFactor(0.85)
            if let plural = side.plural {
                Text(plural)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            if let alternates = side.alternates {
                Text(alternates)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    @ViewBuilder
    private func headline(_ side: Side, emphasized: Bool) -> some View {
        if side.femMarker {
            HStack(spacing: DL.Space.s) {
                headlineText(side, emphasized: emphasized)
                FeminineBadge()
            }
        } else {
            headlineText(side, emphasized: emphasized)
        }
    }

    /// Both sides use the same font so a word never changes size just
    /// because the card flipped role.
    private func headlineText(_ side: Side, emphasized: Bool) -> Text {
        let word = Text(side.text)
            .font(compact ? DL.Fonts.title : DL.Fonts.hero)
            .foregroundStyle(emphasized ? Color.dlAccent : Color.dlTextPrimary)
        guard let article = side.article else { return word }
        return Text(verbatim: "\(article) ")
            .font(compact ? DL.Fonts.title : DL.Fonts.hero)
            .foregroundStyle(DL.articleColor(article))
            + word
    }
}

/// Labeled ♀ badge — marks a feminine-sibling prompt/answer; decorative
/// grammar cue, never part of grading.
struct FeminineBadge: View {
    var body: some View {
        Text(verbatim: "♀")
            .font(DL.Fonts.badge)
            .foregroundStyle(Color.dlDie)
            .padding(.horizontal, DL.Space.s)
            .padding(.vertical, DL.Space.xs)
            .background(Color.dlDie.opacity(0.14), in: Capsule())
            .accessibilityLabel("a11y.feminineForm")
    }
}

// MARK: - Card flip transition
//
// Quick horizontal 3D flip BETWEEN cards (~0.3 s). Within a card the reveal
// stays a fade — the flip only ever marks the switch to the next card.

struct CardFlipEffect: ViewModifier, @MainActor Animatable {
    var angle: Double

    var animatableData: Double {
        get { angle }
        set { angle = newValue }
    }

    func body(content: Content) -> some View {
        content
            .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.4)
            // why: hide the mirrored "backface" at ±90° so the outgoing and
            // incoming card each show only their front half of the flip.
            .opacity(abs(angle) >= 90 ? 0 : 1)
    }
}

extension AnyTransition {
    /// Insertion flips in from the right, removal flips out to the left.
    @MainActor static var dlCardFlip: AnyTransition {
        .asymmetric(
            insertion: .modifier(active: CardFlipEffect(angle: 90),
                                 identity: CardFlipEffect(angle: 0)),
            removal: .modifier(active: CardFlipEffect(angle: -90),
                               identity: CardFlipEffect(angle: 0))
        )
    }
}

extension Animation {
    /// The one animation used for the between-cards flip.
    static let dlCardFlip = Animation.easeInOut(duration: 0.3)
}

// MARK: - ArticleBadge

/// Colored article pill, exactly like the poster's "der/die/das" pills.
/// The article text itself carries the meaning; color only reinforces it.
struct ArticleBadge: View {
    let article: String

    var body: some View {
        Text(article)
            .font(DL.Fonts.badge)
            .foregroundStyle(Color.dlOnColor)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.xs + 2)
            .background(DL.articleColor(article), in: Capsule())
    }
}

// MARK: - Previews

#Preview("Recognize · prompt") {
    VocabCardView(
        emoji: "🧊",
        prompt: .init(text: "friji"),
        answer: .init(text: "Kühlschrank", article: "der", plural: "Pl. Kühlschränke"),
        note: nil,
        revealed: false
    )
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Recognize · revealed") {
    VocabCardView(
        emoji: "🍳",
        prompt: .init(text: "kikaango"),
        answer: .init(text: "Pfanne", article: "die", plural: "Pl. Pfannen"),
        note: "wörtlich: kleines Bratgefäß",
        revealed: true
    )
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Compact · with vs. without emoji") {
    VStack(spacing: DL.Space.l) {
        // Not-yet-sticking word: emoji as light support.
        VocabCardView(
            emoji: "🥄",
            prompt: .init(text: "Löffel", article: "der", plural: "Pl. Löffel"),
            answer: .init(text: "kijiko"),
            note: nil,
            revealed: false,
            compact: true
        )
        // Sticking word (or a verb/phrase): no circle, word-focused.
        VocabCardView(
            emoji: nil,
            prompt: .init(text: "rennen"),
            answer: .init(text: "kukimbia"),
            note: nil,
            revealed: false,
            compact: true
        )
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Produce · revealed · dark") {
    VocabCardView(
        emoji: "🔪",
        prompt: .init(text: "Messer"),
        answer: .init(text: "kisu", alternates: "auch: chombo"),
        note: nil,
        revealed: true
    )
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
