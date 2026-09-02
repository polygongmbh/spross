import SwiftUI
import SprossKern

/// Simpler sibling of VocabCardView: one big tabular-digit prompt ("347",
/// "1978", "14:35"), and the same reveal growing below it.
///
/// The card carries NO drill label and no emoji: the run's header line already
/// names what is drilled ("🔢 1 Stelle"), the field's placeholder names the
/// language to answer in, and a card that repeats both spends the screen's
/// scarce axis saying what the learner just tapped their way into.
struct TrainerPromptCard: View {
    let task: TrainerTask
    var sentence = false
    /// A short fact about THIS prompt ("Neue Stelle: mia"), shown until the
    /// answer arrives. It rides inside the card so its coming and going never
    /// moves the field or the button below — see `hintPill`.
    struct Hint {
        let icon: String
        let text: LocalizedStringKey
    }

    var hint: Hint?
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
            // why: promptDisplay is the learner's form — grouped digits ("12 345")
            // where `prompt` is the machine one the kern parses back with toLong().
            Text(task.promptDisplay)
                .font(sentence ? Theme.prompt.sentence : Theme.prompt.digits)
                .monospacedDigit()
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(sentence ? 4 : 1)
                .minimumScaleFactor(0.5)
                .multilineTextAlignment(.center)
            if revealed {
                DLCardReveal(note: task.gloss) {
                    DLSpokenWord(pronounce: pronounce, isPlaying: isPlaying) {
                        Text(task.display)
                            .font(sentence ? Theme.typography.headline : Theme.typography.title)
                            .foregroundStyle(Theme.colors.accent)
                            .multilineTextAlignment(.center)
                            .minimumScaleFactor(0.6)
                            .dlSpoken(task.display, language: task.language)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
                if let otherWord {
                    // why: same line as the review session's — both explain what
                    // became of the answer, so they read alike.
                    Text("session.otherWord \(otherWord.word) \(otherWord.meanings)")
                        .dlPauseLine()
                }
            } else if let hint {
                // why: the reveal TAKES this slot rather than stacking under it —
                // the hint is scaffolding for a prompt still unanswered.
                hintPill(hint)
            }
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        // why: room for the prompt AND the hint pill, held whether or not the
        // pill is there, so the field and button below never move.
        .frame(minHeight: Theme.reserve.drillCard)
        .dlCardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed)
    }

    private func hintPill(_ hint: Hint) -> some View {
        Label(hint.text, systemImage: hint.icon)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.accent)
            .padding(.horizontal, Theme.spacing.md)
            .padding(.vertical, Theme.spacing.sm)
            .background(
                Capsule().fill(Theme.colors.surfaceTint)
            )
    }
}
