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
    /// The answer is out — the card grows it below the prompt, exactly like a
    /// vocabulary card, instead of a panel under the input field.
    var revealed = false

    var body: some View {
        VStack(spacing: DL.Space.m) {
            Text(task.prompt)
                .font(.system(size: sentence ? 28 : 56, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(sentence ? 4 : 1)
                .minimumScaleFactor(0.5)
                .multilineTextAlignment(.center)
            if revealed {
                DLCardReveal(note: task.gloss) {
                    Text(task.display)
                        .font(sentence ? DL.Fonts.headline : DL.Fonts.title)
                        .foregroundStyle(Color.dlAccent)
                        .multilineTextAlignment(.center)
                        .minimumScaleFactor(0.6)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity)
        // why: compact enough that prompt + input + button clear the keyboard.
        .frame(minHeight: 185)
        .dlCardSurface()
        .animation(.easeOut(duration: 0.25), value: revealed)
    }
}
