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
    var canPracticeMore: Bool = false
    /// Today's recall has fallen far below what the box schedules for
    /// (`TodayReport.recallStrained`). Practising on stays available either
    /// way — this only adds the line saying why stopping is the better call.
    var restSuggested: Bool = false
    var onPractice: () -> Void = {}
    var onDone: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var burst = false
    /// Set once and left set: it drives the endless sway, which is a
    /// repeatForever animation and so needs exactly one change to start.
    @State private var swaying = false
    /// Bumped on every replay; ConfettiView adds a wave per value.
    @State private var celebration = 0

    /// The ring around the popper — one sign per idea (hands, star, sparkle,
    /// arm, sprout, balloon), so no two read as the same thing at a glance.
    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("⭐️", -140, 100), ("🙌", -104, 120), ("✨", -64, 118),
        ("💪", -28, 100), ("🌱", -174, 64), ("🎈", -6, 64),
    ]

    /// Every piece rocks on its own clock and through its own angle — one
    /// shared period would read as a single rigid object rocking, which is
    /// the opposite of six light things hanging in the air.
    private static func swayAngle(_ index: Int) -> Double { 5 + Double(index % 3) * 2 }
    private static func swayPeriod(_ index: Int) -> Double { 2.1 + Double(index) * 0.27 }

    /// Nothing on this screen has to move for it to be read, so the endless
    /// part of the celebration is the first thing Reduce Motion drops.
    private func sway(period: Double) -> Animation? {
        reduceMotion ? nil : .easeInOut(duration: period).repeatForever(autoreverses: true)
    }

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
            StreakFlameView(days: streakDays)
            if restSuggested {
                Text("session.finished.restHint")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            VStack(spacing: DL.Space.m) {
                // why: the day's work is done — stopping is the default choice,
                // and practising on is the one that has to be reached for.
                Button(action: onDone) {
                    Text("common.done").frame(maxWidth: .infinity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                if canPracticeMore {
                    Button(action: onPractice) {
                        Text("session.finished.keepPracticing").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(DLSoftButtonStyle())
                }
            }
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        .overlay(ConfettiView(run: celebration).ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture(perform: replay)
        .onAppear {
            burst = true
            swaying = true
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
                    .rotationEffect(.degrees(swaying ? Self.swayAngle(index) : -Self.swayAngle(index)))
                    .animation(sway(period: Self.swayPeriod(index)), value: swaying)
            }
            Text(verbatim: "🎉")
                .font(.system(size: 88))
                .scaleEffect(burst ? 1 : 0.4)
                .rotationEffect(.degrees(burst ? 0 : -25))
                .animation(.spring(response: 0.5, dampingFraction: 0.5), value: burst)
                // why: the popper carries the least of it — a big shape rocking
                // as far as a small one reads as the screen itself tilting.
                .rotationEffect(.degrees(swaying ? 3 : -3))
                .animation(sway(period: 3.7), value: swaying)
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
