import Foundation

// MARK: - TrainerProgress
//
// The highest rung a drill has ever reached, per variant and language.
// It is the one source the unlock ladder reads: everything a learner has
// earned is derived from these numbers, never tracked a second time.
//
// UserDefaults rather than the box document, for the same reason
// TrainerRecords lives there (docs/design.md:156-159): a drill run touches
// no card and no schedule, so it is not box state — losing a rung costs a
// climb, where anything in the box costs learning history.
//
// Separate from TrainerRecords despite the identical shape: a streak record
// is kept per RUN SELECTION, a rung per variant, and the two keys drift apart
// as soon as a run can offer more than one variant.

enum TrainerProgress {
    private static let prefix = "trainer.level."

    /// The best rung booked for `key`, or 0 where the variant was never run.
    static func best(for key: String) -> Int {
        UserDefaults.standard.integer(forKey: prefix + key)
    }

    /// Books `level` as the new best if it beats the standing one, and says
    /// whether it did. Strictly greater, so closing a run that only repeated
    /// an earned rung never reads as fresh progress.
    @discardableResult
    static func record(_ level: Int, for key: String) -> Bool {
        guard level > best(for: key) else { return false }
        UserDefaults.standard.set(level, forKey: prefix + key)
        return true
    }

    #if DEBUG
    /// UI-test hook: drop a variant's rung so a locked ladder can be driven.
    static func clear(_ key: String) {
        UserDefaults.standard.removeObject(forKey: prefix + key)
    }
    #endif
}
