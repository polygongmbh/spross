import SprossKern
import SwiftUI

// MARK: - Drill chrome
//
// What the two endless drills — the slot drill and the letter drill — put
// around whatever they happen to be asking. Their state machines stay apart
// (a heard glyph and a typed numeral share no grammar); only the frame does.

extension SessionScaffold {
    /// The chrome of an ENDLESS run, which has no total to count toward.
    /// Position and total move together, so the bar fills as the run grows
    /// instead of breaking past a fixed end, and the counter is the run's own
    /// `DrillTally` rather than "position/total" — which answers each half of
    /// it counts is kern's rule, and the slash is all this adds.
    static func endless(tally: DrillTally,
                        outcomes: [SessionOutcome],
                        showsMuteButton: Bool = false,
                        onClose: @escaping () -> Void,
                        @ViewBuilder content: () -> Content) -> SessionScaffold {
        SessionScaffold(position: outcomes.count + 1,
                        total: outcomes.count + 1,
                        outcomes: outcomes,
                        counter: "\(tally.clean)/\(tally.judged)",
                        showsMuteButton: showsMuteButton,
                        onClose: onClose,
                        content: content)
    }
}

// MARK: - Streak line

/// The score line above the card: which Sprosse the run stands on, how long the
/// streak is, and the standing record once the streak has fallen short of it.
struct DrillStreakLine: View {
    /// The Sprosse, worded by the drill that owns it — a digit count reads
    /// differently from a plain level. nil where a run has one Sprosse only.
    var level: Text?
    let streak: Int
    let bestStreak: Int
    /// Whether the SPOKEN line names the record as well. Only the slot drill's
    /// does; the letter drill has always announced the streak alone, and this
    /// carries that difference rather than quietly settling it.
    var announcesRecord = false

    var body: some View {
        text
            .font(DL.Fonts.caption)
            .foregroundStyle(streak > 0 ? Color.dlAccent : Color.dlTextSecondary)
            .monospacedDigit()
            .frame(maxWidth: .infinity)
            .animation(.easeOut(duration: 0.2), value: streak)
            .accessibilityLabel(accessibility)
    }

    /// Composed as `Text` (not a joined String) so each part localizes via the
    /// environment locale with catalog plural handling.
    private var text: Text {
        var parts: [Text] = []
        if let level { parts.append(level) }
        parts.append(Text("trainer.run.streak \(streak.formatted())"))
        if bestStreak > streak { parts.append(Text("trainer.run.record \(bestStreak.formatted())")) }
        return parts.joined() ?? Text(verbatim: "")
    }

    private var accessibility: Text {
        let spoken = Text("a11y.count.streakInARow \(streak.formatted())")
        guard announcesRecord, bestStreak > streak else { return spoken }
        return spoken + Text("a11y.suffix.record \(bestStreak.formatted())")
    }
}

// MARK: - The way out, offered where it is wanted

/// "Fertig", under the button that goes on. An endless run has no natural end,
/// so the offer is tied to the one moment a learner is actually weighing it: the
/// SECOND miss in a row. One miss is what a drill is made of; two is where
/// carrying on stops feeling like a choice, and the corner ✕ reads as abandoning
/// something rather than finishing it. The same word the session summary stops
/// on — where it sits says "for now", so the words need not.
struct DrillStopOffer: View {
    let action: () -> Void

    var body: some View {
        Button("common.done", action: action)
            .buttonStyle(DLSoftButtonStyle())
            .transition(.opacity)
    }
}

// MARK: - What a closed run leaves behind

/// The whole of what a finished run has to say. It travels back to the overview
/// that started it rather than filling a screen of its own: three figures do not
/// earn a page, and a page they do not earn is one more ✕ between the learner
/// and the next run.
struct DrillRunResult: Equatable {
    let doneCount: Int
    let bestStreak: Int
    /// The run beat the drill's standing record. A drill that keeps no record
    /// store leaves it false, which drops the record line and the confetti with it.
    var newRecord = false
    /// Which Sprosse the best streak earned. Kern's (`DrillRunSummary.tier`) — the
    /// ladder is one table, and a second copy of it here is one coincidence away
    /// from praising a run the engine does not.
    var tier: StreakTier = .sprout
    /// What was drilled — the variant's own name, since a page can host several.
    let title: LocalizedStringKey
}

/// The result as the overview wears it: one tile above the picks, where the
/// button that starts the next run already is.
struct DrillResultTile: View {
    let result: DrillRunResult

    var body: some View {
        HStack(alignment: .center, spacing: DL.Space.l) {
            Text(verbatim: emoji)
                .font(.system(size: 40))
                .dlSway(angle: 4, period: 3.4)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("trainer.result.tasksDone \(result.doneCount)")
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                Text("trainer.result.bestStreak \(result.bestStreak.formatted())")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                if result.newRecord {
                    Text("trainer.result.newRecord")
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlAccent)
                }
            }
            Spacer(minLength: 0)
            Text(result.title)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurfaceTint)
        )
        // why: one VoiceOver stop — the figures describe a single run.
        .accessibilityElement(children: .combine)
    }

    /// The face of the Sprosse kern says the run reached.
    private var emoji: String {
        switch result.tier {
        case .trophy: return "🏆"
        case .cheer: return "🎉"
        case .effort: return "💪"
        case .sprout: return "🌱"
        }
    }
}

// MARK: - Previews

#Preview("Result tile · record") {
    VStack(spacing: DL.Space.l) {
        DrillResultTile(result: DrillRunResult(doneCount: 17, bestStreak: 12, newRecord: true,
                                               tier: .trophy, title: "trainer.skill.numbers"))
        DrillResultTile(result: DrillRunResult(doneCount: 4, bestStreak: 1, title: "trainer.skill.letters"))
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Streak line") {
    VStack(spacing: DL.Space.xl) {
        DrillStreakLine(level: Text("trainer.sprosse \(7.formatted())"), streak: 0, bestStreak: 0)
        DrillStreakLine(level: Text("numbers.sprosse \(5)"), streak: 7, bestStreak: 12,
                        announcesRecord: true)
        DrillStreakLine(streak: 3, bestStreak: 3)
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
