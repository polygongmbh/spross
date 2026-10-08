import Foundation
import SprossKern

// MARK: - TrainerProgress
//
// The highest Sprosse a drill has ever reached, per exercise and language —
// and, for the atlas, the calendar and the two scrambles, the Sprossen a run
// has CLEARED, as kern's bitmask. The first is the one source the unlock
// ladder reads; the second is where the next run opens. Nothing a learner has
// earned is tracked a second time.
//
// A shell over kern's rules and nothing more: WHERE a Sprosse is filed is
// `NumbersMode.progressKey` (+ this prefix), and WHICH Sprossen a closed run may
// book is `NumbersRun.close`, which already filters to the ones that strictly
// beat what was standing. This side only reads and writes.
//
// UserDefaults rather than the box document, for the same reason
// TrainerRecords lives there (docs/design.md § Persistence): a drill run touches
// no card and no schedule, so it is not box state — losing a Sprosse costs a
// climb, where anything in the box costs learning history.

enum TrainerProgress {
    private static var prefix: String { NumbersMode.companion.PROGRESS_PREFIX }

    /// The best Sprosse booked for `key`, or 0 where the exercise was never run.
    static func best(for key: String) -> Int {
        UserDefaults.standard.integer(forKey: prefix + key)
    }

    /// What the store holds now for the keys a closing run could book —
    /// kern compares its high-waters against exactly this.
    static func standing(_ keys: [String]) -> [String: KotlinInt] {
        Dictionary(keys.map { ($0, KotlinInt(int: Int32(best(for: $0)))) },
                   uniquingKeysWith: { first, _ in first })
    }

    /// Everything a closed run files (`DrillBookings`): each store keeps the
    /// higher figure, or ORs the mask in, so a re-closed run claims nothing twice.
    static func book(_ bookings: DrillBookings) {
        for (key, sprosse) in bookings.sprossen { record(Int(truncating: sprosse), for: key) }
        for (key, sprossen) in bookings.cleared { bookCleared(sprossen, for: key) }
        for (key, figure) in bookings.records { TrainerRecords.record(Int(truncating: figure), for: key) }
        for (key, answers) in bookings.answers { TrainerRecords.recordAnswers(Int(truncating: answers), for: key) }
        if let lastRun = bookings.lastRun { stampRun(lastRun) }
    }

    /// Books `sprosse` as the new best if it beats the standing one, and says
    /// whether it did.
    @discardableResult
    static func record(_ sprosse: Int, for key: String) -> Bool {
        guard sprosse > best(for: key) else { return false }
        UserDefaults.standard.set(sprosse, forKey: prefix + key)
        return true
    }

    // MARK: - Cleared Sprossen

    private static var clearedPrefix: String { NumbersMode.companion.CLEARED_PREFIX }

    /// The Sprossen every run under `key` has cleared — answered out, or climbed
    /// off, before its first slip, which the store files as one thing.
    static func cleared(for key: String) -> Set<Int> {
        Set(held(for: key).map { Int(truncating: $0) })
    }

    /// The same mask in the shape kern takes it — a run config's `cleared`, a
    /// ladder's `entrySprosse`.
    static func held(for key: String) -> Set<KotlinInt> {
        let mask = Int32(truncatingIfNeeded: UserDefaults.standard.integer(forKey: clearedPrefix + key))
        return NumbersMode.companion.clearedSprossen(mask: mask)
    }

    /// ORs a closed run's cleared Sprossen into the standing mask. Never
    /// filtered: a Sprosse cleared stays cleared.
    static func bookCleared(_ sprossen: Set<KotlinInt>, for key: String) {
        guard !sprossen.isEmpty else { return }
        let mask = Int(NumbersMode.companion.clearedMask(sprossen: sprossen))
        let standing = UserDefaults.standard.integer(forKey: clearedPrefix + key)
        UserDefaults.standard.set(standing | mask, forKey: clearedPrefix + key)
    }

    // MARK: - Last run

    /// When a run of the drill under `key` (`DrillSuggestion.lastRunKey`) last
    /// closed answered, as epoch millis; nil where none has.
    static func lastRun(_ key: String) -> Int64? {
        (UserDefaults.standard.object(forKey: DrillSuggestion.shared.LAST_RUN_PREFIX + key) as? NSNumber)?
            .int64Value
    }

    /// Stamps a closed run, which is what the suggestion reads "least recently run" off.
    static func stampRun(_ key: String) {
        UserDefaults.standard.set(Date().epochMillis, forKey: DrillSuggestion.shared.LAST_RUN_PREFIX + key)
    }

    // MARK: - Unlock marks

    /// The rows an overview marks as newly unlocked on this showing — kern's
    /// `DrillUnlockMark` over what the page last showed padlocked — and files
    /// the rows it shows padlocked now, so each unlock is marked once.
    static func unlockMarks(page: String, locked: Set<String>, open: Set<String>) -> Set<String> {
        let key = DrillUnlockMark.shared.key(page: page)
        let last = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
        UserDefaults.standard.set(locked.sorted(), forKey: key)
        return DrillUnlockMark.shared.marked(lastLocked: last, open: open)
    }

    #if DEBUG
    /// UI-test hook: drop an exercise's Sprosse so a locked ladder can be driven.
    static func clear(_ key: String) {
        UserDefaults.standard.removeObject(forKey: prefix + key)
    }
    #endif
}
