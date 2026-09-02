import SprossKern
import SwiftUI

// MARK: - VocabCardView
//
// The hero review card. The prompt is COMPACT (no space reserved for the
// answer); the reveal expands the card, animated.
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
        var article: DLArticle?
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
        /// This face IS the sound: the big replay glyph stands where the word
        /// would, and `text` never renders. Set on a produce prompt asked by
        /// ear, where showing the word would be showing the answer.
        var listening: Bool = false

        init(text: String, article: DLArticle? = nil, plural: String? = nil,
             alternates: String? = nil, context: String? = nil, femMarker: Bool = false,
             language: String? = nil, pronounce: (() -> Void)? = nil, isPlaying: Bool = false,
             listening: Bool = false) {
            self.listening = listening
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

    /// WHEN the picture appears — the shared slot's rule, named here for the
    /// call sites that already speak of a card's emoji cue. Its place never
    /// changes, so `onReveal` fades it into a slot that was already there.
    typealias EmojiCue = DLCardEmoji.Cue

    /// Per-word illustration; nil for verbs/phrases with no seed emoji —
    /// the card then drops the slot and centers on the word itself.
    let emoji: String?
    var emojiCue: EmojiCue = .upfront
    let prompt: Side
    let answer: Side
    /// Optional literal gloss ("wörtlich: …"), shown only post-reveal.
    let note: String?
    var revealed: Bool = false
    /// WHERE the picture sits — stated as the situation that decides it, never
    /// as a size. It was a size flag once, and the run that had to pick picked
    /// wrong: listening drew the big picture, the words kept a column too narrow
    /// for their font, and a six-letter word hyphenated ("mchele" as "mc-hele").
    enum Arrangement {
        /// The card SHARES the screen — a review or drill card standing above an
        /// input, a button and a keyboard. Vertical space is the scarce axis, so
        /// the picture stays small and sits BESIDE the words.
        case beside
        /// The card OWNS the screen — a run whose only content is the card
        /// (listening: nothing to type, nothing to press, no keyboard). Height is
        /// abundant and width is what the words are short of, so the picture
        /// stands ABOVE them and the words get the card's full width.
        case above
    }

    /// What the surface is; the card works its own layout out from that.
    var arrangement: Arrangement = .beside

    /// The picture (`DLCardEmoji`) belongs to the CARD rather than the prompt
    /// line, so it stays centered against prompt and reveal together instead of
    /// riding up as the card grows. Its slot is held for the card's whole life
    /// in either arrangement — the card's text stays centered and the picture is
    /// mirrored on the far edge, so a withheld one fades into a space already kept
    /// for it rather than reflowing the words to make room.
    var body: some View {
        arranged
            .padding(shared ? Theme.spacing.lg : Theme.spacing.xl)
            .frame(maxWidth: .infinity)
            // why: a shared-screen card holds one height whether the prompt is a
            // word, a word under an area label, or the replay glyph of a by-ear
            // question; a card that owns the screen is free to grow into it.
            .frame(minHeight: shared ? Theme.reserve.reviewCard : nil)
            .dlCardSurface()
            .animation(.easeOut(duration: 0.25), value: revealed)
    }

    // MARK: Pieces

    @ViewBuilder
    private var arranged: some View {
        switch arrangement {
        case .beside:
            VStack(spacing: Theme.spacing.sm) {
                pictureRow
                if revealed && hasClosingLines {
                    closingLines
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        case .above:
            VStack(spacing: Theme.spacing.lg) {
                if hasEmoji { picture }
                words
            }
        }
    }

    /// The picture's own row: it stands beside the HEADWORDS — prompt, divider,
    /// answer — so it ends up roughly parallel to the divider instead of floating
    /// above the whole stack. A mirror of its slot on the trailing edge keeps that
    /// block optically centered in the CARD; the width that costs is given back to
    /// the lines that need it, below (`closingLines`), not taken from the center.
    private var pictureRow: some View {
        HStack(spacing: Theme.spacing.md) {
            if hasEmoji { picture }
            VStack(spacing: Theme.spacing.sm) {
                sideBlock(prompt, emphasized: false)
                if revealed {
                    // why: the divider belongs beside the picture, so the reveal
                    // grows the row rather than starting a second stack under it.
                    DLCardReveal(note: nil) {
                        headwordBlock(answer, emphasized: true)
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .frame(maxWidth: .infinity)
            if hasEmoji { DLCardEmoji.balance(emojiSize) }
        }
    }

    /// What the reveal says ABOUT the answer rather than as the answer: grammar,
    /// the other forms, the literal gloss. They are the long lines and they sit
    /// beside nothing, so they take the card's full width.
    private var closingLines: some View {
        VStack(spacing: Theme.spacing.xs) {
            secondaryLines(answer)
            if let note {
                Text(note).dlNoteLine()
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var hasClosingLines: Bool {
        note != nil || answer.plural != nil || answer.alternates != nil
    }

    private var picture: some View {
        DLCardEmoji(emoji ?? "", size: emojiSize, cue: emojiCue, revealed: revealed)
    }

    private var words: some View {
        VStack(spacing: shared ? Theme.spacing.sm : Theme.spacing.lg) {
            sideBlock(prompt, emphasized: false)
            if revealed {
                revealSection
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var hasEmoji: Bool {
        !(emoji ?? "").isEmpty
    }

    /// The screen belongs to more than this card — the tighter of the two.
    private var shared: Bool { arrangement == .beside }

    private var emojiSize: DLCardEmoji.Size { shared ? .compact : .hero }

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
        VStack(spacing: Theme.spacing.xs) {
            headwordBlock(side, emphasized: emphasized)
            secondaryLines(side)
        }
    }

    /// The word itself, under its label: the part a picture may stand beside.
    private func headwordBlock(_ side: Side, emphasized: Bool) -> some View {
        VStack(spacing: Theme.spacing.xs) {
            // why: ABOVE the headword, so it reads as a label on the prompt and never
            // sits in the plural/alternates region that belongs to the reveal.
            if let context = side.context {
                Text(context)
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            headline(side, emphasized: emphasized)
                .multilineTextAlignment(.center)
                // why: a gentle floor keeps a long answer the same size as a
                // short one (it wraps rather than shrinking); the factor is only
                // overflow insurance for the rare word too long to wrap.
                .minimumScaleFactor(0.85)
        }
    }

    /// Grammar and the other forms — small lines that belong TO a headword
    /// without being one.
    @ViewBuilder
    private func secondaryLines(_ side: Side) -> some View {
        if let plural = side.plural {
            Text(plural)
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
        }
        if let alternates = side.alternates {
            // why: matches the plural line — both belong to the reveal, so
            // neither shrinks below the size the learner has to read.
            Text(alternates)
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
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
        if side.listening {
            // why: the glyph IS the question here — no word may render beside
            // it, and it keeps its own label because there is no headword for
            // VoiceOver to read instead.
            SpeakerIcon(size: .large, isPlaying: side.isPlaying, pronounce: side.pronounce)
                .accessibilityLabel("a11y.action.pronounce")
        } else {
            DLSpokenWord(pronounce: side.pronounce,
                         isPlaying: side.isPlaying,
                         badge: side.femMarker ? AnyView(FeminineBadge()) : nil) {
                headlineWord(side, emphasized: emphasized)
            }
        }
    }

    /// The headline as VoiceOver should hear it: tagged with the language it is
    /// written in (`dlSpoken`), article included, because that is how the line
    /// reads on screen.
    private func headlineWord(_ side: Side, emphasized: Bool) -> some View {
        headlineText(side, emphasized: emphasized)
            .dlSpoken(side.article.map { "\($0.text) \(side.text)" } ?? side.text,
                      language: side.language)
    }

    /// Both sides use the same font so a word never changes size just
    /// because the card flipped role.
    private func headlineText(_ side: Side, emphasized: Bool) -> Text {
        let word = Text(side.text)
            .font(shared ? Theme.typography.title : Theme.typography.hero)
            .foregroundStyle(emphasized ? Theme.colors.accent : Theme.colors.textPrimary)
        guard let article = side.article else { return word }
        return Text(verbatim: "\(article.text) ")
            .font(shared ? Theme.typography.title : Theme.typography.hero)
            .foregroundStyle(Theme.genderColor(article.gender))
            + word
    }
}

/// Labeled ♀ badge — marks a feminine-sibling prompt/answer; decorative
/// grammar cue, never part of grading.
struct FeminineBadge: View {
    var body: some View {
        Text(verbatim: "♀")
            .font(Theme.typography.badge)
            .foregroundStyle(Theme.colors.die)
            .padding(.horizontal, Theme.spacing.sm)
            .padding(.vertical, Theme.spacing.xs)
            .background(Theme.colors.die.opacity(0.14), in: Capsule())
            .accessibilityLabel("a11y.glyph.feminineForm")
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
    let article: DLArticle

    var body: some View {
        Text(article.text)
            .font(Theme.typography.badge)
            .foregroundStyle(Theme.colors.onColor)
            .padding(.horizontal, Theme.spacing.md)
            .padding(.vertical, Theme.spacing.xs + 2)
            .background(Theme.genderColor(article.gender), in: Capsule())
    }
}

// MARK: - Previews

#Preview("Shared screen · prompt") {
    VocabCardView(
        emoji: "🧊",
        prompt: .init(text: "friji"),
        answer: .init(text: "Kühlschrank", article: .init("der", gender: .masculine),
                      plural: "Pl. Kühlschränke"),
        note: nil,
        revealed: false
    )
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Shared screen · revealed") {
    VocabCardView(
        emoji: "🍳",
        emojiCue: emojiCue(givesAnswerAway: true),
        prompt: .init(text: "kikaango"),
        answer: .init(text: "Pfanne", article: .init("die", gender: .feminine),
                      plural: "Pl. Pfannen"),
        note: "wörtlich: kleines Bratgefäß",
        revealed: true
    )
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Shared screen · with vs. without emoji") {
    VStack(spacing: Theme.spacing.lg) {
        // Not-yet-sticking word: emoji as light support.
        VocabCardView(
            emoji: "🥄",
            prompt: .init(text: "Löffel", article: .init("der", gender: .masculine),
                          plural: "Pl. Löffel"),
            answer: .init(text: "kijiko"),
            note: nil,
            revealed: false,
            arrangement: .beside
        )
        // Sticking word (or a verb/phrase): no circle, word-focused.
        VocabCardView(
            emoji: nil,
            prompt: .init(text: "rennen"),
            answer: .init(text: "kukimbia"),
            note: nil,
            revealed: false,
            arrangement: .beside
        )
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Owns the screen · before and after the meaning") {
    VStack(spacing: Theme.spacing.lg) {
        VocabCardView(
            emoji: "🍚",
            prompt: .init(text: "mchele"),
            answer: .init(text: "Reis"),
            note: nil,
            revealed: false,
            arrangement: .above
        )
        VocabCardView(
            emoji: "🔪",
            prompt: .init(text: "kisu", alternates: "auch: chombo"),
            answer: .init(text: "Messer"),
            note: nil,
            revealed: true,
            arrangement: .above
        )
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
    .preferredColorScheme(.dark)
}
