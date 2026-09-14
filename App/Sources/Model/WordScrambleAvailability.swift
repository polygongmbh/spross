import Foundation
import SprossKern

/// What the word scramble can ASK on THIS profile — kern's report, whole.
///
/// Unlike the letter drill there is no capability port to hand in: a word with
/// its letters mixed is written, never heard, so nothing about it is a device
/// fact. Which words are eligible, how many there must be before the drill
/// exists at all and how tall its ladder runs are all kern's
/// (`WordScrambleAvailability`), out of the box alone.
///
/// Deliberately NOT cached: the pool grows as words consolidate, so the hub
/// rebuilds this rather than deciding once at launch that the drill is empty.
@MainActor
struct WordScrambleAvailability {

    /// Everything a run draws from — handed to `WordScrambleRunConfig` whole.
    let report: SprossKern.WordScrambleAvailability.Report

    init(model: AppModel) {
        report = Self.built(model: model)
    }

    var drillAvailable: Bool { report.drillAvailable }

    /// The top of the masking ladder — kern's, never a count written down here.
    var maxLevel: Int { Int(report.maxLevel) }

    /// A profile with no box can ask nothing; kern's own empty report says so
    /// without a second predicate on this side.
    private static func built(model: AppModel) -> SprossKern.WordScrambleAvailability.Report {
        guard let box = model.box else { return .init(words: []) }
        return SprossKern.WordScrambleAvailability.shared.report(box: box)
    }
}
