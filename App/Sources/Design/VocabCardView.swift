import SwiftUI

// MARK: - VocabCardView
//
// The hero review card. Hard spec rule: the card stays VISUALLY STABLE
// between prompt and revealed states — the reveal section is ALWAYS laid
// out (reserved space) and only fades in, so nothing ever flips or jumps.

struct VocabCardView: View {

    enum Mode {
        /// German shown first (de→target recognition): reveal shows the translation.
        case recognition
        /// Translation shown first (target→de production): reveal shows the German side.
        case production
    }

    let emoji: String
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
    /// Review-phase cards ("sticking" cards) must not leak the emoji hint
    /// during the prompt — a neutral "?" holds its place until reveal.
    var hideEmojiUntilRevealed: Bool = false

    var body: some View {
        VStack(spacing: compact ? DL.Space.s : DL.Space.l) {
            emojiIllustration
            promptSection
            revealSection
                .opacity(revealed ? 1 : 0)
                .accessibilityHidden(!revealed)
            Spacer(minLength: 0)
        }
        .padding(compact ? DL.Space.l : DL.Space.xl)
        .frame(maxWidth: .infinity)
        // why: compact must leave room for input + primary button above the
        // keyboard on a 874 pt screen — no scrolling during review.
        .frame(minHeight: compact ? 240 : 380, alignment: .top)
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

    private var emojiHidden: Bool {
        hideEmojiUntilRevealed && !revealed
    }

    private var emojiIllustration: some View {
        ZStack {
            Text(emoji)
                .font(.system(size: compact ? 40 : 76))
                .opacity(emojiHidden ? 0 : 1)
            // Same footprint as the emoji — the card never jumps on reveal.
            Text("?")
                .font(.system(size: compact ? 28 : 52, weight: .bold, design: .rounded))
                .foregroundStyle(Color.dlTextSecondary.opacity(0.55))
                .opacity(emojiHidden ? 1 : 0)
        }
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

    private var germanBlock: some View {
        VStack(spacing: DL.Space.s) {
            if let article {
                ArticleBadge(article: article)
            }
            Text(headword)
                .font(compact ? DL.Fonts.title : DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.6)
            if let plural {
                Text(plural)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextSecondary)
            }
        }
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

#Preview("Compact · review phase (emoji hidden)") {
    VStack(spacing: DL.Space.l) {
        VocabCardView(
            emoji: "🥄",
            article: "der",
            headword: "Löffel",
            plural: "die Löffel",
            translation: "kijiko",
            note: nil,
            mode: .recognition,
            revealed: false,
            compact: true,
            hideEmojiUntilRevealed: true
        )
        VocabCardView(
            emoji: "🥄",
            article: "der",
            headword: "Löffel",
            plural: "die Löffel",
            translation: "kijiko",
            note: nil,
            mode: .recognition,
            revealed: true,
            compact: true,
            hideEmojiUntilRevealed: true
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
