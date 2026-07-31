import Foundation

// MARK: - TrainerRecords
//
// The best run a drill has ever produced, per drill and language.
//
// Kept in UserDefaults rather than the box document on purpose: a drill run
// touches no card and no schedule, so it is not box state — losing a record
// costs a number, where anything in the box costs learning history.

enum TrainerRecords {
    private static let prefix = "trainer.record."

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

    #if DEBUG
    /// UI-test hook: drop a drill's record so the new-record state can be driven.
    static func clear(_ key: String) {
        UserDefaults.standard.removeObject(forKey: prefix + key)
    }
    #endif
}
