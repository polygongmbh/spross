import SprossKern
import SwiftUI

/// Every card a question is asked on, drawn from kern's `Question`:
/// what stands on it, when it opens and what it closes on are kern's,
/// and this only sets them — so a drill card and a review card cannot drift
/// into two ideas of what a card shows.
///
/// The answer and the closing lines grow below the prompt only once `opens`;
/// before that the card holds the prompt and, at first sight, the hint.
struct QuestionCardView: View {

    /// What the screen around the card is — the card works its own layout and sizes out from that.
    enum Surface {
        /// A drill task above a field, tiles or a pad: the question at the fixed size
        /// its form picks (`Theme.Prompt`), held to the drill card's reserve.
        case drill
    }

    let question: Question
    var surface: Surface = .drill
    var voice: CardVoice = .silent
    /// What a screen reader hears for the prompt where its text is no word to read — a mixed word, spelled out.
    var promptLabel: Text?
    /// VoiceOver lands on the replay control as each question goes up, where the prompt is a sound.
    var replayFocus: AccessibilityFocusState<Bool>.Binding?

    @Environment(\.locale) var locale

    var body: some View {
        switch surface {
        case .drill: drillFace
        }
    }

    var opens: Bool { question.opens }

    var revealAnimation: Animation { .easeOut(duration: Double(CardMotion.shared.REVEAL_MS) / 1000) }

    // MARK: - A drill task

    /// The picture rides in the card's leading slot, mirrored on the far edge so the words stay
    /// centered in the card — unless the picture IS the question, when it stands where the words would.
    private var drillFace: some View {
        HStack(spacing: Theme.spacing.md) {
            if let emoji = besidePicture {
                CardEmoji(emoji, cue: question.emojiCue, revealed: opens)
            }
            VStack(spacing: Theme.spacing.md) {
                if let ask = question.ask {
                    caption(ask)
                }
                drillPrompt
                if opens {
                    // why: a gap question closes over its blank where it stood, so nothing below it moves.
                    if question.prompt.form != .gap {
                        CardReveal(note: noteText) { drillAnswer }
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                    if let other = question.otherWord {
                        // why: same line as the review session's — both explain what became of the answer.
                        Text("session.otherWord \(other.word) \(other.meanings.joined(separator: ", "))")
                            .pauseLine()
                    }
                } else if let hint = question.hint {
                    // why: the reveal TAKES this slot rather than stacking under it —
                    // the hint is scaffolding for a prompt still unanswered.
                    DrillHintPill(hintPill(hint))
                }
            }
            .frame(maxWidth: .infinity)
            if besidePicture != nil {
                CardEmoji.balance()
            }
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        // why: one height for every drill's ordinary question — the field below never jumps,
        // and a learner moving between drills meets one layout. A gap word may still grow it.
        .frame(minHeight: Theme.reserve.drillCard)
        .cardSurface()
        .animation(revealAnimation, value: opens)
    }

    private var besidePicture: String? {
        guard let emoji = question.emoji, !emoji.isEmpty, !question.emojiIsQuestion else { return nil }
        return emoji
    }

    @ViewBuilder
    private var drillPrompt: some View {
        let side = question.prompt
        switch side.form {
        case .sound:
            replayGlyph
        case .gap:
            replayGlyph
            gapLine
        default:
            if question.emojiIsQuestion, let emoji = question.emoji {
                // why: the flag IS the question here, so it takes the place and size the name
                // would have had, and stays readable to VoiceOver for the same reason.
                Text(verbatim: emoji)
                    .font(Theme.prompt.glyph)
            } else if let text = side.text {
                SpokenWord(pronounce: pronounce(side), isPlaying: isPlaying(side)) {
                    promptText(side, text)
                        .font(promptFont(side.form))
                        .foregroundStyle(Theme.colors.textPrimary)
                        .lineLimit(promptLines(side.form))
                        .minimumScaleFactor(0.5)
                        .multilineTextAlignment(.center)
                        .accessibilityLabel(promptLabel ?? promptReading(side, text))
                }
            }
        }
    }

    /// The blanked word while the question stands, the whole word in the answer's accent once it opens.
    @ViewBuilder
    private var gapLine: some View {
        let word = opens ? question.answer.text : question.prompt.text
        if let word {
            Text(verbatim: word)
                .font(Theme.prompt.word)
                .foregroundStyle(opens ? Theme.colors.accent : Theme.colors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .contentTransition(.opacity)
                .spoken(word, language: question.prompt.lang)
        }
    }

    @ViewBuilder
    private var drillAnswer: some View {
        let side = question.answer
        if let text = side.text {
            SpokenWord(pronounce: pronounce(side), isPlaying: isPlaying(side)) {
                Text(verbatim: text)
                    .font(drillAnswerFont)
                    .foregroundStyle(Theme.colors.accent)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.6)
                    .spoken(text, language: side.lang)
            }
        }
    }

    /// Big, but never circled or filled — it names what the card does rather than looking like a button.
    ///
    /// why: it keeps its generous tap target but reserves only the glyph in layout,
    /// overhanging into the gaps above and below, where nothing is tappable.
    private var replayGlyph: some View {
        SpeakerIcon(size: .large, isPlaying: isPlaying(question.prompt), pronounce: pronounce(question.prompt))
            .accessibilityLabel("a11y.action.replayPrompt")
            .accessibilityAddTraits(.startsMediaSession)
            .modifier(ReplayFocus(focus: replayFocus))
            .frame(height: 52)
    }

    private func caption(_ ask: QuestionAsk) -> some View {
        Text(askKey(ask))
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
            .textCase(.uppercase)
            .multilineTextAlignment(.center)
    }

    // MARK: - Speakers

    func pronounce(_ side: Question.Side) -> (() -> Void)? {
        side.saying.flatMap(voice.pronounce)
    }

    func isPlaying(_ side: Question.Side) -> Bool {
        side.saying.map(voice.isPlaying) ?? false
    }
}

/// What a card's speakers do: the tap that says a side's `Saying`, and whether it is sounding now.
/// A nil tap drops the speaker beside a word, and leaves a replay glyph dimmed and inert.
struct CardVoice {
    var pronounce: (Saying) -> (() -> Void)?
    var isPlaying: (Saying) -> Bool

    static var silent: CardVoice { CardVoice(pronounce: { _ in nil }, isPlaying: { _ in false }) }
}

extension AppModel {
    /// The tap on a card's speaker, which speaks even while reading aloud is off.
    var cardVoice: CardVoice {
        CardVoice(pronounce: { self.pronounceAction(for: $0.form, lang: $0.lang, article: $0.article) },
                  isPlaying: { self.isPronouncing($0.form, lang: $0.lang) })
    }
}

private struct ReplayFocus: ViewModifier {
    let focus: AccessibilityFocusState<Bool>.Binding?

    func body(content: Content) -> some View {
        if let focus {
            content.accessibilityFocused(focus)
        } else {
            content
        }
    }
}
