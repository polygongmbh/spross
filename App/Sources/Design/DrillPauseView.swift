import SprossKern
import SwiftUI

/// Where an endless drill run stops to ask whether to go on — kern's
/// `DrillPacing`, which says when and why; this only draws it. It stands in
/// place of the question inside the run's own full screen, on the session
/// summary's own scaffold: Done closes the run as the ✕ does, keep practicing
/// carries the same run on.
struct DrillPauseView: View {
    let run: any DrillRunProgress
    var onDone: () -> Void
    var onKeepPracticing: () -> Void

    private var pacing: DrillPacing { run.pacing }

    var body: some View {
        SummaryScaffold(title: Text(title),
                        tally: Text("trainer.result.tasksDone \(Int(run.done))"),
                        hint: run.pause == DrillPauseReason.struggling
                            ? Text("trainer.pause.struggling.hint") : nil,
                        onDone: onDone,
                        onPractice: onKeepPracticing,
                        hero: { _ in SummaryGlyph(glyph: glyph) },
                        details: { figures })
    }

    /// What the run has done beyond its count: how many landed clean, the best
    /// answer streak, the climb — and a note only where something new was reached.
    private var figures: some View {
        VStack(spacing: Theme.spacing.xs) {
            Text("trainer.pause.tally \(Int(run.tally.clean).formatted()) \(Int(run.tally.judged).formatted())")
            Text("trainer.result.bestStreak \(Int(run.bestAnswerStreak).formatted())")
            if let climb { climb }
            if pacing.newSprossen > 0 {
                Text("trainer.pause.newSprosse").foregroundStyle(Theme.colors.accent)
            }
            if pacing.newRecord {
                Text("trainer.result.newRecord").foregroundStyle(Theme.colors.accent)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// The Sprosse the run opened on and the highest it reached — one Sprosse
    /// where it has not moved, nothing where it climbs several ladders at once.
    private var climb: Text? {
        guard let opened = pacing.openedOn?.intValue, let reached = pacing.reached?.intValue else { return nil }
        if reached > opened {
            return Text("trainer.pause.sprossen \(opened.formatted()) \(reached.formatted())")
        }
        return Text("trainer.sprosse \(reached.formatted())")
    }

    private var title: LocalizedStringKey {
        if run.pause == DrillPauseReason.improved { return "trainer.pause.title.improved" }
        if run.pause == DrillPauseReason.struggling { return "trainer.pause.title.struggling" }
        return "trainer.pause.title.count"
    }

    private var glyph: String {
        if run.pause == DrillPauseReason.improved { return "🎉" }
        if run.pause == DrillPauseReason.struggling { return "☕️" }
        return "💪"
    }
}
