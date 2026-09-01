import SwiftUI

// MARK: - RatingButtonsView
//
// Self-grade row, under the question it answers:
//
//         How well did you know it?
//     Knew it    Shaky    Not at all
//
// Three buttons, not four: the learner says whether the word came, and the
// clock behind them decides whether one that came, came instantly (the rule
// and its reasoning: kern `SelfGrading`). Nobody can pick their way to a long
// interval — Easy is earned by answering fast.
//
// The labels name what the LEARNER knows, never what the scheduler will do
// (design §Presentation model) — which is why none of them is an FSRS rating's
// name. Ordered best to worst, so the miss ends up under a resting thumb and
// the two opposite verdicts are kept apart by the middle one.
//
// They emit `SessionOutcome`, the same three the progress bar draws, in the
// same three colors — the button you press is the segment you get.
// Colorblind-safe: every one carries a distinct icon AND label, so color
// never has to be read on its own.

struct RatingButtonsView: View {

    var onGrade: (SessionOutcome) -> Void
    /// What stands under the row. The standing question by default; the first
    /// round's coaching replaces it, so only ever one line sits there.
    var caption: LocalizedStringKey = "session.rating.question"

    var body: some View {
        VStack(spacing: DL.Space.s) {
            HStack(spacing: DL.Space.s) {
                button(.right)
                button(.tough)
                button(.wrong)
            }
            Text(caption)
                .dlPauseLine()
        }
    }

    private func button(_ outcome: SessionOutcome) -> some View {
        GradeButton(outcome: outcome) { onGrade(outcome) }
    }
}

// MARK: - Button face

private extension SessionOutcome {
    var label: LocalizedStringKey {
        switch self {
        case .right: return "session.rating.good"
        case .tough: return "session.rating.hard"
        case .wrong: return "session.rating.unknown"
        }
    }

    var icon: String {
        switch self {
        case .right: return "checkmark"
        case .tough: return "circle.fill"
        case .wrong: return "xmark"
        }
    }
}

private struct GradeButton: View {
    let outcome: SessionOutcome
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: DL.Space.xs + 2) {
                Image(systemName: outcome.icon)
                    .font(.body.weight(.bold))
                    // why: the glyphs differ in height, which pushed the labels
                    // of neighboring buttons off each other's line.
                    .frame(height: 22)
                Text(outcome.label)
                    .font(DL.Fonts.caption)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(outcome.color)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 60)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                    .fill(outcome.color.opacity(0.14))
            )
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                    .strokeBorder(outcome.color.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(outcome.label)
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
