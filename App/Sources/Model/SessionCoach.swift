import SwiftUI
import SprossKern

/// The three lines the FIRST round teaches itself with, one per moment the learner
/// meets: give the word a moment, answer honestly, write the one you missed.
///
/// The same three the onboarding page said in advance — said again where each applies,
/// because a page read once before any card is not where a rule lands. It is copy and
/// nothing else: no step is added, no button waits on it, and a learner who ignores it
/// gets exactly the round everyone else does.
///
/// It runs for the opening cards of one round only. The flag behind it is persisted
/// (`AppModel.coachPending`), so an app killed mid-round still has its lesson coming;
/// spending it is `closeSession`'s.
enum SessionCoach {

    /// How many cards wear the lines. Three is a whole first turn and then some: long
    /// enough that a missed word has come up, short enough that the screen goes quiet
    /// while the round is still new.
    static let cards = 3

    /// The line the prompt owes — the recall this presentation is asking for. Recognition
    /// only, and only while the answer is still hidden: produce says what it wants with a
    /// field and a keyboard, and a revealed card is past the moment this describes.
    static func recallLine(role: PresentationRole, revealed: Bool) -> LocalizedStringKey? {
        guard role == .recognize, !revealed else { return nil }
        return "session.coach.recall"
    }

    /// What stands under the self-grade row. It REPLACES the standing question rather
    /// than joining it: two quiet lines in one slot is one more than anybody reads.
    // why: computed, not stored — LocalizedStringKey is not Sendable, so a static
    // constant of one is a Swift 6 concurrency error.
    static var gradeCaption: LocalizedStringKey { "session.coach.grade" }

    /// The write-out step's line — what typing a word you missed is for.
    static var writeLine: LocalizedStringKey { "session.coach.write" }
}
