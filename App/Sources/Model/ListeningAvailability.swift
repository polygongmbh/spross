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
/// Deliberately NOT cached, and asked ONCE PER RUN: a voice may be installed in
/// Settings while the app sleeps, so the sweep reads the voice table as it finds
/// it when a run opens rather than deciding anything at launch.
///
/// Home does not ask this at all. Its card stands on the box holding words, and
/// nothing more: every catalog language but `en` ships several hundred
/// recordings and `en` is spoken by every device there is, so a joined box with
/// nothing sayable in it does not occur, and walking the whole join to prove
/// that again on every glance at Home would be a catalog question no screen
/// should pay for.
@MainActor
struct ListeningAvailability {

    /// Everything a run draws from — handed to `ListeningRun.idle` whole.
    let report: ListeningPool.Report

    init(model: AppModel) {
        report = Self.built(model: model)
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
