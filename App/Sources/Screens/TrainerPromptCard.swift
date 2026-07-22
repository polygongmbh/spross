import SwiftUI
import SprossKern

/// Simpler sibling of VocabCardView: same card framing, one big
/// tabular-digit prompt ("347", "1978", "14:35").
struct TrainerPromptCard: View {
    let task: TrainerTask
    var sentence = false

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(spacing: DL.Space.m) {
            Text(sentence ? "💬" : task.kind.trainerEmoji)
                .font(.system(size: 36))
                .padding(DL.Space.s + 2)
                .background(Circle().fill(Color.dlSurfaceTint))
                .accessibilityHidden(true)
            Text.joined(sentence ? Text("Satz") : Text(task.kind.trainerPromptLabelKey),
                        Text("auf \(LanguageNames.display(task.language, locale: locale, catalog: nil))"))
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .textCase(.uppercase)
            Text(task.prompt)
                .font(.system(size: sentence ? 28 : 56, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(sentence ? 4 : 1)
                .minimumScaleFactor(0.5)
                .multilineTextAlignment(.center)
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity)
        // why: compact enough that prompt + input + button clear the keyboard.
        .frame(minHeight: 185)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .strokeBorder(Color.dlSeparator.opacity(0.6), lineWidth: 1)
        )
        .dlCardShadow()
    }
}
