import Foundation
import SprossKern

/// What the sentence scramble can ASK on THIS profile — kern's report, whole.
///
/// No capability port, for the same reason its word sibling needs none: an
/// arrangement is tapped, never heard. Which phrases are unlocked, how they cut
/// into atoms and how tall the ladder of lengths runs are all kern's
/// (`SentenceScrambleAvailability`), out of the box alone.
///
/// Deliberately NOT cached: phrases unlock as their words grow, so the hub
/// rebuilds this rather than deciding once at launch that the drill is empty.
@MainActor
struct SentenceScrambleAvailability {

    /// Everything a run draws from — handed to `SentenceScrambleRunConfig` whole.
    let report: SprossKern.SentenceScrambleAvailability.Report

    init(model: AppModel) {
        report = Self.built(model: model)
    }

    var drillAvailable: Bool { report.drillAvailable }

    /// The longest phrase the box has unlocked, as a Sprosse count — kern's.
    var maxLevel: Int { Int(report.maxLevel) }

    /// A profile with no box can ask nothing; kern's own empty report says so
    /// without a second predicate on this side.
    private static func built(model: AppModel) -> SprossKern.SentenceScrambleAvailability.Report {
        guard let box = model.box else { return .init(phrases: []) }
        return SprossKern.SentenceScrambleAvailability.shared.report(box: box)
    }
}
