import Foundation
import SprossKern

/// What listening can PLAY on THIS device — kern's pool, with the two facts
/// kern cannot know handed in.
///
/// The whole port is `hasVoice`, twice: a turn says the target word and then
/// its meaning, so a candidate needs audio on BOTH sides, and a profile
/// routinely has a voice for one language and not the other (a `sw` learner on
/// iOS has recordings but no synthesizer). Which cards join, which are
/// suspended, and how the whole sayable join becomes the pool are all kern's
/// (`ListeningPool`).
///
/// Taken ONCE PER OPEN: a voice may be installed in Settings while the app
/// sleeps, so the voice table is read as it stands when a run opens. It is also
/// the key a closed run is kept on (`AppModel.keptListening`): the same box and
/// the same voices are what lets a run be taken up again rather than dealt anew.
///
/// Home does not ask this at all. Its card stands on the box holding words, and
/// nothing more: every catalog language but `en` ships several hundred
/// recordings and `en` is spoken by every device there is, so a joined box with
/// nothing sayable in it does not occur, and walking the whole join to prove
/// that again on every glance at Home would be a catalog question no screen
/// should pay for.
struct ListeningAvailability: Sendable {
    let catalog: Catalog
    let box: BoxState
    let source: String
    let target: String
    let hasTargetVoice: Bool
    let hasSourceVoice: Bool

    /// Nil for a profile with no catalog, no box or no target language, which
    /// can play nothing.
    @MainActor
    init?(model: AppModel) {
        guard let catalog = model.catalog, let box = model.box, let target = model.targetLanguage
        else { return nil }
        self.catalog = catalog
        self.box = box
        source = model.sourceLanguage
        self.target = target
        hasTargetVoice = Pronouncer.shared.canSpeak(language: target)
        hasSourceVoice = Pronouncer.shared.canSpeak(language: source)
    }

    /// Whether a run dealt from `other` still stands for this one: nothing of
    /// the box the pool reads has moved (`ListeningPool.dealsAlike`), and the
    /// same voices.
    func deals(like other: ListeningAvailability) -> Bool {
        ListeningPool.shared.dealsAlike(dealt: other.box, now: box)
            && hasTargetVoice == other.hasTargetVoice && hasSourceVoice == other.hasSourceVoice
    }

    /// The playlist itself, dealt for ONE run, away from the main thread: the
    /// walk of the whole join with two catalog lookups per card.
    func deal() async -> ListeningPool.Report {
        let seed = Date().epochMillis
        return await Task.detached(priority: .userInitiated) { [self] in
            ListeningPool.shared.report(
                catalog: catalog, box: box, source: source, target: target,
                hasTargetVoice: hasTargetVoice, hasSourceVoice: hasSourceVoice, seed: seed
            )
        }.value
    }
}
