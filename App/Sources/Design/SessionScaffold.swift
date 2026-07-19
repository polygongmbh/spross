import SwiftUI

// MARK: - SessionScaffold
//
// Session container chrome: close button + session progress bar on top,
// arbitrary content below. Pure chrome — knows nothing about cards.

/// One answered item in a session/drill, for the segmented progress bar.
enum SessionOutcome: Equatable {
    case right, tough, wrong

    var color: Color {
        switch self {
        case .right: return .dlSuccess
        case .tough: return .dlAmber
        case .wrong: return .dlWrong
        }
    }
}

struct SessionScaffold<Content: View>: View {
    /// 1-based position of the current card in the composed session.
    let position: Int
    let total: Int
    /// Answered items in order; when non-empty the bar renders one colored
    /// segment per answer (green right / amber tough / brick wrong) with
    /// the unanswered remainder neutral.
    var outcomes: [SessionOutcome] = []
    /// Overrides the "position/total" counter (endless drills show "right/done").
    var counter: String?
    var onClose: () -> Void = {}
    @ViewBuilder var content: Content

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return Double(position - 1) / Double(total)
    }

    /// `Text` (not a String) so it localizes via the environment locale.
    private var progressAccessibility: Text {
        if outcomes.isEmpty {
            return Text("Karte \(position) von \(total)")
        }
        let right = outcomes.filter { $0 == .right }.count
        let tough = outcomes.filter { $0 == .tough }.count
        let wrong = outcomes.filter { $0 == .wrong }.count
        return Text("\(right) richtig, \(tough) schwer, \(wrong) daneben")
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
                if outcomes.isEmpty {
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.dlSeparator)
                        Capsule()
                            .fill(Color.dlAccent)
                            .frame(width: max(geo.size.width * fraction, 10))
                    }
                } else {
                    let slots = max(total, outcomes.count)
                    HStack(spacing: slots > 40 ? 0.5 : 1) {
                        ForEach(Array(outcomes.enumerated()), id: \.offset) { _, outcome in
                            Rectangle().fill(outcome.color)
                        }
                        if outcomes.count < slots {
                            Rectangle()
                                .fill(Color.dlSeparator)
                                .frame(width: geo.size.width * CGFloat(slots - outcomes.count) / CGFloat(slots))
                        }
                    }
                    .clipShape(Capsule())
                }
            }
            .frame(height: 10)
            .animation(.easeOut(duration: 0.3), value: outcomes.count)
            .animation(.easeOut(duration: 0.3), value: fraction)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(progressAccessibility)

            Text(counter ?? "\(position)/\(total)")
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
    var newCount: Int = 0
    var graduatedCount: Int = 0
    let reviewCount: Int
    let streakDays: Int
    var canPracticeMore: Bool = false
    var onPractice: () -> Void = {}
    var onDone: () -> Void = {}

    @State private var burst = false

    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("⭐️", -150, 96), ("🎉", -110, 118), ("✨", -70, 118),
        ("💪", -30, 96), ("🌟", -170, 60), ("🎈", -10, 60),
    ]

    /// "3 neu · 2 gefestigt · 8 wiederholt" — only the non-zero parts. Built
    /// as `Text` so each part localizes via the environment locale.
    private var summaryText: Text {
        var parts: [Text] = []
        if newCount > 0 { parts.append(Text("\(newCount) neu")) }
        if graduatedCount > 0 { parts.append(Text("\(graduatedCount) gefestigt")) }
        if reviewCount > 0 { parts.append(Text("\(reviewCount) wiederholt")) }
        guard var result = parts.first else { return Text("Alles erledigt") }
        for part in parts.dropFirst() { result = result + Text(" · ") + part }
        return result
    }

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            burstHero
            Text("Geschafft!")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            summaryText
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            StreakFlameView(days: streakDays)
            Spacer()
            VStack(spacing: DL.Space.m) {
                if canPracticeMore {
                    Button("Weiter üben", action: onPractice)
                        .buttonStyle(DLPrimaryButtonStyle())
                        .frame(maxWidth: .infinity)
                    Button("Fertig", action: onDone)
                        .buttonStyle(DLSoftButtonStyle())
                        .frame(maxWidth: .infinity)
                } else {
                    Button("Fertig", action: onDone)
                        .buttonStyle(DLPrimaryButtonStyle())
                        .frame(maxWidth: .infinity)
                }
            }
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
