import SwiftUI

/// The pending "say the answer" wait, held rather than fired and forgotten:
/// the beat outlives a fast tap, and a reveal closed within it would otherwise
/// speak its answer over whatever screen replaced the run. Every surface that
/// reads an answer out holds one of these, so the beat and the cancel cannot
/// drift apart between them.
@MainActor
final class AnswerVoice {
    private var pending: Task<Void, Never>?

    /// Say `form` once the chime has landed. `.auto`, so the read-aloud switch
    /// and VoiceOver both still veto it — a tap on the speaker outranks the
    /// mute, this does not.
    func speak(_ form: String, lang: String, via model: AppModel) {
        pending?.cancel()
        pending = Task { @MainActor in
            // why: the correct/wrong chime lands first — the same 300 ms the
            // review session waits, or the word starts under the chime.
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            model.pronounceAloud(form, lang: lang)
        }
    }

    /// Silence, and drop a wait that has not fired yet — a reading belongs to
    /// the task that revealed it and to nothing after.
    func hush() {
        pending?.cancel()
        pending = nil
        Pronouncer.shared.stop()
    }
}

extension View {

    /// Says a form the moment the learner is owed one, once per answer: the
    /// trigger is "is a form owed", so a slip and a miss both speak and the
    /// neutral state that follows resets it. nil says nothing — a run with no
    /// voice, or a side that is never read out, simply hands nil over.
    func saysOwedAnswer(_ form: String?, lang: String, via model: AppModel?,
                        voice: AnswerVoice) -> some View {
        onChange(of: form) { _, owed in
            guard let owed, let model else { return }
            voice.speak(owed, lang: lang, via: model)
        }
    }
}

extension AnswerInputView.Feedback {

    /// The form currently owed to the learner: the correction after a slip,
    /// otherwise the revealed answer. nil while the answer is still theirs to
    /// produce — nothing may speak an answer to a question still standing.
    func owedForm(revealing display: String) -> String? {
        switch self {
        case .almost(let form, _): return form
        case .revealed: return display
        case .neutral, .correct: return nil
        }
    }
}
