import SwiftUI

/// The 2×2 a multiple-choice question is answered off, wherever one is asked:
/// the letters ladder's opening stages and the calendar's warm-up Sprosse.
///
/// What is shared is the VERDICT skin — the tints an answered tile takes, the
/// mark that carries correctness for anyone who cannot tell those tints apart,
/// the tile going dead once a pick has landed, and what VoiceOver hears. That
/// half must never drift between two drills; a learner reading a wrong pick as
/// right on one screen and not the other is one app behaving as two.
///
/// What is NOT shared is how an option is SET, which is the only real
/// difference: a letterform is a picture and is set at picture size, a calendar
/// name is prose. `label` covers the other one — a bare Cyrillic glyph read by
/// a German engine is a guess where "Buchstabe ч" is not, while a name needs no
/// help being read as itself.
struct DrillChoiceGrid: View {
    /// The options in kern's own shuffled order — both platforms render the
    /// same draw, so a seeded run is reproducible.
    let options: [String]
    /// Which of them is right; the grid marks it once a pick has landed.
    let answer: String
    /// What was picked, or nil while the question is still owed.
    let chosen: String?
    /// How one option is set on its tile.
    let font: Font
    /// What a screen reader hears in place of the bare text, where the bare
    /// text is not a word. nil ⇒ the text reads as itself.
    var label: ((String) -> Text)?
    let pick: (String) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: Theme.spacing.md),
                            GridItem(.flexible(), spacing: Theme.spacing.md)],
                  spacing: Theme.spacing.md) {
            ForEach(options, id: \.self) { option in
                tile(option)
            }
        }
        .animation(.easeOut(duration: 0.2), value: chosen)
    }

    private func tile(_ option: String) -> some View {
        let answered = chosen != nil
        let isAnswer = option == answer
        let isChosen = option == chosen
        return Button {
            pick(option)
        } label: {
            Text(verbatim: option)
                .font(font)
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                .frame(maxWidth: .infinity, minHeight: Theme.reserve.tile)
                .padding(Theme.spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                        .fill(fill(answered: answered, isAnswer: isAnswer, isChosen: isChosen))
                )
                // why: correctness is never color alone — the mark carries it
                // for anyone who cannot tell the two tints apart.
                .overlay(alignment: .topTrailing) {
                    mark(answered: answered, isAnswer: isAnswer, isChosen: isChosen)
                }
        }
        .buttonStyle(TrainerChipButtonStyle())
        .disabled(answered)
        .accessibilityLabel(label?(option) ?? Text(verbatim: option))
        .accessibilityValue(answered && isAnswer ? Text("a11y.verdict.correct") : Text(verbatim: ""))
    }

    private func fill(answered: Bool, isAnswer: Bool, isChosen: Bool) -> Color {
        guard answered else { return Theme.colors.surfaceTint }
        if isAnswer { return Theme.colors.success.opacity(0.22) }
        return isChosen ? Theme.colors.wrong.opacity(0.22) : Theme.colors.surfaceTint
    }

    @ViewBuilder
    private func mark(answered: Bool, isAnswer: Bool, isChosen: Bool) -> some View {
        if answered, isAnswer {
            markImage("checkmark.circle.fill", tint: Theme.colors.success)
        } else if answered, isChosen {
            markImage("xmark.circle.fill", tint: Theme.colors.wrong)
        }
    }

    private func markImage(_ symbol: String, tint: Color) -> some View {
        Image(systemName: symbol)
            .font(.title3)
            .foregroundStyle(tint)
            .padding(Theme.spacing.sm)
            .accessibilityHidden(true)
    }
}
