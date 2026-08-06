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
extension TrainerSessionView {

    /// The form currently owed to the learner: the correction after a slip,
    /// otherwise the revealed reading. nil while the answer is still theirs to
    /// produce — nothing may speak the answer to a question still standing.
    var spokenAnswer: String? {
        switch feedback {
        case .almost(let form, _): return form
        case .revealed: return current.display
        case .neutral, .correct: return nil
        }
    }

    /// Tap-to-replay for the correction box. The card's own speaker is wired
    /// separately (`drillContent`) because it says the reading even when the
    /// box is not on screen.
    var correctionPronounce: (() -> Void)? {
        guard case .almost(let form, _) = feedback else { return nil }
        return model?.pronounceAction(for: form, lang: language)
    }

    var correctionPlaying: Bool {
        guard case .almost(let form, _) = feedback else { return false }
        return model?.isPronouncing(form, lang: language) ?? false
    }

    /// Fires once when the answer comes out, however it came out. `.auto`, so
    /// the read-aloud switch and VoiceOver both still veto it — a tap on the
    /// speaker outranks the mute, this does not.
    ///
    /// Held in `answerVoice` rather than fired and forgotten: the wait outlives
    /// a fast tap, and a reveal that is closed within it would otherwise speak
    /// its answer over whatever screen replaced the run.
    func autoplayAnswer() {
        guard let model, let form = spokenAnswer else { return }
        answerVoice?.cancel()
        answerVoice = Task { @MainActor in
            // why: the correct/wrong chime lands first — the same 300 ms the
            // review session waits, or the word starts under the chime.
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            model.pronounceAloud(form, lang: language)
        }
    }

    /// Silence, and drop a wait that has not fired yet. Every way out of a task
    /// goes through here — the next prompt, the summary, the door — because a
    /// reading belongs to the task that revealed it and to nothing after.
    func hushAnswer() {
        answerVoice?.cancel()
        answerVoice = nil
        Pronouncer.shared.stop()
    }

    /// Ask the field that is about to be on screen for focus. The immediate
    /// request covers a field already mounted; the retry covers one mounting in
    /// the same frame — a request that arrives before its field exists is
    /// simply dropped (`SessionView.focusAnswerField`, same shape).
    ///
    /// It began to matter here when "Aufdecken" started REMOVING the field
    /// rather than disabling it: the next task remounts one, and the plain
    /// assignment raced it.
    func focusAnswerField() {
        answerFocused = true
        focusRetry?.cancel()
        focusRetry = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(120))
            guard !Task.isCancelled else { return }
            answerFocused = true
        }
    }
}
