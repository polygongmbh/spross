import SwiftUI
import SprossKern

/// The write-it-out step, as this screen renders it. WHICH misses ask for one,
/// what a keystroke in it is worth and when it lets go are kern's
/// (`TurnWriteOut`); what is left here is the field, its focus and its words.
///
/// It is an encoding step, never a grade: the rating was already chosen by the
/// self-grade buttons and is applied unchanged afterwards, so the 2026-07-22
/// "self-grade only" ruling still owns the schedule (kern README §3). Only words
/// that have not consolidated take this path, so it never slows a word down that
/// already sticks, and only Again triggers it — Easy/Good/Hard advance straight
/// away, which is how "I already knew this" stays one tap.
extension SessionView {

    @ViewBuilder
    func copyControls(_ step: CopyStep) -> some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $copyInput,
                            // why: green edge + checkmark the moment the word
                            // stands written — kern re-judges it per keystroke.
                            feedback: step.written ? .correct : .neutral,
                            placeholder: copyPlaceholder,
                            focus: $answerFocused) {
                dispatch(TurnIntent.CopySubmit(text: copyInput))
            }
            // why: the word finishing IS the action — there is nothing to confirm
            // when the answer is already on screen, so no button asks for a tap.
            .onChange(of: copyInput) { _, _ in dispatch(TurnIntent.InputChanged(text: copyInput)) }
            // why: this field replaces the one the rating buttons sat under, so
            // it has to claim focus itself — the tap that opened it fired first.
            .onAppear { focusAnswerField() }
            if model.coachActive {
                // why: the field opened by itself after a miss — the first round says
                // what it is FOR, or copying a word off the card reads as busywork.
                Text(SessionCoach.writeLine).dlPauseLine()
            }
            if step.missed {
                // why: the answer is already on the card, so this points back to it.
                Text("session.copyMismatch")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            Button("session.skipCopy") { dispatch(TurnIntent.SkipCopy.shared) }
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// Only the TARGET language is ever copied, so the field asks for it by name.
    private var copyPlaceholder: String {
        guard let target = model.targetLanguage else { return "" }
        let name = LanguageNames.display(target, locale: locale, catalog: model.catalog)
        return String(format: DLChrome.string("session.copy.placeholder %@", locale: locale), name)
    }
}
