import SwiftUI
import SprossKern

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
        // The tile's state and what it announces are kern's (`ChoiceTile`).
        let state = ChoiceTile.companion.of(option: option, answer: answer, chosen: chosen)
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
                        .fill(fill(state))
                )
                .overlay(alignment: .topTrailing) { mark(state) }
        }
        .buttonStyle(ChipButtonStyle())
        .disabled(state != .open)
        .accessibilityLabel(label?(option) ?? Text(verbatim: option))
        .accessibilityValue(spoken(state.verdict))
    }

    private func fill(_ state: ChoiceTile) -> Color {
        if state == .answer { return Theme.colors.success.opacity(Palette.shared.WASH) }
        if state == .wrongPick { return Theme.colors.wrong.opacity(Palette.shared.WASH) }
        return Theme.colors.surfaceTint
    }

    private func spoken(_ verdict: ChoiceVerdict?) -> Text {
        if verdict == .correct { return Text("a11y.verdict.correct") }
        if verdict == .wrong { return Text("a11y.verdict.wrong") }
        return Text(verbatim: "")
    }

    @ViewBuilder
    private func mark(_ state: ChoiceTile) -> some View {
        if state == .answer {
            markImage("checkmark.circle.fill", tint: Theme.colors.success)
        } else if state == .wrongPick {
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
