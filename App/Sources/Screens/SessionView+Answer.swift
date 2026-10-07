import SwiftUI
import SprossKern

/// What stands under the review card: kern's `TurnState.controls` on the shared `AnswerArea`.
/// Every tap and keystroke here is a `TurnIntent` — what each one is worth, and which beat it
/// earns, is `TurnMachine`'s. Split out purely for file size.
extension SessionView {

    /// The answer field and the write-out keep their own text; only one is ever mounted.
    /// Each field claims focus from its own appearance: a request made before the field
    /// is on screen lands on nothing.
    func answerArea(_ turn: TurnState) -> some View {
        let writing = turn.copyStep != nil
        return AnswerArea(
            controls: turn.controls,
            text: writing ? $copyInput : $input,
            placeholder: writing ? copyPlaceholder : inputPlaceholder,
            focus: $answerFocused,
            correctionVoice: .init(pronounce: { correctionSpeaker($0) },
                                   isPlaying: { correctionIsPlaying($0) }),
            nextLocale: model.targetChromeLocale,
            caption: writing ? (model.coachActive ? SessionCoach.writeLine : nil) : gradeCaption,
            actions: AnswerActions(
                submit: { writing ? dispatch(TurnIntent.CopySubmit(text: copyInput)) : submitFromField() },
                type: { dispatch(TurnIntent.InputChanged(text: $0)) },
                reveal: { dispatch(TurnIntent.Reveal.shared) },
                confirm: { dispatch(TurnIntent.ConfirmPending.shared) },
                // why: giving up on a retype ends the card — that field already is the one
                // write-out the word gets, so nothing hands it a second.
                giveUp: { dispatch(writing ? TurnIntent.SkipCopy.shared : TurnIntent.GiveUp.shared) },
                selfGrade: { dispatch(TurnIntent.SelfGrade(verdict: $0.verdict)) },
                cantListen: {
                    // why: the word in the air belongs to a question that is about to stand
                    // in writing — nothing may keep playing over the answer to "I can't listen".
                    Pronouncer.shared.stop()
                    dispatch(TurnIntent.ShowPromptText.shared)
                },
                fieldAppeared: { focusAnswerField() }))
    }

    /// Enter in the answer field. Which intent it IS depends on what the field
    /// currently stands for — kern grades each of the three differently, and
    /// only the platform knows which one is on screen.
    private func submitFromField() {
        if retryApproved {
            // why: the retype already stands — Enter only skips the beat the
            // timer is waiting out, it does not re-grade it.
            dispatch(TurnIntent.ConfirmPending.shared)
        } else if case .revealed = feedback {
            // why: a hardware keyboard still needs a way to give up
            // without finishing the retype.
            dispatch(TurnIntent.GiveUp.shared)
        } else {
            dispatch(TurnIntent.Submit(text: input))
        }
    }

    /// The language the field asks for, named. Kern's answer side decides which
    /// one that is — the meaning on a card asked by ear, the target everywhere
    /// else — and this placeholder is the one place the learner is told.
    private var inputPlaceholder: String {
        guard let lang = turn?.answerLang ?? model.targetLanguage else { return "" }
        return answerPlaceholder(lang)
    }

    /// The write-out's field: only the TARGET language is ever copied, so it asks for it by name.
    /// Which misses ask for a write-out and when it lets go are kern's (`TurnWriteOut`).
    private var copyPlaceholder: String {
        guard let target = model.targetLanguage else { return "" }
        let name = LanguageNames.display(target, catalog: model.catalog)
        return String(format: ChromeStrings.string("session.copy.placeholder %@", locale: locale), name)
    }

    /// The speaker beside a correction — none where the card was asked by ear:
    /// what it owes back is then a SOURCE word, and the target voice would read
    /// a German word in Swahili.
    private func correctionSpeaker(_ form: String) -> (() -> Void)? {
        answerIsMeaning ? nil : pronounceAction(for: form)
    }

    private func correctionIsPlaying(_ form: String) -> Bool {
        answerIsMeaning ? false : isPronouncing(form)
    }
}
