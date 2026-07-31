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

    @State private var burst = false
    /// Bumped on every replay; ConfettiView reseeds its field from it.
    @State private var celebration = 0

    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("⭐️", -150, 96), ("🙌", -110, 118), ("✨", -70, 118),
        ("💪", -30, 96), ("🌟", -170, 60), ("🎈", -10, 60),
    ]

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
            }
            Text(verbatim: "🎉")
                .font(.system(size: 88))
                .scaleEffect(burst ? 1 : 0.4)
                .rotationEffect(.degrees(burst ? 0 : -25))
                .animation(.spring(response: 0.5, dampingFraction: 0.5), value: burst)
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
