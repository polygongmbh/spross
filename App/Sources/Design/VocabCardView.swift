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
        /// BCP-47 code of the language this face is IN — set on the TARGET side
        /// only, so VoiceOver reads the headword with the right voice instead
        /// of spelling a Ukrainian word out in German.
        var language: String?
        /// Says the headword out loud, if it can be heard at all — nil hides
        /// the speaker icon beside the word entirely (see `headlineRow`).
        var pronounce: (() -> Void)?
        /// Whether this side's word is the one sounding right now — pulses
        /// the small speaker icon beside it.
        var isPlaying: Bool = false

        init(text: String, article: String? = nil, plural: String? = nil,
             alternates: String? = nil, context: String? = nil, femMarker: Bool = false,
             language: String? = nil, pronounce: (() -> Void)? = nil, isPlaying: Bool = false) {
            self.text = text
            self.article = article
            self.plural = plural
            self.alternates = alternates
            self.context = context
            self.femMarker = femMarker
            self.language = language
            self.pronounce = pronounce
            self.isPlaying = isPlaying
        }
    }

    /// WHEN the picture appears. Its place never changes, so `onReveal` fades it
    /// into a slot that was already there — nothing on the card moves either way.
    enum EmojiCue { case upfront, onReveal }

    /// Per-word illustration; nil for verbs/phrases with no seed emoji —
    /// the card then drops the slot and centers on the word itself.
    let emoji: String?
    var emojiCue: EmojiCue = .upfront
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
            promptRow
            if revealed {
                revealSection
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(compact ? DL.Space.l : DL.Space.xl)
        .frame(maxWidth: .infinity)
        .dlCardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed)
    }

    // MARK: Pieces

    /// The picture sits BESIDE the word, never above it: vertical space is the
    /// scarce axis (card + input + button + keyboard share one screen), and a
    /// fixed side slot means the reveal can fade it in without moving a thing.
    /// The slot is mirrored on the trailing edge so the word stays centred.
    private var promptRow: some View {
        HStack(spacing: DL.Space.m) {
            if hasEmoji {
                emojiIllustration(emoji ?? "")
                    .opacity(emojiCue == .upfront || revealed ? 1 : 0)
            }
            sideBlock(prompt, emphasized: false)
                .frame(maxWidth: .infinity)
            if hasEmoji {
                Color.clear.frame(width: emojiDiameter, height: 1)
            }
        }
    }

    private var hasEmoji: Bool {
        !(emoji ?? "").isEmpty
    }

    private var emojiDiameter: CGFloat { compact ? 52 : 96 }

    private func emojiIllustration(_ emoji: String) -> some View {
        Text(emoji)
            .font(.system(size: compact ? 28 : 52))
            .frame(width: emojiDiameter, height: emojiDiameter)
            .background(Circle().fill(Color.dlSurfaceTint))
            .accessibilityHidden(true) // why: decorative; the headword carries the content
    }

    @ViewBuilder
    private var revealSection: some View {
        DLCardReveal(note: note) {
            sideBlock(answer, emphasized: true)
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
                // why: matches the plural line — both belong to the reveal, so
                // neither shrinks below the size the learner has to read.
                Text(alternates)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    /// The 44 pt floor sits on every card unconditionally — a word with a
    /// recording and one without must measure exactly the same, or the same
    /// card would change height between reviews as the synonym rotation lands
    /// on an unrecorded form. The tap lives on the speaker icon beside it
    /// (`headlineRow`), never on the word itself.
    private func headline(_ side: Side, emphasized: Bool) -> some View {
        headlineRow(side, emphasized: emphasized)
            .frame(minHeight: 44)
    }

    @ViewBuilder
    private func headlineRow(_ side: Side, emphasized: Bool) -> some View {
        if side.pronounce != nil || side.femMarker {
            HStack(spacing: DL.Space.s) {
                // why: the same accessories mirrored on the leading edge, inert —
                // the two faces of a card carry different ones (the target side has
                // the speaker, the source side does not), so without the ballast the
                // reveal's word sits visibly off the prompt's word above it.
                accessories(side)
                    .hidden()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                headlineWord(side, emphasized: emphasized)
                accessories(side)
            }
        } else {
            headlineWord(side, emphasized: emphasized)
        }
    }

    /// What rides beside the headword. The speaker keeps its 44 pt tap target
    /// but reserves only the glyph in layout, overhanging into the gap — at
    /// full width it would cost the word 104 pt of its line once mirrored.
    @ViewBuilder
    private func accessories(_ side: Side) -> some View {
        HStack(spacing: DL.Space.s) {
            if let pronounce = side.pronounce {
                SpeakerIcon(size: .small, isPlaying: side.isPlaying, pronounce: pronounce)
                    .accessibilityLabel("a11y.pronounce")
                    .frame(width: 26)
            }
            if side.femMarker {
                FeminineBadge()
            }
        }
    }

    @ViewBuilder
    private func headlineWord(_ side: Side, emphasized: Bool) -> some View {
        let word = headlineText(side, emphasized: emphasized)
        if let label = spokenLabel(side) {
            word.accessibilityLabel(label)
        } else {
            word
        }
    }

    /// The headline as VoiceOver should hear it, tagged with the language it is
    /// written in. It matters most where autoplay is off by design: a VoiceOver
    /// session never autoplays (nothing may speak over the screen reader), so
    /// this reading is the only pronunciation the learner gets.
    private func spokenLabel(_ side: Side) -> Text? {
        guard let language = side.language else { return nil }
        var label = AttributedString(side.article.map { "\($0) \(side.text)" } ?? side.text)
        label.languageIdentifier = language
        return Text(label)
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
        emojiCue: .onReveal,
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
