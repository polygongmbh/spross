import SwiftUI

// MARK: - VocabCardView
//
// The hero review card. The prompt is COMPACT (no space reserved for the
// answer); the reveal expands the card downward, animated — existing
// content never moves or flips, growth is strictly below it.

struct VocabCardView: View {

    enum Mode {
        /// German shown first (de→target recognition): reveal shows the translation.
        case recognition
        /// Translation shown first (target→de production): reveal shows the German side.
        case production
    }

    /// Per-word illustration; nil for verbs/phrases with no seed emoji —
    /// the card then drops the circle and centers on the word itself.
    let emoji: String?
    let article: String?
    let headword: String
    let plural: String?
    let translation: String
    /// Optional literal gloss ("wörtlich: …"), shown only post-reveal.
    let note: String?
    var mode: Mode = .recognition
    var revealed: Bool = false
    /// Session style: tighter card so card + input + button + keyboard
    /// all fit on screen without scrolling. Previews keep the big card.
    var compact: Bool = false
    /// German plural line: only learners OF GERMAN need it ("die Wörter"
    /// is noise when German is your known language).
    var showPlural: Bool = true

    var body: some View {
        VStack(spacing: compact ? DL.Space.s : DL.Space.l) {
            if let emoji, !emoji.isEmpty {
                emojiIllustration(emoji)
            }
            promptSection
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
    private var promptSection: some View {
        switch mode {
        case .recognition: germanBlock
        case .production: translationBlock
        }
    }

    @ViewBuilder
    private var revealSection: some View {
        VStack(spacing: DL.Space.m) {
            RoundedRectangle(cornerRadius: 1)
                .fill(Color.dlSeparator)
                .frame(width: 44, height: 2)
            switch mode {
            case .recognition: translationBlock
            case .production: germanBlock
            }
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
    /// the word (poster style), plural as a small line only when wanted.
    private var germanBlock: some View {
        VStack(spacing: DL.Space.xs) {
            germanLine
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.6)
            if showPlural, let plural {
                Text(plural)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextSecondary)
            }
        }
    }

    private var germanLine: Text {
        let word = Text(headword)
            .font(compact ? DL.Fonts.title : DL.Fonts.hero)
            .foregroundStyle(Color.dlTextPrimary)
        guard let article else { return word }
        return Text("\(article) ")
            .font(compact ? DL.Fonts.title : DL.Fonts.hero)
            .foregroundStyle(DL.articleColor(article))
            + word
    }

    private var translationBlock: some View {
        Text(translation)
            .font(mode == .production && !compact ? DL.Fonts.hero : DL.Fonts.title)
            .foregroundStyle(mode == .production ? Color.dlTextPrimary : Color.dlAccent)
            .multilineTextAlignment(.center)
            .minimumScaleFactor(0.6)
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
            .foregroundStyle(.white)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.xs + 2)
            .background(DL.articleColor(article), in: Capsule())
    }
}

// MARK: - Previews

#Preview("Recognition · prompt") {
    VocabCardView(
        emoji: "🧊",
        article: "der",
        headword: "Kühlschrank",
        plural: "die Kühlschränke",
        translation: "friji",
        note: nil,
        mode: .recognition,
        revealed: false
    )
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Recognition · revealed") {
    VocabCardView(
        emoji: "🍳",
        article: "die",
        headword: "Pfanne",
        plural: "die Pfannen",
        translation: "kikaango",
        note: "wörtlich: kleines Bratgefäß",
        mode: .recognition,
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
            article: "der",
            headword: "Löffel",
            plural: "die Löffel",
            translation: "kijiko",
            note: nil,
            mode: .recognition,
            revealed: false,
            compact: true
        )
        // Sticking word (or a verb/phrase): no circle, word-focused.
        VocabCardView(
            emoji: nil,
            article: nil,
            headword: "rennen",
            plural: nil,
            translation: "kukimbia",
            note: nil,
            mode: .recognition,
            revealed: false,
            compact: true
        )
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Production · revealed · dark") {
    VocabCardView(
        emoji: "🔪",
        article: "das",
        headword: "Messer",
        plural: "die Messer",
        translation: "kisu",
        note: nil,
        mode: .production,
        revealed: true
    )
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
