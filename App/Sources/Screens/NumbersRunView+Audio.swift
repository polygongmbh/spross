import SwiftUI
import SprossKern

/// Saying the drill's answer out loud. The prompt is a NUMERAL ("347", "14:35")
/// — there is nothing to play until the reading is out, so unlike the letter
/// drill this surface has no prompt audio at all: every fire here is a graded
/// answer's reading, which kern hands over as the run's `reading`.
///
/// The readings are generated and no catalog lists them, but Kern's lookup is
/// total — it hands back an utterance for the live voice where it has no
/// recording — so the ordinary `pronounceAloud` says them.
extension NumbersRunView {

    /// Every way out of a task goes through here — the next prompt, the
    /// summary, the door.
    func hushAnswer() {
        reader.hush()
    }

    /// A reveal from a blank field REMOVES it and the next task mounts a fresh
    /// one, so a plain assignment would race the mount (`AnswerFocus`).
    func focusAnswerField() {
        AnswerFocus.claim($answerFocused, retry: &focusRetry)
    }
}
