import Foundation
import SprossKern

/// The one question both scramble reports answer, so one wrapper can be asked
/// it: can this box be asked at all. Kern keeps the two reports apart on
/// purpose — a mixed spelling is drawn from grown WORDS and a shuffled phrase
/// from unlocked PHRASES — so what they have in common is read here and not
/// minted there.
protocol ScrambleReport {
    var drillAvailable: Bool { get }
}

extension SprossKern.WordScrambleAvailability.Report: ScrambleReport {}
extension SprossKern.SentenceScrambleAvailability.Report: ScrambleReport {}

/// What a scramble can ASK on THIS profile — kern's report, whole.
///
/// Neither scramble has a capability port to hand in: a word with its letters
/// mixed is written and a phrase is tapped back into order, so nothing about
/// either is a device fact. Which material is eligible, how much of it there
/// must be before the drill exists at all and how tall the ladder runs are all
/// kern's, out of the box alone.
///
/// Deliberately NOT cached: the pool grows as words consolidate and as the join
/// unlocks phrases, so the hub rebuilds this rather than deciding once at
/// launch that a drill is empty.
@MainActor
struct ScrambleAvailability<Report: ScrambleReport> {

    /// Everything a run draws from — handed to the run config whole.
    let report: Report

    var drillAvailable: Bool { report.drillAvailable }

    /// A profile with no box can ask nothing; kern's own empty report says so
    /// without a second predicate on this side.
    init(model: AppModel, empty: @autoclosure () -> Report, of sweep: (BoxState) -> Report) {
        report = model.box.map(sweep) ?? empty()
    }
}

typealias WordScrambleAvailability =
    ScrambleAvailability<SprossKern.WordScrambleAvailability.Report>

typealias SentenceScrambleAvailability =
    ScrambleAvailability<SprossKern.SentenceScrambleAvailability.Report>

extension ScrambleAvailability where Report == SprossKern.WordScrambleAvailability.Report {
    init(model: AppModel) {
        self.init(model: model, empty: .init(words: []),
                  of: { SprossKern.WordScrambleAvailability.shared.report(box: $0) })
    }
}

extension ScrambleAvailability where Report == SprossKern.SentenceScrambleAvailability.Report {
    init(model: AppModel) {
        self.init(model: model, empty: .init(phrases: []),
                  of: { SprossKern.SentenceScrambleAvailability.shared.report(box: $0) })
    }
}
