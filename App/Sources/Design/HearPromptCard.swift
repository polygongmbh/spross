import SwiftUI

/// The audio question, on the same card face as every other session card: a
/// caption naming what is being asked, one large replay glyph, and — for a gap
/// question — the example word with the asked grapheme blanked.
///
/// The caption stays here where the slot drill dropped its own, because a sound
/// has nothing on screen to say what it wants back: the same glyph asks for a
/// letter, for the grapheme missing from a word, or for the whole word. WHICH
/// language it is owed in is not its business — the field's placeholder already
/// says that, and a clause here would be the third telling (docs/surfaces.md).
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
    /// BCP-47 code of the language everything written on this card is in — the
    /// gap word, and the answer that closes it. Never shown: it tags them for
    /// VoiceOver (`spoken`), which is the only reading a screen-reader
    /// session gets, since nothing may autoplay over one.
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

    var body: some View {
        VStack(spacing: Theme.spacing.md) {
            caption
            replayGlyph
            // why: the gap word and the whole word occupy ONE slot — the answer
            // replaces the blank where it stood, so nothing below it moves.
            if let word = gapWord {
                Text(verbatim: word)
                    .font(Theme.prompt.word)
                    .foregroundStyle(revealed == nil ? Theme.colors.textPrimary : Theme.colors.accent)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .contentTransition(.opacity)
                    .spoken(word, language: language)
            }
            // A dictation has no gap to close — the word grows below instead,
            // the same reveal a vocabulary card shows.
            if gapText == nil, let revealed {
                CardReveal(note: revealed.note) {
                    SpokenWord(pronounce: revealed.pronounce, isPlaying: revealed.isPlaying) {
                        Text(verbatim: revealed.word)
                            .font(Theme.typography.title)
                            .foregroundStyle(Theme.colors.accent)
                            .multilineTextAlignment(.center)
                            .minimumScaleFactor(0.6)
                            .spoken(revealed.word, language: language)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        // why: the sibling drill card's height — an ordinary question holds it
        // exactly, so a run sits still and a learner moving between the two
        // drills meets one layout. A gap word is what may still grow it.
        .frame(minHeight: Theme.reserve.drillCard)
        .cardSurface()
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
        Text(question)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
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
            .accessibilityLabel("a11y.action.replayPrompt")
            .accessibilityAddTraits(.startsMediaSession)
            .accessibilityFocused(replayFocus)
            .frame(height: 52)
    }
}

// MARK: - Previews

private struct HearPromptPreviewHost: View {
    @AccessibilityFocusState private var focus: Bool

    var body: some View {
        VStack(spacing: Theme.spacing.xl) {
            HearPromptCard(question: "letters.ask.hear", language: "uk",
                           replay: {}, replayFocus: $focus)
            HearPromptCard(question: "letters.ask.spell", language: "de",
                           gapText: "Na＿t", replay: {}, replayFocus: $focus)
            // The gap closed: the whole word stands where the blank did.
            HearPromptCard(question: "letters.ask.spell", language: "de",
                           gapText: "Na＿t", revealed: .init(word: "Nacht", pronounce: {}),
                           replay: {}, replayFocus: $focus)
            // Dictation: no gap to close, so the word grows below the glyph.
            HearPromptCard(question: "letters.ask.dictation", language: "sw",
                           revealed: .init(word: "lugha", note: "Sprache", pronounce: {}),
                           replay: {}, replayFocus: $focus)
        }
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.colors.background)
    }
}

#Preview("Hear prompt") {
    HearPromptPreviewHost()
}

#Preview("Hear prompt · dark") {
    HearPromptPreviewHost()
        .preferredColorScheme(.dark)
}
