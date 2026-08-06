import SwiftUI

/// The audio question, on the same card face as every other session card: a
/// caption naming what is being asked, one large replay glyph, and — for a gap
/// question — the example word with the asked grapheme blanked. The caption
/// stays here where the slot drills dropped theirs: a sound has nothing on
/// screen to say what it is asking for.
///
/// No answer renders here WHILE THE QUESTION STANDS, and that is the whole
/// point: everything the learner is given is the sound, plus whatever the gap
/// word already shows. Once it is out, the card answers like every other card —
/// the gap closes over the grapheme it was hiding, or, where there was no gap
/// to close, the word grows below the glyph.
///
/// Neither mute reaches this card: a screen whose only content is a sound was
/// itself the request to hear one (`Pronouncer.Trigger.essential`), so there is
/// no silenced state to name and no unmute to offer.
struct HearPromptCard: View {
    /// What is being asked ("Welcher Buchstabe ist das?" …).
    let question: LocalizedStringKey
    /// The language the prompt is spoken in — named in the caption.
    let language: String
    /// `Na＿t` for a gap question; nil where a letter's name is spoken.
    var gapText: String?
    /// The answer, once it is out. A gap question closes over its blank with
    /// the whole word (`Na＿t` → `Nacht`); a dictation, which has no blank to
    /// close, grows the word below the glyph instead.
    ///
    /// Kern hands the whole word over as it is (`LetterDrillTask.gloss` on a
    /// gap prompt) — the card never rebuilds it out of the gap it cut.
    var revealed: Reveal?

    struct Reveal {
        let word: String
        /// The word's meaning — a dictation owes it back once the answer is
        /// out. A gap question has none: its `word` IS what the gloss carried.
        var note: String?
        var pronounce: (() -> Void)?
        var isPlaying = false
    }
    /// nil where the device can neither play nor speak this prompt.
    var replay: (() -> Void)?
    /// Whether the prompt is sounding right now — pulses the speaker glyph.
    var isPlaying: Bool = false
    /// VoiceOver lands here on every task change: the question is one action
    /// away rather than somewhere below the caption.
    var replayFocus: AccessibilityFocusState<Bool>.Binding

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(spacing: DL.Space.m) {
            caption
            replayGlyph
            // why: the gap word and the whole word occupy ONE slot — the answer
            // replaces the blank where it stood, so nothing below it moves.
            if let word = gapWord {
                Text(verbatim: word)
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                    .foregroundStyle(revealed == nil ? Color.dlTextPrimary : Color.dlAccent)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .contentTransition(.opacity)
            }
            // A dictation has no gap to close — the word grows below instead,
            // the same reveal a vocabulary card shows.
            if gapText == nil, let revealed {
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
        // why: the sibling drill card's height — an ordinary question holds it
        // exactly, so a run sits still and a learner moving between the two
        // drills meets one layout. A gap word is what may still grow it.
        .frame(minHeight: DL.Reserve.drillCard)
        .dlCardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed?.word)
    }

    /// What stands in the word slot: the blank while the question is open, the
    /// whole word once the answer is out. nil for a letter asked by its name,
    /// which never had a word on the card at all.
    private var gapWord: String? {
        guard gapText != nil else { return nil }
        return revealed?.word ?? gapText
    }

    private var caption: some View {
        Text.joined(Text(question),
                    Text("trainer.prompt.inLanguage \(LanguageNames.display(language, locale: locale, catalog: nil))"))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            .multilineTextAlignment(.center)
    }

    /// Big, but never circled or filled — it names what the card does rather
    /// than acting as a control styled to look like one.
    ///
    /// why: it keeps its generous tap target but reserves only the glyph in
    /// layout, overhanging into the gaps above and below — nothing there is
    /// tappable, and at full height it cost the card 36 pt of empty air.
    private var replayGlyph: some View {
        SpeakerIcon(size: .large, isPlaying: isPlaying, pronounce: replay)
            .accessibilityLabel("a11y.replayPrompt")
            .accessibilityAddTraits(.startsMediaSession)
            .accessibilityFocused(replayFocus)
            .frame(height: 52)
    }
}

// MARK: - Previews

private struct HearPromptPreviewHost: View {
    @AccessibilityFocusState private var focus: Bool

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            HearPromptCard(question: "letters.hear", language: "uk",
                           replay: {}, replayFocus: $focus)
            HearPromptCard(question: "letters.spell", language: "de",
                           gapText: "Na＿t", replay: {}, replayFocus: $focus)
            // The gap closed: the whole word stands where the blank did.
            HearPromptCard(question: "letters.spell", language: "de",
                           gapText: "Na＿t", revealed: .init(word: "Nacht", pronounce: {}),
                           replay: {}, replayFocus: $focus)
            // Dictation: no gap to close, so the word grows below the glyph.
            HearPromptCard(question: "letters.dictation", language: "sw",
                           revealed: .init(word: "lugha", note: "Sprache", pronounce: {}),
                           replay: {}, replayFocus: $focus)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground)
    }
}

#Preview("Hear prompt") {
    HearPromptPreviewHost()
}

#Preview("Hear prompt · dark") {
    HearPromptPreviewHost()
        .preferredColorScheme(.dark)
}
