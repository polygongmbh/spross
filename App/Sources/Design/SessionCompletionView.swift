import SwiftUI

// MARK: - SessionCompletionView
//
// "Geschafft!" — warm and playful. Confetti falls over the whole screen
// (ConfettiView) while an emoji burst opens under it; the cheer sounds once
// as the screen arrives. Tapping anywhere but the buttons replays all three.

struct SessionCompletionView: View {
    var newCount: Int = 0
    var graduatedCount: Int = 0
    let reviewCount: Int
    let streakDays: Int
    /// Today's run is the longest the box has ever held (`BoxStatistics`), so the
    /// streak is worth naming rather than just counting.
    var streakIsRecord: Bool = false
    /// The area this round worked hardest, and how it stands now. The round
    /// just moved it, so its tree is the one thing on this screen that is about
    /// THIS learner's box rather than about finishing anything.
    var grownArea: AreaTree?
    var canPracticeMore: Bool = false
    /// Today's recall has fallen far below what the box schedules for
    /// (`TodayReport.recallStrained`). Practising on stays available either
    /// way — this only adds the line saying why stopping is the better call.
    var restSuggested: Bool = false
    var onPractice: () -> Void = {}
    var onDone: () -> Void = {}

    @State private var burst = false
    /// Bumped on every replay; ConfettiView adds a wave per value.
    @State private var celebration = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The ring around the popper — one sign per idea (sprout, star, hands,
    /// sparkle, arm, balloon), so no two read as the same thing at a glance.
    /// The radius keeps them against the popper's own edge: further out they
    /// stop reading as its burst and start floating on their own.
    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("🌱", -184, 78), ("⭐️", -146, 84), ("🙌", -110, 88),
        ("✨", -70, 88), ("💪", -34, 84), ("🎈", 4, 78),
    ]

    private static func swayAngle(_ index: Int) -> Double { 5 + Double(index % 3) * 2 }
    private static func swayPeriod(_ index: Int) -> Double { 2.1 + Double(index) * 0.27 }

    /// "3 neu · 2 gefestigt · 8 wiederholt" — only the non-zero parts. Built
    /// as `Text` so each part localizes via the environment locale.
    private var summaryText: Text {
        var parts: [Text] = []
        if newCount > 0 { parts.append(Text("session.summary.new \(newCount.formatted())")) }
        if graduatedCount > 0 { parts.append(Text("session.summary.consolidated \(graduatedCount.formatted())")) }
        if reviewCount > 0 { parts.append(Text("session.summary.reviewed \(reviewCount.formatted())")) }
        return parts.joined() ?? Text("session.summary.allDone")
    }

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            burstHero
            Text("session.finished.title")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            summaryText
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            grownAreaBand
            VStack(spacing: DL.Space.s) {
                StreakFlameView(days: streakDays)
                if streakIsRecord {
                    Text("session.finished.streakRecord")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlAccent)
                }
            }
            if restSuggested {
                Text("session.finished.restHint")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            SessionExitButtons(onDone: onDone,
                               onPractice: canPracticeMore ? onPractice : nil)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        .overlay(ConfettiView(run: celebration).ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture(perform: replay)
        // why: after the overlay and the replay gesture, so the corner stays
        // tappable — a tap there leaves instead of setting off the confetti.
        .sessionCloseCorner(label: "common.done", action: onDone)
        .onAppear {
            burst = true
            DLSound.cheer()
        }
    }

    /// Snaps the burst back to rest with no animation, then re-triggers it
    /// on the next runloop turn so the spring actually replays.
    private func replay() {
        var reset = Transaction()
        reset.disablesAnimations = true
        withTransaction(reset) { burst = false }
        DispatchQueue.main.async {
            burst = true
            celebration += 1
        }
        DLSound.cheer()
    }

    /// The area the round moved most, growing out of the ground once. Named in
    /// words underneath, because the tree alone cannot say which area it is —
    /// and a round that touched nothing joinable simply shows nothing.
    @ViewBuilder
    private var grownAreaBand: some View {
        if let grownArea, !grownArea.isBare {
            VStack(spacing: DL.Space.xs) {
                GrowingTreeView(tree: grownArea, grown: burst || reduceMotion ? 1 : 0)
                    .frame(height: 118)
                    .animation(reduceMotion ? nil
                                : .spring(response: 1.15, dampingFraction: 0.72).delay(0.2),
                               value: burst)
                Text("session.finished.grew \(grownArea.title)")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            .accessibilityElement(children: .combine)
        }
    }

    private var burstHero: some View {
        ZStack {
            ForEach(Array(Self.pieces.enumerated()), id: \.offset) { index, piece in
                let radians = piece.angle * .pi / 180
                Text(piece.emoji)
                    .font(.title2)
                    .offset(
                        x: burst ? piece.distance * cos(radians) : 0,
                        y: burst ? piece.distance * sin(radians) : 0
                    )
                    .scaleEffect(burst ? 1 : 0.2)
                    .rotationEffect(.degrees(burst ? 0 : index.isMultiple(of: 2) ? -70 : 70))
                    .opacity(burst ? 1 : 0)
                    .animation(
                        .spring(response: 0.6, dampingFraction: 0.6)
                        .delay(0.15 + Double(index) * 0.06),
                        value: burst
                    )
                    .dlSway(angle: Self.swayAngle(index), period: Self.swayPeriod(index))
            }
            Text(verbatim: "🎉")
                .font(.system(size: 88))
                .scaleEffect(burst ? 1 : 0.4)
                .rotationEffect(.degrees(burst ? 0 : -25))
                .animation(.spring(response: 0.5, dampingFraction: 0.5), value: burst)
                // why: the popper carries the least of it — a big shape rocking
                // as far as a small one reads as the screen itself tilting.
                .dlSway(angle: 3, period: 3.7)
        }
        .frame(height: 180)
        .accessibilityHidden(true) // why: purely celebratory; "session.finished.title" below carries the message
    }
}

// MARK: - Previews

#Preview("Completion") {
    SessionCompletionView(reviewCount: 18, streakDays: 7, canPracticeMore: true)
}

#Preview("Completion · dark") {
    SessionCompletionView(reviewCount: 5, streakDays: 1)
        .preferredColorScheme(.dark)
}
