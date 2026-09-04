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
/// Deliberately NOT cached: a voice may be installed in Settings while the app
/// sleeps, so Home re-asks this on every foreground rather than deciding once
/// at launch that the mode does not exist — the letter drill's discipline
/// (`LetterDrillAvailability`), for the same reason.
///
/// Two ways in, because the two questions cost wildly different things.
/// [offered] is the card's gate and stops at the first word this device can
/// say — that is what Home asks. Building one of these deals the whole
/// playlist instead, and belongs to a run being opened (`ListeningDriver`),
/// never to a glance at Home.
@MainActor
struct ListeningAvailability {

    /// Everything a run draws from — handed to `ListeningRun.idle` whole.
    let report: ListeningPool.Report

    init(model: AppModel) {
        report = Self.built(model: model)
    }

    /// The two device facts the walk needs, read where they live — the voice
    /// table is UIKit's and main-actor only. Handed to [offered] so the walk
    /// itself can happen off this actor.
    static func voices(model: AppModel) -> (source: Bool, target: Bool)? {
        guard let target = model.targetLanguage else { return nil }
        return (Pronouncer.shared.canSpeak(language: model.sourceLanguage),
                Pronouncer.shared.canSpeak(language: target))
    }

    /// Whether the card stands: kern stops at the first word both halves of a
    /// turn can say, so this is the cheap question Home is allowed to ask.
    nonisolated static func offered(
        catalog: Catalog, box: BoxState, source: String, target: String,
        hasSourceVoice: Bool, hasTargetVoice: Bool,
    ) -> Bool {
        ListeningPool.shared.offered(
            catalog: catalog, box: box, source: source, target: target,
            hasTargetVoice: hasTargetVoice, hasSourceVoice: hasSourceVoice)
    }

    var candidates: [ListeningCandidate] { report.candidates }

    /// The playlist itself, dealt for ONE run: the walk of the whole join with
    /// two catalog lookups per card, which is why it waits for a run to be
    /// opened rather than running on the way past the card.
    ///
    /// A profile with no catalog, no box or no target language can play
    /// nothing; kern's own empty report says so without a second predicate on
    /// this side.
    private static func built(model: AppModel) -> ListeningPool.Report {
        guard let catalog = model.catalog, let box = model.box, let target = model.targetLanguage
        else { return .init(candidates: []) }
        let source = model.sourceLanguage
        return ListeningPool.shared.report(
            catalog: catalog, box: box, source: source, target: target,
            hasTargetVoice: Pronouncer.shared.canSpeak(language: target),
            hasSourceVoice: Pronouncer.shared.canSpeak(language: source),
            seed: Date().epochMillis
        )
    }
}
