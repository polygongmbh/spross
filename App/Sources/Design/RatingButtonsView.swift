import SwiftUI

// MARK: - RatingButtonsView
//
// Self-grade pad for recognition mode. German labels.
// Colorblind-safe: every rating has a distinct icon AND label —
// color only grades the pad, it never carries the meaning alone.
// No red anywhere ("never punishing"): Unbekannt is warm terracotta.
//
//     Einfach   Schwer
//     Gut       Unbekannt
//
// Right column = the word resisted, left = it came; quality rises upward.
// The two opposite verdicts therefore sit diagonally, so confusing them is
// the hardest slip to make. Unbekannt holds the bottom-right corner, where
// a thumb already rests: it is the common answer at first exposure, and the
// cheapest mistake to undo — the word returns within minutes, where a stray
// Einfach hides it for weeks.

struct RatingButtonsView: View {

    /// Cases keep the FSRS names the kern grades with; the labels speak the
    /// learner's language instead — "Nochmal" was a promise about the schedule,
    /// and it read as a lie on a word being met for the very first time.
    enum Rating: CaseIterable {
        case again, hard, good, easy

        var label: LocalizedStringKey {
            switch self {
            case .again: return "rating.unknown"
            case .hard: return "rating.hard"
            case .good: return "rating.good"
            case .easy: return "rating.easy"
            }
        }

        var icon: String {
            switch self {
            case .again: return "questionmark"
            case .hard: return "tortoise.fill"
            case .good: return "checkmark"
            case .easy: return "sparkles"
            }
        }

        var color: Color {
            switch self {
            case .again: return .dlAccent
            case .hard: return .dlAmber
            case .good: return .dlSuccess
            case .easy: return .dlTeal
            }
        }
    }

    var onRate: (Rating) -> Void

    // Written out rather than looped so the source reads as the pad it draws.
    var body: some View {
        Grid(horizontalSpacing: DL.Space.s, verticalSpacing: DL.Space.s) {
            GridRow {
                button(.easy)
                button(.hard)
            }
            GridRow {
                button(.good)
                button(.again)
            }
        }
    }

    private func button(_ rating: Rating) -> some View {
        RatingButton(rating: rating) { onRate(rating) }
    }
}

// MARK: - Single button

private struct RatingButton: View {
    let rating: RatingButtonsView.Rating
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: DL.Space.xs + 2) {
                Image(systemName: rating.icon)
                    .font(.body.weight(.bold))
                    // why: the glyphs differ in height (a tortoise is taller than
                    // a checkmark), which pushed the labels of a pair off each
                    // other's line once they sat side by side in the pad.
                    .frame(height: 22)
                Text(rating.label)
                    .font(DL.Fonts.caption)
                    // why: two-up leaves room the four-up row never had, so the
                    // longest label wraps instead of forcing a dynamic-type cap.
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(rating.color)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 60)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                    .fill(rating.color.opacity(0.14))
            )
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                    .strokeBorder(rating.color.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(rating.label)
    }
}

private struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

// MARK: - Previews

#Preview("Rating pad") {
    VStack {
        Spacer()
        RatingButtonsView { _ in }
            .padding(DL.Space.xl)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Rating pad · dark") {
    VStack {
        Spacer()
        RatingButtonsView { _ in }
            .padding(DL.Space.xl)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
