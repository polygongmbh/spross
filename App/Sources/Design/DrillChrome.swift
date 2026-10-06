import SprossKern
import SwiftUI

// MARK: - Drill chrome
//
// What the two endless drills — the slot drill and the letter drill — put
// around whatever they happen to be asking. Their state machines stay apart
// (a heard glyph and a typed numeral share no grammar); only the frame does.

extension SessionScaffold {
    /// The chrome of an ENDLESS drill run, which has no total to count
    /// toward — the counter is the run's own `DrillTally` rather than
    /// "position/total", which answers each half of it counts is kern's
    /// rule, and the slash is all this adds (`SessionScaffold.running`).
    static func endless(tally: DrillTally,
                        outcomes: [SessionOutcome],
                        showsMuteButton: Bool = false,
                        speaksPastMute: Bool = false,
                        scoreLine: some View,
                        onClose: @escaping () -> Void,
                        @ViewBuilder content: () -> Content) -> SessionScaffold {
        .running(endless: true,
                outcomes: outcomes,
                counter: "\(tally.clean)/\(tally.judged)",
                showsMuteButton: showsMuteButton,
                speaksPastMute: speaksPastMute,
                status: AnyView(scoreLine),
                onClose: onClose,
                content: content)
    }
}

// MARK: - Streak line

/// The score line above the card: which Sprosse the run stands on and how long
/// the answer streak is. The record stays off it — a record is named where it
/// falls, on the pause and the result tile, never counted mid-run.
struct DrillStreakLine: View {
    /// The Sprosse, worded by the drill that owns it — a digit count reads
    /// differently from a plain Sprosse. nil where a run has one Sprosse only.
    var sprosse: Text?
    /// A timed run's clock and score, standing after the Sprosse; empty elsewhere.
    var timed: [Text] = []
    let answerStreak: Int

    var body: some View {
        text
            .font(Theme.typography.headline)
            .foregroundStyle(answerStreak > 0 ? Theme.colors.accent : Theme.colors.textSecondary)
            .monospacedDigit()
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .animation(.easeOut(duration: 0.2), value: answerStreak)
            .accessibilityLabel(accessibility)
    }

    /// Composed as `Text` (not a joined String) so each part localizes via the
    /// environment locale with catalog plural handling.
    private var text: Text {
        var parts: [Text] = []
        if let sprosse { parts.append(sprosse) }
        parts += timed
        parts.append(Text("trainer.run.streak \(answerStreak.formatted())"))
        return parts.joined() ?? Text(verbatim: "")
    }

    private var accessibility: Text {
        let streakSpoken = Text("a11y.count.streakInARow \(answerStreak.formatted())")
        return timed.joined(separator: ", ").map { $0 + Text(verbatim: ", ") + streakSpoken } ?? streakSpoken
    }
}

// MARK: - The way out, offered where it is wanted

/// "Fertig", under the button that goes on. Between the pauses kern calls
/// (`DrillPacing`) an endless run has no end of its own, so the offer is tied to the one moment a learner is actually weighing it: the
/// SECOND miss in a row. One miss is what a drill is made of; two is where
/// carrying on stops feeling like a choice, and the corner ✕ reads as abandoning
/// something rather than finishing it. The same word the session summary stops
/// on — where it sits says "for now", so the words need not.
struct DrillStopOffer: View {
    let action: () -> Void

    var body: some View {
        Button("common.done", action: action)
            .buttonStyle(SoftButtonStyle())
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
    let bestAnswerStreak: Int
    /// The run beat the drill's standing record. A drill that keeps no record
    /// store leaves it false, which drops the record line and the confetti with it.
    var newRecord = false
    /// Which Sprosse the best answer streak earned. Kern's (`DrillRunSummary.milestone`) — the
    /// ladder is one table, and a second copy of it here is one coincidence away
    /// from praising a run the engine does not.
    var milestone: AnswerStreakMilestone = .sprout
    /// A timed run's score, and the challenge it answered; nil for every other run.
    var timed: TimedOutcome?
    /// Long enough to report where nothing asked for it — kern's (`DrillRunSummary.worthReporting`).
    var worthReporting = true
    /// The close earns confetti and the cheer — kern's (`DrillRunSummary.celebrated`).
    var celebrated = false
    /// What was drilled — the exercise's own name, since a page can host several.
    let title: LocalizedStringKey
}

/// The result as the overview wears it: one tile above the picks, where the
/// button that starts the next run already is.
struct DrillResultTile: View {
    let result: DrillRunResult
    @Environment(\.locale) private var locale

    var body: some View {
        HStack(alignment: .center, spacing: Theme.spacing.lg) {
            Text(verbatim: emoji)
                .font(.system(size: 40)) // card-parity: the tile's own glyph, not a card prompt
                .sway(angle: 4, period: 3.4)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("trainer.result.tasksDone \(result.doneCount)")
                    .font(Theme.typography.headline)
                    .foregroundStyle(Theme.colors.textPrimary)
                Text("trainer.result.bestStreak \(result.bestAnswerStreak.formatted())")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                if let timed = result.timed {
                    Text("trainer.run.score \(Int(timed.score))")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textPrimary)
                    if let verdict = verdictLine(timed) {
                        verdict
                            .font(Theme.typography.caption)
                            .foregroundStyle(Theme.colors.accent)
                    }
                    if let reply = timed.replyCode {
                        ShareLink(item: message(score: Int(timed.score), code: reply)) {
                            Label { Text("trainer.challenge.send \(reply)") }
                                icon: { Image(systemName: "square.and.arrow.up") }
                        }
                        .font(Theme.typography.caption)
                    }
                }
                if result.newRecord {
                    Text("trainer.result.newRecord")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.accent)
                }
            }
            Spacer(minLength: 0)
            Text(result.title)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
        .panelSurface(Theme.colors.surfaceTint)
        // why: one VoiceOver stop, unless a share button must stay reachable.
        .accessibilityElement(children: result.timed?.replyCode == nil ? .combine : .contain)
    }

    /// How the score stands against the one a challenge's code arrived with.
    private func verdictLine(_ timed: TimedOutcome) -> Text? {
        guard let verdict = timed.verdict,
              let theirs = timed.challenge?.opponentScore.map({ Int(truncating: $0) }) else { return nil }
        switch verdict {
        case .won: return Text("trainer.challenge.won \(theirs)")
        case .tied: return Text("trainer.challenge.tied \(theirs)")
        case .lost: return Text("trainer.challenge.lost \(theirs)")
        }
    }

    /// The share text, resolved by hand against the chrome locale.
    private func message(score: Int, code: String) -> String {
        let format = ChromeStrings.string("trainer.challenge.message %lld %@", locale: locale)
        return String(format: format, score, code)
    }

    /// The face of the Sprosse kern says the run reached.
    private var emoji: String {
        switch result.milestone {
        case .trophy: return "🏆"
        case .cheer: return "🎉"
        case .effort: return "💪"
        case .sprout: return "🌱"
        }
    }
}

// MARK: - Previews

#Preview("Result tile · record") {
    VStack(spacing: Theme.spacing.lg) {
        DrillResultTile(result: DrillRunResult(doneCount: 17, bestAnswerStreak: 12, newRecord: true,
                                               milestone: .trophy, title: "trainer.drill.numbers"))
        DrillResultTile(result: DrillRunResult(doneCount: 4, bestAnswerStreak: 1, title: "trainer.drill.letters"))
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Streak line") {
    VStack(spacing: Theme.spacing.xl) {
        DrillStreakLine(sprosse: Text("trainer.sprosse \(7.formatted())"), answerStreak: 0)
        DrillStreakLine(sprosse: Text("numbers.sprosse \(5)"), answerStreak: 7)
        DrillStreakLine(answerStreak: 3)
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

// MARK: - The Sprosse circle

/// What a Sprosse circle says about a ladder's record: never stood on, stood on
/// by some run (ocean), or answered out by one (forest) — the last only where the
/// Sprosse enumerates. Untouched differs by SHAPE too: an outline against two fills.
enum SprosseMark {
    case untouched, reached, cleared

    var color: Color {
        switch self {
        case .untouched: return Theme.colors.textSecondary
        case .reached: return Theme.colors.teal
        case .cleared: return Theme.colors.success
        }
    }

    /// What VoiceOver says of the row, where the shape says something.
    var a11y: LocalizedStringKey? {
        switch self {
        case .untouched: return nil
        case .reached: return "a11y.trainer.sprosse.reached"
        case .cleared: return "a11y.trainer.sprosse.cleared"
        }
    }
}

/// A Sprosse's number in its circle — the mark on every ladder row. The letters
/// ladder wears it per stage: forest where a run climbed the stage off clean,
/// filled on the stage its run opens on.
struct SprosseCircle: View {
    let number: Int
    let mark: SprosseMark

    var body: some View {
        Image(systemName: mark == .untouched ? "\(number).circle" : "\(number).circle.fill")
            .font(.title3)
            .foregroundStyle(mark.color)
    }
}
