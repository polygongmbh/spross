import SwiftUI

// MARK: - AnswerInputView
//
// Typed-answer field with inline feedback. Spec rules:
// - correct  → subtle green glow + checkmark, never loud
// - wrong    → the correct answer appears inline BELOW the input,
//              warm amber accent, NO red flash — never punishing.

struct AnswerInputView: View {

    enum Feedback: Equatable {
        case neutral
        case correct
        case revealed(correctAnswer: String)
    }

    @Binding var text: String
    var feedback: Feedback = .neutral
    var placeholder: String = "Antwort eingeben …"
    /// Session views own focus so the keyboard is up the moment a card
    /// appears; standalone use falls back to the internal focus state.
    var focus: FocusState<Bool>.Binding?
    var onSubmit: () -> Void = {}

    @FocusState private var fallbackFocus: Bool

    var body: some View {
        VStack(spacing: DL.Space.m) {
            inputField
            // why: when "Aufdecken" fills the field with the answer, the field
            // already shows it — the panel below would just duplicate it.
            if case .revealed(let answer) = feedback, text != answer {
                revealPanel(answer: answer)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    // MARK: Input field

    private var inputField: some View {
        HStack(spacing: DL.Space.s) {
            TextField(placeholder, text: $text)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused(focus ?? $fallbackFocus)
                .onSubmit(onSubmit)
                // why: on wrong answers Enter/tap must advance the session
                // instead; on correct the field stays enabled so the keyboard
                // does not bounce during the 1.2 s auto-advance.
                .disabled(isRevealed)
            statusIcon
        }
        .padding(.horizontal, DL.Space.l)
        .frame(minHeight: 56)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .strokeBorder(borderColor, lineWidth: feedback == .neutral ? 1 : 2)
        )
        .shadow(
            color: feedback == .correct ? Color.dlSuccess.opacity(0.35) : .clear,
            radius: 10
        )
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch feedback {
        case .neutral:
            EmptyView()
        case .correct:
            Image(systemName: "checkmark.circle.fill")
                .font(.title3)
                .foregroundStyle(Color.dlSuccess)
                .accessibilityLabel("Richtig")
        case .revealed:
            Image(systemName: "lightbulb.fill")
                .font(.title3)
                .foregroundStyle(Color.dlAmber)
                .accessibilityLabel("Aufgelöst")
        }
    }

    private var isRevealed: Bool {
        if case .revealed = feedback { return true }
        return false
    }

    private var borderColor: Color {
        switch feedback {
        case .neutral: return .dlSeparator
        case .correct: return .dlSuccess
        case .revealed: return .dlAmber
        }
    }

    // MARK: Reveal panel (warm, never red)

    private func revealPanel(answer: String) -> some View {
        HStack(spacing: DL.Space.m) {
            Image(systemName: "arrow.turn.down.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.dlAmber)
            VStack(alignment: .leading, spacing: DL.Space.xs) {
                Text("Richtige Antwort")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                Text(answer)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
            }
            Spacer(minLength: 0)
        }
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlAmber.opacity(0.14))
        )
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Previews

private struct AnswerInputPreviewHost: View {
    @State private var neutral = ""
    @State private var right = "kisu"
    @State private var wrong = "kijiko"

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            AnswerInputView(text: $neutral, feedback: .neutral)
            AnswerInputView(text: $right, feedback: .correct)
            AnswerInputView(text: $wrong, feedback: .revealed(correctAnswer: "kisu"))
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground)
    }
}

#Preview("All states") {
    AnswerInputPreviewHost()
}

#Preview("All states · dark") {
    AnswerInputPreviewHost()
        .preferredColorScheme(.dark)
}
