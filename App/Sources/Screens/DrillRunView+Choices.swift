import SwiftUI
import SprossKern

/// The tapped answer: the calendar's warm-up Sprosse, where four names stand
/// where the field otherwise would and the question is picked rather than
/// written. State lives on DrillRunView; what a pick leaves behind is here.
extension DrillRunView {

    /// 2×2 of name tiles, and what follows a pick — the grid itself is
    /// `DrillChoiceGrid`, shared with the letters ladder's own choice stages.
    @ViewBuilder
    func choiceControls(_ names: [String]) -> some View {
        VStack(spacing: Theme.spacing.md) {
            // A calendar name is prose: it is set as prose, and a screen reader
            // saying it needs no help, where a bare glyph would.
            DrillChoiceGrid(options: names,
                            answer: current.display,
                            chosen: chosen,
                            font: Theme.typography.headline,
                            pick: choose)
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
}
