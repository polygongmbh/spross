import Foundation
import SprossKern

/// What the letter drill can ASK on THIS device — kern's report, with the one
/// fact kern cannot know handed in.
///
/// The whole port is `hasVoice`: whether this device can say ANYTHING in the
/// language. Which alphabet rows are promptable, which held words can be
/// dictated, where a run opens and how far it reaches are all kern's
/// (`LetterDrillAvailability`), out of its own catalog and box.
///
/// Deliberately NOT cached beyond one read: a voice may be installed in
/// Settings while the app sleeps, so the pages rebuild this on every foreground
/// rather than deciding once at launch that the drill does not exist.
@MainActor
struct LetterDrillAvailability {

    /// Everything a run draws from — handed to `LetterDrillRunConfig` whole.
    let report: SprossKern.LetterDrillAvailability.Report

    init(model: AppModel, language: String) {
        report = Self.built(model: model, language: language)
    }

    var drillAvailable: Bool { report.drillAvailable }
    var dictationAvailable: Bool { report.dictationAvailable }
    /// The stage a run would OPEN on — kern's step from the words the learner
    /// already holds, capped by what this device can reach.
    var entryStage: LetterStage { report.entryStage }

    /// A profile with no catalog or no box can ask nothing; kern's own empty
    /// report says so without a second predicate on this side.
    private static func built(model: AppModel,
                              language: String) -> SprossKern.LetterDrillAvailability.Report {
        guard let catalog = model.catalog, let box = model.box else {
            return .init(language: language, alphabet: nil, promptableRefs: [],
                         dictationCandidates: [], gapWords: [:], consolidatedCards: 0)
        }
        return SprossKern.LetterDrillAvailability.shared
            .report(catalog: catalog, box: box, language: language,
                    hasVoice: Pronouncer.shared.canSpeak(language: language))
    }
}
