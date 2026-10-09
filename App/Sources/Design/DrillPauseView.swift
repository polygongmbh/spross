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
    private var celebrated: Bool { run.pause?.celebrated ?? false }

    var body: some View {
        SummaryScaffold(title: Text(title),
                        tally: Text("trainer.result.tasksDone \(Int(run.done))"),
                        milestone: milestone,
                        hint: run.pause == DrillPauseReason.struggling
                            ? Text("trainer.pause.struggling.hint") : nil,
                        onDone: onDone,
                        onPractice: onKeepPracticing) { _ in SummaryGlyph(glyph: (run.pause ?? DrillPauseReason.count).emoji) }
        // why: a pause is a round's end the run may go on from, celebrated as the
        // round summary is — confetti and cheer are one thing (`docs/design.md`).
        .overlay {
            if celebrated { ConfettiView().ignoresSafeArea().allowsHitTesting(false) }
        }
        .onAppear { if celebrated { Sound.cheer() } }
    }

    /// What the stretch reached, only where it reached something: the climb
    /// from the Sprosse the run opened on, and a record beaten.
    private var milestone: Text? {
        var parts: [Text] = []
        if let climb = pacing.climbed {
            parts.append(Text("trainer.pause.sprossen \(Int(climb.from).formatted()) \(Int(climb.to).formatted())"))
        }
        if pacing.newRecord { parts.append(Text("trainer.result.newRecord")) }
        return parts.joined()
    }

    private var title: LocalizedStringKey {
        if run.pause == DrillPauseReason.improved { return "trainer.pause.title.improved" }
        if run.pause == DrillPauseReason.struggling { return "trainer.pause.title.struggling" }
        return "trainer.pause.title.count"
    }
}
