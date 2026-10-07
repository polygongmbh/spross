import SwiftUI
import SprossKern

/// The one place a question's `Reading` becomes sound, for a review card and a
/// drill alike: the prompt at once, the answer once the verdict's chime has
/// landed, each at most once per `Reading.key` (`docs/read-aloud.md`). What is
/// said is kern's; this only times it. The Android twin is `rememberReadAloud`.
///
/// The answer's wait is held rather than fired and forgotten: the beat outlives
/// a fast tap, and a reveal closed within it would otherwise speak its answer
/// over whatever screen replaced the question.
@MainActor
final class Reader {
    /// The longest a beat waits on a reading — a ceiling for an end that never
    /// arrives, far past any word or phrase a question says.
    private static let longestReading: Duration = .seconds(8)

    private var key: String?
    /// The sides already said under `key`: false the prompt, true the answer.
    private var said: Set<Bool> = []
    private var pending: Task<Void, Never>?
    /// The answer being said, until the reader is hushed — what a beat waits on.
    private var owed: (form: String, lang: String, model: AppModel)?

    /// Says whatever of `reading` has not been said yet. Idempotent: every
    /// hook that may see a new question or a verdict calls it, and only the
    /// first sight of each side sounds. `.auto` throughout, so the read-aloud
    /// switch and VoiceOver both still veto it — a tap outranks the mute, this
    /// does not. A nil `model` (a preview) says nothing.
    func follow(_ reading: Reading?, model: AppModel?) {
        guard let reading, let model else { return }
        if reading.key != key {
            key = reading.key
            said = []
        }
        // why: once per key — an approval withdrawn by typing past it and
        // given again must not say the word a second time.
        if let prompt = reading.prompt, said.insert(false).inserted {
            model.pronounceAloud(prompt.form, lang: prompt.lang, article: prompt.article)
        }
        if let answer = reading.answer, said.insert(true).inserted {
            sayAnswer(answer, via: model)
        }
    }

    private func sayAnswer(_ answer: Saying, via model: AppModel) {
        pending?.cancel()
        owed = (answer.form, answer.lang, model)
        pending = Task { @MainActor in
            // why: the correct/wrong chime lands first, or the word starts under it.
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            pending = nil
            model.pronounceAloud(answer.form, lang: answer.lang, article: answer.article)
        }
    }

    /// Returns once the owed answer has been said, or was never going to sound
    /// (muted, no voice, cut off by a tap).
    func saidAnswer() async {
        let start = ContinuousClock.now
        while saying, !Task.isCancelled, ContinuousClock.now - start < Self.longestReading {
            try? await Task.sleep(for: .milliseconds(100))
        }
    }

    private var saying: Bool {
        guard let owed else { return false }
        return pending != nil || owed.model.isPronouncing(owed.form, lang: owed.lang)
    }

    /// A new turn under the same key — a card dealt again straight after itself
    /// — says both its sides afresh.
    func forget() {
        key = nil
        said = []
    }

    /// Silence, and drop a wait that has not fired yet — a reading belongs to
    /// the question that revealed it and to nothing after.
    func hush() {
        pending?.cancel()
        pending = nil
        owed = nil
        Pronouncer.shared.stop()
    }
}
