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
