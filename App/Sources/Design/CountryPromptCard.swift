import SwiftUI

/// The atlas question, on the same card face as every other session card: a
/// caption naming what is asked and in which language, the flag where the
/// question is about a country, and the name itself — then the answer growing
/// below it once it is out.
///
/// The caption stays here rather than on the field, because a bare name says
/// nothing about which of the four things is being asked: "Deutschland" is the
/// prompt whether the answer owed is the country, its people or its language.
struct CountryPromptCard: View {
    /// What is being asked ("Wie heißt dieses Land?" …).
    let ask: LocalizedStringKey
    /// The language the PROMPT is written in — named in the caption, so the
    /// reversed run never has to be guessed at.
    let promptLanguage: String
    /// The country's flag; nil where the question is about a language.
    var emoji: String?
    let text: String
    /// The answer, once the learner has stopped owing it.
    var revealed: Reveal?

    struct Reveal {
        let word: String
        /// The answer side's neighbouring form — the people beside the country,
        /// the country beside the language. Never shown before the answer is in.
        var note: String?
        var pronounce: (() -> Void)?
        var isPlaying = false
    }

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(spacing: DL.Space.m) {
            caption
            if let emoji {
                Text(verbatim: emoji)
                    .font(.system(size: 44))
                    .accessibilityHidden(true)
            }
            Text(verbatim: text)
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
                .lineLimit(3)
                .minimumScaleFactor(0.5)
            if let revealed {
                DLCardReveal(note: revealed.note) {
                    DLSpokenWord(pronounce: revealed.pronounce, isPlaying: revealed.isPlaying) {
                        Text(verbatim: revealed.word)
                            .font(DL.Fonts.title)
                            .foregroundStyle(Color.dlAccent)
                            .multilineTextAlignment(.center)
                            .minimumScaleFactor(0.6)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
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

    private var caption: some View {
        Text.joined(Text(ask),
                    Text("trainer.prompt.inLanguage \(LanguageNames.display(promptLanguage, locale: locale, catalog: nil))"))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            .multilineTextAlignment(.center)
    }
}

// MARK: - Previews

#Preview("Country prompt") {
    VStack(spacing: DL.Space.xl) {
        CountryPromptCard(ask: "countries.ask.country", promptLanguage: "de",
                          emoji: "🇰🇪", text: "Kenia")
        CountryPromptCard(ask: "countries.ask.spokenIn", promptLanguage: "de",
                          emoji: "🇨🇭", text: "die Schweiz",
                          revealed: .init(word: "Kijerumani", note: "Uswisi", pronounce: {}))
        CountryPromptCard(ask: "countries.ask.language", promptLanguage: "sw",
                          text: "Kiswahili")
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
