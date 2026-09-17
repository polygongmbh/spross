import SwiftUI
import SprossKern

/// The written answer and the one action under it, on every drill that asks for
/// one. Which drill it is changes the placeholder, the keyboard and the voice
/// beside the correction box — parameters, all of them; what the learner is
/// offered is the same four states, so this is one component and not one per
/// drill (`docs/design.md` § Review UX rules).
///
/// Nothing here grades or decides: `feedback` is where kern says the answer
/// stands, and every branch below only draws it.
struct DrillAnswerControls: View {

    @Binding var text: String
    /// Where kern says the answer stands.
    let feedback: AnswerInputView.Feedback
    /// What the field asks for, named in the language the answer is owed in
    /// (`LanguageNaming.answerPlaceholder`).
    let placeholder: String
    var focus: FocusState<Bool>.Binding?
    /// Tap-to-replay for the correction box — the form the slip owed, said in
    /// the language it is owed in. Left off where the drill can say nothing.
    var correctionVoice: Voice?
    var keyboard: UIKeyboardType = .default
    /// Offered every keystroke, where the drill approves live. nil where nothing
    /// is graded until it is submitted — the letters ladder.
    var onType: (() -> Void)?
    let onSubmit: () -> Void
    /// The tap that books whatever the feedback already said.
    let onConfirm: () -> Void
    /// The way out, where the run offers one: on the SECOND miss in a row.
    var onStop: (() -> Void)?

    var body: some View {
        VStack(spacing: Theme.spacing.md) {
            AnswerInputView(text: $text,
                            feedback: feedback,
                            placeholder: placeholder,
                            focus: focus,
                            correctionVoice: correctionVoice,
                            keyboard: keyboard,
                            onSubmit: onSubmit)
                // why: writing the answer out is the answer — the review
                // session's rule, so a word you know never asks for a
                // confirming tap.
                .onChange(of: text) { _, _ in onType?() }
            switch feedback {
            case .neutral:
                // ONE primary action: an empty field reveals, a typed one checks.
                Button(action: onSubmit) {
                    Text(text.isBlankAnswer ? "common.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: text.isBlankAnswer)
            case .almost:
                // The amber hold: the box above spells the form out, and this
                // waits for the tap that books it amber.
                DrillNextButton(action: onConfirm)
                    .transition(.opacity)
            case .correct:
                // why: the timer never arms under a screen reader, so a clean
                // hit would otherwise have nothing to move on with.
                if AutoAdvance.screenReaderOn {
                    DrillNextButton(action: onConfirm)
                        .transition(.opacity)
                }
            case .revealed:
                DrillRevealedControls(onConfirm: onConfirm, onStop: onStop)
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }
}

/// The one button that books whatever the feedback already said — kern decides
/// what that is, so every branch reaching for it says the same word.
struct DrillNextButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        // why: Enter advances here too (hardware keyboards).
        .keyboardShortcut(.defaultAction)
    }
}

/// The way on after a miss, and — on the second in a row — the way out.
struct DrillRevealedControls: View {
    let onConfirm: () -> Void
    /// nil where the run is not offering the way out, or the drill has none.
    var onStop: (() -> Void)?

    var body: some View {
        VStack(spacing: Theme.spacing.sm) {
            DrillNextButton(action: onConfirm)
            if let onStop { DrillStopOffer(action: onStop) }
        }
    }
}
