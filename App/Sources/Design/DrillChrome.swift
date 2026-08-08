import SwiftUI

// MARK: - Drill chrome
//
// What the two endless drills — the slot drill and the letter drill — put
// around whatever they happen to be asking. Their state machines stay apart
// (a heard glyph and a typed numeral share no grammar); only the frame does.

extension SessionScaffold {
    /// The chrome of an ENDLESS run, which has no total to count toward.
    /// Position and total move together, so the bar fills as the run grows
    /// instead of breaking past a fixed end, and the counter reads
    /// "clean/answered" rather than "position/total".
    static func endless(answered: Int,
                        outcomes: [SessionOutcome],
                        showsMuteButton: Bool = false,
                        onClose: @escaping () -> Void,
                        @ViewBuilder content: () -> Content) -> SessionScaffold {
        SessionScaffold(position: answered + 1,
                        total: answered + 1,
                        outcomes: outcomes,
                        counter: "\(outcomes.filter { $0 != .wrong }.count)/\(answered)",
                        showsMuteButton: showsMuteButton,
                        onClose: onClose,
                        content: content)
    }
}

// MARK: - Streak line

/// The score line above the card: which rung the run stands on, how long the
/// streak is, and the standing record once the streak has fallen short of it.
struct DrillStreakLine: View {
    /// The rung, worded by the drill that owns it — a digit count reads
    /// differently from a plain level. nil where a run has one rung only.
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
        parts.append(Text("trainer.streak \(streak.formatted())"))
        if bestStreak > streak { parts.append(Text("trainer.record \(bestStreak.formatted())")) }
        return parts.joined() ?? Text(verbatim: "")
    }

    private var accessibility: Text {
        let spoken = Text("a11y.streakInARow \(streak.formatted())")
        guard announcesRecord, bestStreak > streak else { return spoken }
        return spoken + Text("a11y.recordSuffix \(bestStreak.formatted())")
    }
}

// MARK: - Close summary

/// What a closed run shows: how much was answered, the best streak it held,
/// what was drilled — and the way out, in both corners a thumb looks in.
struct DrillSummaryView: View {
    let doneCount: Int
    let bestStreak: Int
    /// The run beat the drill's standing record. A drill that keeps no record
    /// store leaves it false, which drops the record line and the confetti
    /// with it — rather than forking this screen in two.
    var newRecord = false
    /// What was drilled …
    let title: LocalizedStringKey
    /// … and in which language, already named (the drill holds the catalog
    /// that can name it; this screen does not).
    let languageName: String
    let onDone: () -> Void
    let onPractice: () -> Void

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            Text(verbatim: summaryEmoji)
                .font(.system(size: 72))
                .dlSway(angle: 4, period: 3.4)
                .accessibilityHidden(true)
            Text("trainer.tasksDone \(doneCount)")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            VStack(spacing: DL.Space.s) {
                Text("trainer.bestStreak \(bestStreak.formatted())")
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
                if newRecord {
                    Text("trainer.newRecord")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlAccent)
                }
            }
            Text.joined(Text(title), Text(verbatim: languageName))
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
            Spacer()
            SessionExitButtons(onDone: onDone, onPractice: onPractice)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        // why: confetti is what a record costs — a drill can be closed a dozen
        // times an evening, and a screen that celebrates every close celebrates
        // nothing. The run itself always sways; only the record rains.
        .overlay {
            if newRecord { ConfettiView().ignoresSafeArea() }
        }
        .sessionCloseCorner(label: "common.done", action: onDone)
    }

    /// The ladder the run's best streak earns.
    private var summaryEmoji: String {
        switch bestStreak {
        case 10...: return "🏆"
        case 5...: return "🎉"
        case 2...: return "💪"
        default: return "🌱"
        }
    }
}

// MARK: - Previews

#Preview("Summary · record") {
    DrillSummaryView(doneCount: 17, bestStreak: 12, newRecord: true,
                     title: "trainer.numbers", languageName: "Swahili",
                     onDone: {}, onPractice: {})
}

#Preview("Summary · no record store") {
    DrillSummaryView(doneCount: 17, bestStreak: 12,
                     title: "trainer.letters", languageName: "Ukrainisch",
                     onDone: {}, onPractice: {})
}

#Preview("Streak line") {
    VStack(spacing: DL.Space.xl) {
        DrillStreakLine(level: Text("trainer.level \(7.formatted())"), streak: 0, bestStreak: 0)
        DrillStreakLine(level: Text("trainer.digits \(5)"), streak: 7, bestStreak: 12,
                        announcesRecord: true)
        DrillStreakLine(streak: 3, bestStreak: 3)
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
