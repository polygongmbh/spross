import SwiftUI
import SprossKern

/// Simpler sibling of VocabCardView: one big prompt ("347", "1978", "14:35", a
/// word with its letters thrown out of order), and the same reveal growing below it.
///
/// The card carries NO drill label and no emoji: the run's header line already
/// names what is drilled ("🔢 1 Stelle"), the field's placeholder names the
/// language to answer in, and a card that repeats both spends the screen's
/// scarce axis saying what the learner just tapped their way into.
struct DrillPromptCard: View {

    /// How large the question is set, and how the reveal under it follows. WHAT
    /// is asked picks it — there is room for one numeral where there is none for
    /// a whole line (`Theme.Prompt`).
    enum Size {
        /// A numeral the whole card is about.
        case digits
        /// One word, whole or with its letters mixed.
        case word
        /// A prompt made of words, wrapped over lines.
        case sentence

        var font: Font {
            switch self {
            case .digits: return Theme.prompt.digits
            case .word: return Theme.prompt.word
            case .sentence: return Theme.prompt.sentence
            }
        }

        var lines: Int { self == .sentence ? 4 : 1 }

        var revealFont: Font {
            self == .sentence ? Theme.typography.headline : Theme.typography.title
        }
    }

    /// The question, set by whoever knows what it is made of — the learner's
    /// form of a numeral ("12 345", where the kern parses "12345" back), or a
    /// mixed word with the letters that still stand marked.
    let prompt: Text
    /// What a screen reader hears in its place, where the written form is not a
    /// word anything can read. nil ⇒ the prompt reads as itself.
    var promptLabel: Text?
    var size: Size = .digits
    /// The canonical answer, and the language it is said in.
    let answer: String
    let language: String
    /// The meaning — under the answer, and never before it.
    var gloss: String?
    /// A short fact about THIS prompt ("Neue Stelle: mia"), shown until the
    /// answer arrives ([DrillHint], the shape the calendar's card wears too).
    var hint: DrillHint?
    /// What a refused answer actually named ("setenta" is 70) — the nudge line
    /// under the reveal, worded by the review session's own key.
    var otherWord: (word: String, meanings: String)?
    /// The answer is out — the card grows it below the prompt, exactly like a
    /// vocabulary card, instead of a panel under the input field.
    var revealed = false
    /// Says the revealed answer — nil where it can neither be played nor
    /// spoken, which drops the icon rather than showing a dead one.
    var pronounce: (() -> Void)?
    var isPlaying = false

    var body: some View {
        VStack(spacing: Theme.spacing.md) {
            prompt
                .font(size.font)
                .monospacedDigit()
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(size.lines)
                .minimumScaleFactor(0.5)
                .multilineTextAlignment(.center)
                .accessibilityLabel(promptLabel ?? prompt)
            if revealed {
                CardReveal(note: gloss) {
                    SpokenWord(pronounce: pronounce, isPlaying: isPlaying) {
                        Text(answer)
                            .font(size.revealFont)
                            .foregroundStyle(Theme.colors.accent)
                            .multilineTextAlignment(.center)
                            .minimumScaleFactor(0.6)
                            .spoken(answer, language: language)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
                if let otherWord {
                    // why: same line as the review session's — both explain what
                    // became of the answer, so they read alike.
                    Text("session.otherWord \(otherWord.word) \(otherWord.meanings)")
                        .pauseLine()
                }
            } else if let hint {
                // why: the reveal TAKES this slot rather than stacking under it —
                // the hint is scaffolding for a prompt still unanswered.
                DrillHintPill(hint)
            }
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        // why: room for the prompt AND the hint pill, held whether or not the
        // pill is there, so the field and button below never move.
        .frame(minHeight: Theme.reserve.drillCard)
        .cardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed)
    }
}
