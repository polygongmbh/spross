import SwiftUI
import SprossKern

/// The tapped answer: the calendar's warm-up Sprosse, where four names stand
/// where the field otherwise would and the question is picked rather than
/// written. State lives on DrillRunView; the grid is here.
///
/// Not the letter drill's grid, though the shape is the same: a letterform is a
/// picture, set at picture size and announced as "Buchstabe ч" because a bare
/// Cyrillic glyph read by a German engine is a guess. A calendar name is prose —
/// it is set as prose, and a screen reader saying it needs no help.
extension DrillRunView {

    /// 2×2 of name tiles in kern's shuffled order — both platforms render the
    /// same draw, so a seeded run is reproducible.
    @ViewBuilder
    func choiceControls(_ names: [String]) -> some View {
        VStack(spacing: Theme.spacing.md) {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: Theme.spacing.md),
                                GridItem(.flexible(), spacing: Theme.spacing.md)],
                      spacing: Theme.spacing.md) {
                ForEach(names, id: \.self) { name in
                    tile(name)
                }
            }
            .animation(.easeOut(duration: 0.2), value: chosen)
            switch feedback {
            // why: nothing under an unanswered grid — the tiles ARE the action,
            // and a button beside them would be a second way to answer nothing.
            case .neutral, .almost:
                EmptyView()
            case .correct:
                // why: the timer never arms under a screen reader, so a clean
                // hit would otherwise have nothing to move on with.
                if screenReaderOn { nextButton(confirm) }
            case .revealed:
                revealedControls
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    private func tile(_ name: String) -> some View {
        let answered = chosen != nil
        let isAnswer = name == current.display
        let isChosen = name == chosen
        return Button {
            choose(name)
        } label: {
            Text(verbatim: name)
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, minHeight: Theme.reserve.tile)
                .padding(Theme.spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                        .fill(tileFill(answered: answered, isAnswer: isAnswer, isChosen: isChosen))
                )
                // why: correctness is never color alone — the mark carries it
                // for anyone who cannot tell the two tints apart.
                .overlay(alignment: .topTrailing) {
                    tileMark(answered: answered, isAnswer: isAnswer, isChosen: isChosen)
                }
        }
        .buttonStyle(TrainerChipButtonStyle())
        .disabled(answered)
        .accessibilityValue(answered && isAnswer ? Text("a11y.verdict.correct") : Text(verbatim: ""))
    }

    private func tileFill(answered: Bool, isAnswer: Bool, isChosen: Bool) -> Color {
        guard answered else { return Theme.colors.surfaceTint }
        if isAnswer { return Theme.colors.success.opacity(0.22) }
        return isChosen ? Theme.colors.wrong.opacity(0.22) : Theme.colors.surfaceTint
    }

    @ViewBuilder
    private func tileMark(answered: Bool, isAnswer: Bool, isChosen: Bool) -> some View {
        if answered, isAnswer {
            tileMarkImage("checkmark.circle.fill", tint: Theme.colors.success)
        } else if answered, isChosen {
            tileMarkImage("xmark.circle.fill", tint: Theme.colors.wrong)
        }
    }

    private func tileMarkImage(_ symbol: String, tint: Color) -> some View {
        Image(systemName: symbol)
            .font(.title3)
            .foregroundStyle(tint)
            .padding(Theme.spacing.sm)
            .accessibilityHidden(true)
    }
}
