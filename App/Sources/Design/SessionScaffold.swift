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
            return Text("session.cardPosition \(position.formatted()) \(total.formatted())")
        }
        let right = outcomes.filter { $0 == .right }.count
        let tough = outcomes.filter { $0 == .tough }.count
        let wrong = outcomes.filter { $0 == .wrong }.count
        return Text("a11y.sessionTally \(right.formatted()) \(tough.formatted()) \(wrong.formatted())")
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
            .accessibilityLabel("a11y.endSession")

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
    /// Today's recall has fallen far below what the box schedules for
    /// (`TodayReport.recallStrained`). Practising on is still offered — it just
    /// stops being the emphasised choice, and the screen says why.
    var restSuggested: Bool = false
    var onPractice: () -> Void = {}
    var onDone: () -> Void = {}

    @State private var burst = false

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

    /// One of the two exit buttons; only which one carries the emphasis changes.
    @ViewBuilder
    private func choice(_ key: LocalizedStringKey,
                        emphasised: Bool,
                        action: @escaping () -> Void) -> some View {
        if emphasised {
            Button(key, action: action)
                .buttonStyle(DLPrimaryButtonStyle())
                .frame(maxWidth: .infinity)
        } else {
            Button(key, action: action)
                .buttonStyle(DLSoftButtonStyle())
                .frame(maxWidth: .infinity)
        }
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
                if canPracticeMore {
                    // why: a day going badly flips the emphasis — stopping becomes the
                    // offered choice, practising on stays available but unpushed.
                    choice("session.finished.keepPracticing",
                           emphasised: !restSuggested, action: onPractice)
                    choice("common.done", emphasised: restSuggested, action: onDone)
                } else {
                    choice("common.done", emphasised: true, action: onDone)
                }
            }
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture(perform: replayBurst)
        .onAppear { burst = true }
    }

    /// Snaps the burst back to rest with no animation, then re-triggers it
    /// on the next runloop turn so the spring actually replays.
    private func replayBurst() {
        var reset = Transaction()
        reset.disablesAnimations = true
        withTransaction(reset) { burst = false }
        DispatchQueue.main.async { burst = true }
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
            Text(verbatim: "🎉")
                .font(.system(size: 88))
                .scaleEffect(burst ? 1 : 0.4)
                .animation(.spring(response: 0.5, dampingFraction: 0.55), value: burst)
        }
        .frame(height: 180)
        .accessibilityHidden(true) // why: purely celebratory; "session.finished.title" below carries the message
    }
}

// MARK: - Previews

#Preview("Session chrome") {
    SessionScaffold(position: 4, total: 12, onClose: {}) {
        VStack(spacing: DL.Space.xl) {
            VocabCardView(
                emoji: "🥄",
                prompt: .init(text: "kijiko"),
                answer: .init(text: "Löffel", article: "der", plural: "Pl. Löffel"),
                note: nil,
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
