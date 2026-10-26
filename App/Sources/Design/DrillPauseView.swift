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
                        onPractice: onKeepPracticing,
                        celebrated: celebrated) { _, _ in SummaryGlyph(glyph: (run.pause ?? DrillPauseReason.count).emoji) }
    }

    /// What the stretch reached, only where it reached something: the highest
    /// Sprosse, where above the one the run opened on, and a record beaten.
    private var milestone: Text? {
        var parts: [Text] = []
        if let peak = pacing.peak {
            // why: the key is built as a STRING and only then wrapped, as
            // `SessionSummaryView.headlineKey` does — three lines in the string table.
            let key = "trainer.pause.sprosse.\(Int(pacing.peakLine(count: 3))) %@"
            parts.append(Text(String(format: NSLocalizedString(key, comment: ""), peak.intValue.formatted())))
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
