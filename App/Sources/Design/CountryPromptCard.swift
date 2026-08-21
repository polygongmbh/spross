import SwiftUI

/// The atlas question, on the same card face as every other session card: a
/// caption naming what is asked, the flag BESIDE the words where the question
/// is about a country, and the name itself — then the answer growing below it
/// once it is out.
///
/// The caption stays here rather than on the field, because a bare name says
/// nothing about which of the four things is being asked: "Deutschland" is the
/// prompt whether the answer owed is the country, its people or its language.
/// It says THAT and no more — which language the answer is owed in is the
/// field's placeholder's to say, and saying it here too would be the third
/// telling of what one tap already settled (docs/surfaces.md).
///
/// One question has no name on it at all: where kern hands over a flag and no
/// [text], the flag IS the question and stands where the name would.
///
/// The other way round, a flag can be the ANSWER — a reversed run is answered
/// in the learner's own language, so showing it would settle the question. It
/// is held to the reveal rather than dropped ([emojiIsGiveaway]): the learner
/// still finds out which country they were asked about, which is the whole
/// point of having carried the picture at all.
struct CountryPromptCard: View {
    /// What is being asked ("Wie heißt dieses Land?" …).
    let ask: LocalizedStringKey
    /// The country's flag; nil where the question is about a language.
    var emoji: String?
    /// Whether showing [emoji] while the answer is owed would ANSWER the
    /// question — kern's `emojiIsGiveaway`, true of a reversed run's country
    /// questions. The flag is then held to the reveal rather than dropped: the
    /// card says WHEN a picture appears and never loses one.
    var emojiIsGiveaway: Bool = false
    /// The name asked about; nil where the flag alone is the question.
    var text: String?
    /// BCP-47 code of the language [text] is WRITTEN in. Never shown — it tags
    /// the name for VoiceOver (`dlSpoken`), which the caption no longer does
    /// and which the placeholder cannot: a11y metadata is not a caption.
    var language: String?
    /// The answer, once the learner has stopped owing it.
    var revealed: Reveal?

    struct Reveal {
        let word: String
        /// The answer side's neighboring form — the people beside the country,
        /// the country beside the language. Never shown before the answer is in.
        var note: String?
        /// BCP-47 code of the language the ANSWER is in — the other side of the
        /// pair from the card's, and the reveal's half of the same tagging.
        var language: String?
        var pronounce: (() -> Void)?
        var isPlaying = false
    }

    /// The flag rides in the card's leading slot (`DLCardEmoji`) exactly as a
    /// word's picture does on a review card — never above the words, where it
    /// pushes the name into the space the reveal needs.
    var body: some View {
        HStack(spacing: DL.Space.m) {
            if let emoji, text != nil {
                DLCardEmoji(emoji,
                            cue: emojiIsGiveaway ? .onReveal : .upfront,
                            revealed: revealed != nil)
            }
            VStack(spacing: DL.Space.m) {
                caption
                if let text {
                    Text(verbatim: text)
                        .font(DL.Fonts.Prompt.name)
                        .foregroundStyle(Color.dlTextPrimary)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .minimumScaleFactor(0.5)
                        .dlSpoken(text, language: language)
                } else if let emoji {
                    // why: no side slot here — the flag is not the picture beside
                    // the question, it IS the question, so it takes the place and
                    // the size the name would have had. It is NOT hidden from
                    // VoiceOver either, for the same reason: hiding it would
                    // leave the screen reader nothing to read but the ask.
                    //
                    // Nor is it ever a giveaway: a question whose whole content
                    // is the flag has nothing left to show if the flag is held
                    // back, which is why kern builds it forward only.
                    Text(verbatim: emoji)
                        .font(DL.Fonts.Prompt.glyph)
                }
                if let revealed {
                    DLCardReveal(note: revealed.note) {
                        DLSpokenWord(pronounce: revealed.pronounce, isPlaying: revealed.isPlaying) {
                            Text(verbatim: revealed.word)
                                .font(DL.Fonts.title)
                                .foregroundStyle(Color.dlAccent)
                                .multilineTextAlignment(.center)
                                .minimumScaleFactor(0.6)
                                .dlSpoken(revealed.word, language: revealed.language)
                        }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .frame(maxWidth: .infinity)
            if emoji != nil, text != nil {
                DLCardEmoji.balance()
            }
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity)
        // why: the sibling drill cards' height — a learner moving between the
        // drills meets one layout, and the field below never jumps.
        .frame(minHeight: DL.Reserve.drillCard)
        .dlCardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed?.word)
    }

    /// The ask alone — the one thing the card face cannot say for itself.
    private var caption: some View {
        Text(ask)
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            .multilineTextAlignment(.center)
    }
}

// MARK: - Previews

#Preview("Country prompt") {
    VStack(spacing: DL.Space.xl) {
        CountryPromptCard(ask: "countries.ask.country", emoji: "🇰🇪",
                          text: "Kenia", language: "de")
        CountryPromptCard(ask: "countries.ask.spokenIn",
                          emoji: "🇨🇭", text: "die Schweiz", language: "de",
                          revealed: .init(word: "Kijerumani", note: "Uswisi",
                                          language: "sw", pronounce: {}))
        CountryPromptCard(ask: "countries.ask.language", text: "Kiswahili", language: "sw")
        // The flag alone: no name anywhere on the card until the answer is in.
        CountryPromptCard(ask: "countries.ask.flag", emoji: "🇺🇦")
        // Reversed: the flag would answer the question, so the slot stands
        // empty while it is owed — and fills the moment the answer is in.
        CountryPromptCard(ask: "countries.ask.country", emoji: "🇦🇹",
                          emojiIsGiveaway: true, text: "Austria", language: "sw")
        CountryPromptCard(ask: "countries.ask.country", emoji: "🇦🇹",
                          emojiIsGiveaway: true, text: "Austria", language: "sw",
                          revealed: .init(word: "Österreich", note: "Österreicher",
                                          language: "de", pronounce: {}))
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
