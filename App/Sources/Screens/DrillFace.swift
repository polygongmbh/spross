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
    associatedtype Run
    /// The reading matter under the start button; a table of its own per drill.
    associatedtype Reference: View

    // MARK: - Who the drill is

    /// The prefix under which the Sprosse and the record are kept, and the name
    /// the hub knows the skill by.
    static var key: String { get }

    /// What the tile a closed run leaves calls it.
    static var resultTitle: LocalizedStringKey { get }

    /// The page's title, around the name of the language being learned.
    static func title(_ language: String) -> LocalizedStringKey

    /// How the ladder is walked, in one line under the Sprossen.
    static var paceKey: LocalizedStringKey { get }

    /// How far a run has come, where one has.
    static func bestLine(_ best: Int) -> LocalizedStringKey

    /// What fast mode buys, once it is paid for.
    static var fastHintKey: LocalizedStringKey { get }

    /// The reverse switch's line — a runtime `%@ %@` pair, asked side first.
    static var reverseHintKey: String { get }

    // MARK: - The material

    static func content(_ catalog: Catalog?, source: String, target: String) -> Content?

    /// The ladder as the switches stand — kern's, never a count written down here.
    /// Answered without content too, because the fast row prices itself against it.
    static func ceiling(_ content: Content?, reverse: Bool) -> Int

    /// What each Sprosse of that ladder asks, in the order it is climbed. Empty
    /// where the drill has nothing to say without content.
    static func sprossen(_ content: Content?, reverse: Bool) -> [DrillSprosse]

    /// Whether fast mode may be picked at all — kern's rule on the stored best.
    static func fastUnlocked(best: Int, content: Content, reverse: Bool) -> Bool

    @ViewBuilder
    static func reference(model: AppModel, content: Content,
                          source: String, target: String) -> Reference

    // MARK: - The run

    /// The language an answer is owed in — the learned one, or the learner's own
    /// where the run was turned round.
    static func answerLanguage(content: Content, reverse: Bool) -> String

    /// A fresh run. `level` opens it partway up the ladder (a run-through hook
    /// only); nil is where every real run starts.
    static func open(content: Content, reverse: Bool, fast: Bool,
                     normalizer: AnswerNormalizer?, level: Int?) -> Run

    /// The run as the screen draws it.
    static func snapshot(_ run: Run) -> DrillSnapshot

    /// One event, put to kern in its own vocabulary.
    static func reduce(_ run: Run, _ move: DrillMove) -> DrillStep<Run>

    /// The ✕, and the end of a run that ran out of questions.
    static func close(_ run: Run, standingRecord: Int) -> DrillEnd<Run>

    #if DEBUG
    /// `-uitest-<drill>-level N`: the Sprosse a run-through opens on.
    static var uitestLevelKey: String { get }

    /// `-uitest-<drill>-best N`: the standing ladder a run-through inherits.
    static var uitestBestKey: String { get }

    /// A run standing mid-streak, which a screenshot run has no thumb to reach.
    static func seedStreak(_ run: Run, _ streak: Int) -> Run
    #endif
}

// MARK: - What the two sides say to each other

/// What the learner did, as the shared screen knows it. Kern spells each of
/// these as an intent of its own per drill; the face is where the vocabularies
/// meet, so the screen never names one machine's.
enum DrillMove {
    /// A live keystroke: an answer finished exactly right needs no check tap.
    case typed(String)
    /// Check/Enter with text standing.
    case submitted(String)
    /// "Aufdecken" on an empty field.
    case revealed
    /// The explicit tap that books whatever the feedback already said.
    case confirmed
    /// The platform's armed beat elapsed.
    case advanced
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
    let bestLevel: Int
    let effects: [DrillEffect]
}

/// The whole of what the drill screen draws: the question on the card and the
/// figures around it, lifted off kern's run so the screen reads one shape
/// whichever machine is running under it.
struct DrillSnapshot {
    /// Bumped per question — the card's identity and what an autoplay keys on.
    let index: Int
    let level: Int
    let streak: Int
    let bestStreak: Int
    let tally: DrillTally
    let outcomes: [AnswerOutcome]
    let feedback: TurnFeedback
    /// The way out, where it is wanted: on the SECOND miss in a row.
    let offersFinish: Bool
    /// Nothing left to ask.
    let finished: Bool
    /// BCP-47 of the language the answer is owed in.
    let answerLanguage: String
    /// BCP-47 of the language the prompt is written in.
    let promptLanguage: String

    /// What the question asks, in words.
    let ask: LocalizedStringKey
    /// The name or line asked about; nil where a picture alone is the question.
    let promptText: String?
    /// The picture beside the words, where the drill has one.
    let promptEmoji: String?
    /// Whether showing that picture while the answer is owed would answer it.
    let emojiIsGiveaway: Bool
    /// The canonical answer, for the reveal.
    let display: String
    /// The answer side's neighboring form, where kern hands one over.
    let gloss: String?
    /// What a refused answer actually named — only beside a revealed miss.
    let otherWord: MatchOtherWord?
}

/// One Sprosse of a ladder as the overview words it.
struct DrillSprosse {
    let title: LocalizedStringKey
    let hint: LocalizedStringKey
}

/// Scroll targets on the overview. Here rather than on the page itself: a
/// `static let` cannot live on a generic type.
enum DrillAnchor {
    /// The tile a closed run leaves.
    static let result = "result"
    /// The tile carrying the reverse and fast switches.
    static let modifiers = "modifiers"
}
