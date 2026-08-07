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

// MARK: - Previews

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
