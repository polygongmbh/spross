import SwiftUI
import SprossKern

/// Saying the drill's answer out loud. The prompt is a NUMERAL ("347", "14:35")
/// — there is nothing to play until the reading is out, so unlike the letter
/// drill this surface has no prompt audio at all: every fire here is a reveal.
///
/// The readings are generated and no catalog lists them, but Kern's lookup is
/// total — it hands back an utterance for the live voice where it has no
/// recording — so the ordinary `pronounceAction` says them. Nothing was ever
/// calling it here; that, not the lookup, is why the drills were silent.
extension NumbersRunView {

    var spokenAnswer: String? { feedback.owedForm(revealing: run.currentTask.display) }

    /// Every way out of a task goes through here — the next prompt, the
    /// summary, the door.
    func hushAnswer() {
        answerVoice.hush()
    }

    /// It began to matter here when "Aufdecken" started REMOVING the field
    /// rather than disabling it: the next task remounts one, and the plain
    /// assignment raced it (`AnswerFocus`).
    func focusAnswerField() {
        AnswerFocus.claim($answerFocused, retry: &focusRetry)
    }
}
