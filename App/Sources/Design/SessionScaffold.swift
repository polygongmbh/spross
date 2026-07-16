import SwiftUI

// MARK: - SessionScaffold
//
// Session container chrome: close button + session progress bar on top,
// arbitrary content below. Pure chrome — knows nothing about cards.

struct SessionScaffold<Content: View>: View {
    /// 1-based position of the current card in the composed session.
    let position: Int
    let total: Int
    var onClose: () -> Void = {}
    @ViewBuilder var content: Content

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return Double(position - 1) / Double(total)
    }

    var body: some View {
        VStack(spacing: DL.Space.l) {
            topBar
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(DL.Space.l)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    private var topBar: some View {
        HStack(spacing: DL.Space.m) {
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Color.dlTextSecondary)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Color.dlSurfaceTint))
            }
            .accessibilityLabel("Sitzung beenden")

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.dlSeparator)
                    Capsule()
                        .fill(Color.dlAccent)
                        .frame(width: max(geo.size.width * fraction, 10))
                }
            }
            .frame(height: 10)
            .animation(.easeOut(duration: 0.3), value: fraction)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Karte \(position) von \(total)")

            Text("\(position)/\(total)")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .monospacedDigit()
                .accessibilityHidden(true)
        }
    }
}

// MARK: - SessionCompletionView
//
// "Geschafft!" — warm and playful. The emoji burst is pure SwiftUI
// (staggered springs), no confetti dependency.

struct SessionCompletionView: View {
    let reviewCount: Int
    let streakDays: Int
    var onDone: () -> Void = {}

    @State private var burst = false

    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("⭐️", -150, 96), ("🎉", -110, 118), ("✨", -70, 118),
        ("💪", -30, 96), ("🌟", -170, 60), ("🎈", -10, 60),
    ]

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            burstHero
            Text("Geschafft!")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text("\(reviewCount) Karten wiederholt")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
            StreakFlameView(days: streakDays)
            Spacer()
            Button("Fertig", action: onDone)
                .buttonStyle(DLPrimaryButtonStyle())
                .frame(maxWidth: .infinity)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        .onAppear { burst = true }
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
                    .opacity(burst ? 1 : 0)
                    .animation(
                        .spring(response: 0.6, dampingFraction: 0.6)
                        .delay(0.15 + Double(index) * 0.06),
                        value: burst
                    )
            }
            Text("🎉")
                .font(.system(size: 88))
                .scaleEffect(burst ? 1 : 0.4)
                .animation(.spring(response: 0.5, dampingFraction: 0.55), value: burst)
        }
        .frame(height: 180)
        .accessibilityHidden(true) // why: purely celebratory; "Geschafft!" below carries the message
    }
}

// MARK: - Previews

#Preview("Session chrome") {
    SessionScaffold(position: 4, total: 12, onClose: {}) {
        VStack(spacing: DL.Space.xl) {
            VocabCardView(
                emoji: "🥄",
                article: "der",
                headword: "Löffel",
                plural: "die Löffel",
                translation: "kijiko",
                note: nil,
                mode: .recognition,
                revealed: true
            )
            RatingButtonsView { _ in }
            Spacer(minLength: 0)
        }
    }
}

#Preview("Completion") {
    SessionCompletionView(reviewCount: 18, streakDays: 7)
}

#Preview("Completion · dark") {
    SessionCompletionView(reviewCount: 5, streakDays: 1)
        .preferredColorScheme(.dark)
}
