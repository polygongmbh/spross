import SwiftUI
import SprossKern

/// Saying the drill's answer out loud. The prompt is a NUMERAL ("347", "14:35")
/// — there is nothing to play until the reading is out, so unlike the letter
/// drill this surface has no prompt audio at all: every fire here is a reveal.
///
/// The forms are generated, so no catalog lists them and the ordinary
/// catalog-keyed lookup finds nothing. `AppModel.speakAction` falls back to the
/// live voice, which is what makes a drill answer audible at all.
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
        return model?.speakAction(for: form, lang: language)
    }

    var correctionPlaying: Bool {
        guard case .almost(let form, _) = feedback else { return false }
        return model?.isSpeaking(form, lang: language) ?? false
    }

    /// Fires once when the answer comes out, however it came out. `.auto`, so
    /// the read-aloud switch and VoiceOver both still veto it — a tap on the
    /// speaker outranks the mute, this does not.
    func autoplayAnswer() {
        guard let model, let form = spokenAnswer else { return }
        let position = index
        Task { @MainActor in
            // why: the correct/wrong chime lands first — the same 300 ms the
            // review session waits, or the word starts under the chime.
            try? await Task.sleep(for: .milliseconds(300))
            // why: a fast "Weiter" can move the run while this waits; the
            // answer to a task already gone must not speak over the new one.
            guard index == position else { return }
            model.speakAloud(form, lang: language)
        }
    }
}
