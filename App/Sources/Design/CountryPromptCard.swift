import SprossKern
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
    /// the name for VoiceOver (`spoken`), which the caption no longer does
    /// and which the placeholder cannot: a11y metadata is not a caption.
    var language: String?
    /// Hearing the name the question ASKS about, where it is a name in the
    /// language being learned — a reversed run's prompt. Nil elsewhere, and for
    /// two different reasons: a forward run's prompt is the learner's own
    /// language, which nothing outside listening mode says, and a prompt whose
    /// READING is the answer (a dates run's `Mo, 3.3.`) would hand it over.
    /// The word is already on the card, so saying it gives nothing away.
    var promptVoice: Voice?
    /// The answer, once the learner has stopped owing it.
    var revealed: Reveal?

    /// A form that can be heard, and whether it is sounding right now.
    struct Voice {
        var pronounce: (() -> Void)?
        var isPlaying = false
    }

    struct Reveal {
        /// What a refused answer actually named (Uswidi is Schweden) — the nudge
        /// line under the reveal; nil everywhere but a refused miss.
        var otherWord: (word: String, meanings: String)?
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

    /// The flag rides in the card's leading slot (`CardEmoji`) exactly as a
    /// word's picture does on a review card — never above the words, where it
    /// pushes the name into the space the reveal needs.
    var body: some View {
        HStack(spacing: Theme.spacing.md) {
            if let emoji, text != nil {
                CardEmoji(emoji,
                            cue: emojiCue(givesAnswerAway: emojiIsGiveaway),
                            revealed: revealed != nil)
            }
            VStack(spacing: Theme.spacing.md) {
                caption
                if let text {
                    SpokenWord(pronounce: promptVoice?.pronounce,
                               isPlaying: promptVoice?.isPlaying ?? false) {
                        Text(verbatim: text)
                            .font(Theme.prompt.name)
                            .foregroundStyle(Theme.colors.textPrimary)
                            .multilineTextAlignment(.center)
                            .lineLimit(3)
                            .minimumScaleFactor(0.5)
                            .spoken(text, language: language)
                    }
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
                        .font(Theme.prompt.glyph)
                }
                if let revealed {
                    CardReveal(note: revealed.note) {
                        SpokenWord(pronounce: revealed.pronounce, isPlaying: revealed.isPlaying) {
                            Text(verbatim: revealed.word)
                                .font(Theme.typography.title)
                                .foregroundStyle(Theme.colors.accent)
                                .multilineTextAlignment(.center)
                                .minimumScaleFactor(0.6)
                                .spoken(revealed.word, language: revealed.language)
                        }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                    if let other = revealed.otherWord {
                        // why: same line as the review session's — both explain
                        // what became of the answer, so they read alike.
                        Text("session.otherWord \(other.word) \(other.meanings)")
                            .pauseLine()
                    }
                }
            }
            .frame(maxWidth: .infinity)
            if emoji != nil, text != nil {
                CardEmoji.balance()
            }
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        // why: the sibling drill cards' height — a learner moving between the
        // drills meets one layout, and the field below never jumps.
        .frame(minHeight: Theme.reserve.drillCard)
        .cardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed?.word)
    }

    /// The ask alone — the one thing the card face cannot say for itself.
    private var caption: some View {
        Text(ask)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
            .textCase(.uppercase)
            .multilineTextAlignment(.center)
    }
}

// MARK: - Previews

#Preview("Country prompt") {
    VStack(spacing: Theme.spacing.xl) {
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
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}
