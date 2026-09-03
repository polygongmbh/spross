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
/// sleeps, so Home rebuilds this on every foreground rather than deciding once
/// at launch that the mode does not exist — the letter drill's discipline
/// (`LetterDrillAvailability`), for the same reason.
@MainActor
struct ListeningAvailability {

    /// Everything a run draws from — handed to `ListeningRun.idle` whole.
    let report: ListeningPool.Report

    init(model: AppModel) {
        report = Self.built(model: model)
    }

    init(report: ListeningPool.Report) {
        self.report = report
    }

    /// The two device facts the sweep needs, read where they live — the voice
    /// table is UIKit's and main-actor only. Handed to [swept] so the walk
    /// itself, which is the whole join with two catalog lookups per card, can
    /// happen off this actor.
    static func voices(model: AppModel) -> (source: Bool, target: Bool)? {
        guard let target = model.targetLanguage else { return nil }
        return (Pronouncer.shared.canSpeak(language: model.sourceLanguage),
                Pronouncer.shared.canSpeak(language: target))
    }

    /// The pool sweep itself: pure kern over immutable values, so it runs
    /// wherever the caller puts it.
    nonisolated static func swept(
        catalog: Catalog, box: BoxState, source: String, target: String,
        hasSourceVoice: Bool, hasTargetVoice: Bool,
    ) -> ListeningPool.Report {
        ListeningPool.shared.report(
            catalog: catalog, box: box, source: source, target: target,
            hasTargetVoice: hasTargetVoice, hasSourceVoice: hasSourceVoice,
            seed: Date().epochMillis)
    }

    /// Whether the Home card stands at all.
    var available: Bool { report.available }

    var candidates: [ListeningCandidate] { report.candidates }

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
