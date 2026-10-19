import SwiftUI
import SprossKern

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
// They emit kern's `SelfGrading.Verdict` in the progress bar's three colors —
// the button you press is the segment you get.
// Colorblind-safe: every one carries a distinct icon AND label, so color
// never has to be read on its own.

struct RatingButtonsView: View {

    var onGrade: (SelfGrading.Verdict) -> Void
    /// What stands under the row. The standing question by default; the first
    /// round's coaching replaces it, so only ever one line sits there.
    var caption: LocalizedStringKey = "session.rating.question"

    var body: some View {
        VStack(spacing: Theme.spacing.sm) {
            HStack(spacing: Theme.spacing.sm) {
                button(.knew)
                button(.tough)
                button(.unknown)
            }
            Text(caption)
                .pauseLine()
        }
    }

    private func button(_ verdict: SelfGrading.Verdict) -> some View {
        GradeButton(verdict: verdict) { onGrade(verdict) }
    }
}

// MARK: - Button face

private extension SelfGrading.Verdict {
    var label: LocalizedStringKey {
        switch self {
        case .knew: return "session.rating.good"
        case .tough: return "session.rating.hard"
        case .unknown: return "session.rating.unknown"
        }
    }

    var icon: String {
        switch self {
        case .knew: return "checkmark"
        case .tough: return "circle.fill"
        case .unknown: return "xmark"
        }
    }

    var color: Color {
        switch self {
        case .knew: return Theme.colors.success
        case .tough: return Theme.colors.amber
        case .unknown: return Theme.colors.wrong
        }
    }
}

private struct GradeButton: View {
    let verdict: SelfGrading.Verdict
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: Theme.spacing.xs + 2) {
                Image(systemName: verdict.icon)
                    .font(.body.weight(.bold))
                    // why: the glyphs differ in height, which pushed the labels
                    // of neighboring buttons off each other's line.
                    .frame(height: 22)
                Text(verdict.label)
                    .font(Theme.typography.caption)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(verdict.color)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 60) // card-parity: the rating row's own height, not a card reserve
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                    .fill(verdict.color.opacity(Palette.shared.WASH))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                    .strokeBorder(verdict.color.opacity(Palette.shared.EDGE), lineWidth: 1)
            )
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(verdict.label)
    }
}

private struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .press(configuration.isPressed, .rating)
    }
}

// MARK: - Previews

#Preview("Rating row") {
    VStack {
        Spacer()
        RatingButtonsView { _ in }
            .padding(Theme.spacing.xl)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Rating row · dark") {
    VStack {
        Spacer()
        RatingButtonsView { _ in }
            .padding(Theme.spacing.xl)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
    .preferredColorScheme(.dark)
}
