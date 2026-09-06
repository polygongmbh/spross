import Foundation
import SprossKern

// MARK: - TrainerRecords
//
// The best run a drill has ever produced, per drill and language: the
// longest clean streak, and — for the atlas and the calendar — the most
// answers one run took, right or wrong.
//
// Kept in UserDefaults rather than the box document on purpose: a drill run
// touches no card and no schedule, so it is not box state — losing a record
// costs a number, where anything in the box costs learning history.

enum TrainerRecords {
    private static var prefix: String { TrainerMode.companion.RECORD_PREFIX }

    static func best(for key: String) -> Int {
        UserDefaults.standard.integer(forKey: prefix + key)
    }

    /// Books `streak` as the new record if it beats the standing one, and says
    /// whether it did. Strictly greater, so returning to a summary that has
    /// already been booked never claims the record a second time.
    @discardableResult
    static func record(_ streak: Int, for key: String) -> Bool {
        guard streak > best(for: key) else { return false }
        UserDefaults.standard.set(streak, forKey: prefix + key)
        return true
    }

    // MARK: - Answers in one run

    private static var answersPrefix: String { TrainerMode.companion.ANSWERS_PREFIX }

    /// The most answers one run under `key` ever took, or 0 where none has closed.
    static func bestAnswers(for key: String) -> Int {
        UserDefaults.standard.integer(forKey: answersPrefix + key)
    }

    /// Books `answers` where it beats the standing figure. Strictly greater, like
    /// the streak — and unlike it no cheer follows: a longer run is not a better one.
    static func recordAnswers(_ answers: Int, for key: String) {
        guard answers > bestAnswers(for: key) else { return }
        UserDefaults.standard.set(answers, forKey: answersPrefix + key)
    }

    #if DEBUG
    /// UI-test hook: drop a drill's record so the new-record state can be driven.
    static func clear(_ key: String) {
        UserDefaults.standard.removeObject(forKey: prefix + key)
    }
    #endif
}
