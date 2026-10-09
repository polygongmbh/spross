import SwiftUI
import SprossKern

/// What ONE typed drill has that another does not.
///
/// The atlas run and the dates run are the same screen: the card, the field,
/// the one primary action, the amber hold, the second-miss finish offer, the
/// close and the page that starts them are `DrillRunView` and `DrillOverview`.
/// What differs is the MATERIAL — a kern machine of its own, the words the
/// chrome says about it, and the reading matter under the start button — and
/// this is the whole of that difference, written once per drill.
///
/// Kern keeps the two machines apart on purpose (a calendar and an atlas share
/// no ladder), so Kotlin hands over two unrelated Swift types; the associated
/// types are the only place they meet, and nothing above this line names either.
///
/// A face is a namespace, not a value — every member is static, and the screens
/// carry it as their generic parameter rather than as state.
@MainActor
protocol DrillFace {
    /// The joined material one run is fixed to, kern's — the calendars, the atlas.
    associatedtype Content
    /// Kern's whole run state.
    associatedtype Run: DrillRunProgress
    /// The reading matter under the start button; a table of its own per drill.
    associatedtype Reference: View

    // MARK: - Who the drill is

    /// The name the hub and the UI tests know the skill by.
    static var key: String { get }

    /// Where a pair's Sprosse and record are kept (kern's `storageKey`) — one key per PAIR,
    /// because the material is a pair's and not a language's.
    static func storageKey(source: String, target: String) -> String

    /// The roster entry this face runs.
    static var drill: Drill { get }


    /// What the tile a closed run leaves calls it.
    static var resultTitle: LocalizedStringKey { get }

    /// The page's title, around the name of the language being learned.
    static func title(_ language: String) -> LocalizedStringKey

    /// Clean answers one Sprosse costs at the normal pace — kern's count, which
    /// the fast switch's line is priced against rather than spelling it out.
    static var winsToAdvance: Int { get }

    /// The reverse switch's line as the switch stands — a runtime `%@ %@` pair,
    /// asked side first. Two lines where a direction changes what is owed and
    /// not merely which language owes it.
    static func reverseHintKey(reverse: Bool) -> String

    // MARK: - The material

    static func content(_ catalog: Catalog?, source: String, target: String) -> Content?

    /// The ladder as the switches stand — kern's, never a count written down here.
    /// Answered without content too, because the fast row prices itself against it.
    static func ceiling(_ content: Content?, reverse: Bool) -> Int

    /// What each Sprosse of that ladder is called, in the order it is climbed —
    /// one line apiece. Empty where the drill has nothing to say without content.
    static func sprossen(_ content: Content?, reverse: Bool) -> [LocalizedStringKey]

    /// Whether fast mode may be picked at all — kern's rule on the stored best.
    static func fastUnlocked(best: Int, content: Content, reverse: Bool) -> Bool

    /// The Sprosse that earns fast mode, as its locked switch prices it — kern's.
    static func fastPrice(_ content: Content?, reverse: Bool) -> Int

    @ViewBuilder
    static func reference(model: AppModel, content: Content,
                          source: String, target: String) -> Reference

    // MARK: - The run

    /// The language an answer is owed in — the learned one, or the learner's own
    /// where the run was turned round.
    static func answerLanguage(content: Content, reverse: Bool) -> String

    /// A fresh run. `sprosse` is the Sprosse the page opens it on — the lowest one
    /// not yet answered out, or the one tapped; nil is the foot of the ladder.
    /// `standingRecord` is the store's record for the page and `cleared` the
    /// Sprossen answered out in this direction — what a pause for improving is
    /// measured against.
    static func open(content: Content, reverse: Bool, fast: Bool,
                     normalizer: AnswerNormalizer?, sprosse: Int?, standingRecord: Int,
                     cleared: Set<KotlinInt>) -> Run

    /// The run as the screen draws it.
    static func snapshot(_ run: Run) -> DrillSnapshot

    /// One event, put to kern in its own vocabulary.
    static func reduce(_ run: Run, _ move: DrillMove) -> DrillStep<Run>

    /// The ✕, and the end of a run that ran out of questions.
    static func close(_ run: Run, standingRecord: Int) -> DrillEnd<Run>

    #if DEBUG
    /// `-uitest-<drill>-level N`: the Sprosse a run-through opens on.
    static var uitestSprosseKey: String { get }

    /// `-uitest-<drill>-best N`: the standing ladder a run-through inherits.
    static var uitestBestKey: String { get }

    /// A run standing mid-streak, which a screenshot run has no thumb to reach.
    static func seedAnswerStreak(_ run: Run, _ answerStreak: Int) -> Run
    #endif
}

// MARK: - What the two sides say to each other

/// What the learner did, as the shared screen knows it. Kern spells each of
/// these as an intent of its own per drill; the face is where the vocabularies
/// meet, so the screen never names one machine's.
enum DrillMove {
    /// A live keystroke: an answer finished exactly right needs no check tap.
    case typed(String)
    /// Check/Enter: kern checks the text standing, and reveals when none does.
    case submitted(String)
    /// The explicit tap that books whatever the feedback already said.
    case confirmed
    /// The platform's armed beat elapsed.
    case advanced
    /// Keep practicing, from a pause kern called.
    case keptPracticing
}

/// One reduction: the run that follows, and what it asks the platform for.
struct DrillStep<Run> {
    let run: Run
    let effects: [DrillEffect]
}

/// A closed run: the figures for the page that started it, and the furthest
/// Sprosse it stood on for that page to file.
struct DrillEnd<Run> {
    let run: Run
    /// nil ⇒ the run was never answered: dismiss, store nothing.
    let summary: DrillRunSummary?
    let bestSprosse: Int
    /// The Sprossen this run answered OUT, for the page to add to what it holds.
    let clearedSprossen: Set<KotlinInt>
    let effects: [DrillEffect]
}

/// The whole of what the drill screen draws: the question on the card and the
/// figures around it, lifted off kern's run so the screen reads one shape
/// whichever machine is running under it.
struct DrillSnapshot {
    /// Bumped per question — the card's identity and what an autoplay keys on.
    let index: Int
    let sprosse: Int
    let answerStreak: Int
    let bestAnswerStreak: Int
    let tally: DrillTally
    let outcomes: [AnswerOutcome]
    let feedback: TurnFeedback
    /// Nothing left to ask.
    let finished: Bool
    /// BCP-47 of the language the answer is owed in.
    let answerLanguage: String
    /// The card, as kern states it.
    let question: Question
    /// The tiles this question is answered off, in kern's own shuffled order —
    /// nil where it is written instead, which is every Sprosse above the
    /// calendar's warm-up.
    let choices: [String]?
    /// Whether a DATE is owed rather than a reading — the calendar turned
    /// round. The keyboard and the placeholder are all that follows from it.
    let digits: Bool
}

/// Scroll targets on the overview. Here rather than on the page itself: a
/// `static let` cannot live on a generic type.
enum DrillAnchor {
    /// The tile a closed run leaves.
    static let result = "result"
    /// The tile carrying the reverse and fast switches.
    static let modifiers = "modifiers"
}
