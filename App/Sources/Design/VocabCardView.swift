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

    var body: some View {
        VStack(spacing: DL.Space.l) {
            emojiIllustration
            promptSection
            revealSection
                .opacity(revealed ? 1 : 0)
                .accessibilityHidden(!revealed)
            Spacer(minLength: 0)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 380, alignment: .top)
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

    private var emojiIllustration: some View {
        Text(emoji)
            .font(.system(size: 76))
            .padding(DL.Space.l)
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
                .font(DL.Fonts.hero)
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
            .font(mode == .production ? DL.Fonts.hero : DL.Fonts.title)
            .foregroundStyle(mode == .production ? Color.dlTextPrimary : Color.dlAccent)
            .multilineTextAlignment(.center)
            .minimumScaleFactor(0.6)
    }
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
