import Foundation
import SprossKern

// MARK: - TrainerProgress
//
// The highest rung a drill has ever reached, per variant and language.
// It is the one source the unlock ladder reads: everything a learner has
// earned is derived from these numbers, never tracked a second time.
//
// A shell over kern's rules and nothing more: WHERE a rung is filed is
// `TrainerMode.progressKey` (+ this prefix), and WHICH rungs a closed run may
// book is `TrainerRun.close`, which already filters to the ones that strictly
// beat what was standing. This side only reads and writes.
//
// UserDefaults rather than the box document, for the same reason
// TrainerRecords lives there (docs/design.md:156-159): a drill run touches
// no card and no schedule, so it is not box state — losing a rung costs a
// climb, where anything in the box costs learning history.

enum TrainerProgress {
    private static var prefix: String { TrainerMode.companion.PROGRESS_PREFIX }

    /// The best rung booked for `key`, or 0 where the variant was never run.
    static func best(for key: String) -> Int {
        UserDefaults.standard.integer(forKey: prefix + key)
    }

    /// What the store holds now for the keys a closing run could book —
    /// kern compares its high-waters against exactly this.
    static func standing(_ keys: [String]) -> [String: KotlinInt] {
        Dictionary(keys.map { ($0, KotlinInt(int: Int32(best(for: $0)))) },
                   uniquingKeysWith: { first, _ in first })
    }

    /// The rungs a closed run earned (`TrainerClose.progressBookings`). The
    /// strictly-greater guard is kept as a belt: kern already filtered, and a
    /// re-closed run must never claim a rung twice.
    static func book(_ bookings: [String: KotlinInt]) {
        for (key, level) in bookings { record(Int(truncating: level), for: key) }
    }

    /// Books `level` as the new best if it beats the standing one, and says
    /// whether it did.
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
