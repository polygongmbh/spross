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
            // Nothing under an unanswered grid: the tiles ARE the action.
            DrillVerdictControls(feedback: feedback, onConfirm: { confirm() }, onStop: stopOffer)
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }
}
