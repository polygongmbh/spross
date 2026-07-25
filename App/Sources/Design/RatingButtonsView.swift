import SwiftUI

// MARK: - RatingButtonsView
//
// Again/Hard/Good/Easy row for recognition mode. German labels.
// Colorblind-safe: every rating has a distinct icon AND label —
// color only grades the row, it never carries the meaning alone.
// No red anywhere ("never punishing"): Again is warm terracotta.

struct RatingButtonsView: View {

    enum Rating: CaseIterable {
        case again, hard, good, easy

        var label: LocalizedStringKey {
            switch self {
            case .again: return "rating.again"
            case .hard: return "rating.hard"
            case .good: return "rating.good"
            case .easy: return "rating.easy"
            }
        }

        var icon: String {
            switch self {
            case .again: return "arrow.counterclockwise"
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

    var body: some View {
        HStack(spacing: DL.Space.s) {
            ForEach(Rating.allCases, id: \.self) { rating in
                RatingButton(rating: rating) { onRate(rating) }
            }
        }
        // why: four-up row would clip at the largest accessibility sizes;
        // capped so labels stay legible while still honoring larger type.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
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
                Text(rating.label)
                    .font(DL.Fonts.caption)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
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

#Preview("Rating row") {
    VStack {
        Spacer()
        RatingButtonsView { _ in }
            .padding(DL.Space.xl)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}

#Preview("Rating row · dark") {
    VStack {
        Spacer()
        RatingButtonsView { _ in }
            .padding(DL.Space.xl)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
