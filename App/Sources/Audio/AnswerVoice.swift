import SwiftUI

/// The pending "say the answer" wait, held rather than fired and forgotten:
/// the beat outlives a fast tap, and a reveal closed within it would otherwise
/// speak its answer over whatever screen replaced the run. Every drill holds
/// one of these, so the beat, the reading and the cancel cannot drift apart
/// between them.
@MainActor
final class AnswerVoice {
    /// The longest a beat waits on a reading — a ceiling for an end that never
    /// arrives, far past any word or phrase a drill says.
    private static let longestReading: Duration = .seconds(8)

    private var pending: Task<Void, Never>?
    /// The form being said, until the voice is hushed — what a beat waits on.
    private var owed: (form: String, lang: String, model: AppModel)?

    /// Say `form` once the chime has landed. `.auto`, so the read-aloud switch
    /// and VoiceOver both still veto it — a tap on the speaker outranks the
    /// mute, this does not.
    func speak(_ form: String, lang: String, via model: AppModel) {
        pending?.cancel()
        owed = (form, lang, model)
        pending = Task { @MainActor in
            // why: the correct/wrong chime lands first — the same 300 ms the
            // review session waits, or the word starts under the chime.
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            pending = nil
            model.pronounceAloud(form, lang: lang)
        }
    }

    /// Returns once the owed form has been said, or was never going to sound
    /// (muted, no voice, cut off by a tap).
    func said() async {
        let start = ContinuousClock.now
        while saying, !Task.isCancelled, ContinuousClock.now - start < Self.longestReading {
            try? await Task.sleep(for: .milliseconds(100))
        }
    }

    private var saying: Bool {
        guard let owed else { return false }
        return pending != nil || owed.model.isPronouncing(owed.form, lang: owed.lang)
    }

    /// Silence, and drop a wait that has not fired yet — a reading belongs to
    /// the task that revealed it and to nothing after.
    func hush() {
        pending?.cancel()
        pending = nil
        owed = nil
        Pronouncer.shared.stop()
    }
}
