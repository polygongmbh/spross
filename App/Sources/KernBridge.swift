import Foundation
import SprossKern

// Swift-side conveniences over the SprossKern (Kotlin) API. The engine
// boundary speaks epochMillis + tzId (kern/README.md §7); everything Date-
// or Int-shaped is bridged HERE, never at call sites.

extension Date {
    var epochMillis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }

    init(epochMillis: Int64) {
        self.init(timeIntervalSince1970: Double(epochMillis) / 1000)
    }
}

/// Engine boundary time zone: device-current per call (v1 parity).
func currentTzId() -> String { TimeZone.current.identifier }

/// Kern day keys are ISO `yyyy-MM-dd` regardless of the device calendar —
/// this must format identically for dailyStats lookups.
func isoDayKey(for date: Date, timeZone: TimeZone = .current) -> String {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let parts = calendar.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
}

extension KotlinInstant {
    var date: Date { Date(epochMillis: toEpochMilliseconds()) }
}

// MARK: - SwiftUI conformances

extension Card: @retroactive Identifiable {}

extension AvailableTarget: @retroactive Identifiable {
    public var id: String { code }
}

extension AudioCredit: @retroactive Identifiable {
    /// Credits group per (language, author, licence) — BY and BY-SA by one
    /// author are two rows, so the licence belongs in the identity.
    public var id: String { "\(language)|\(author)|\(licence)" }
}

extension AlphabetEntry: @retroactive Identifiable {
    /// `ref` is the authored id where a file declares one, else the glyph —
    /// the only thing that tells German's three `ch` rows apart.
    public var id: String { ref }
}

// MARK: - Rating

extension Rating {
    /// Wire value (watch answer events carry the raw 1–4).
    init?(value: Int) {
        switch value {
        case 1: self = .again
        case 2: self = .hard
        case 3: self = .good
        case 4: self = .easy
        default: return nil
        }
    }
}

// MARK: - Int32 bridges (Kotlin Int surfaces as Int32)

extension BoxStatistics {
    var activeCards: Int { Int(activeCount) }
    var dueCards: Int { Int(dueCount) }
    var suspendedCards: Int { Int(suspendedCount) }
    var streakDays: Int { Int(streak) }
    var longestStreakDays: Int { Int(longestStreak) }
    var settledCards: Int { Int(settledCount) }
    /// Active cards not settled yet — the fresh half of the Fortschritt split.
    var freshCards: Int { max(0, activeCards - settledCards) }
}

extension AreaStatistics {
    var totalCards: Int { Int(total) }
    var activeCards: Int { Int(active) }
    var settledCards: Int { Int(settled) }
    var lockedPhrases: Int { Int(phrasesLocked) }
    var unlockedPhrases: Int { Int(phrasesUnlocked) }
}

extension DayStats {
    var reviewCount: Int { Int(reviews) }
}

/// Levels are `Int` everywhere in the drill UI; the ladder is Kotlin `Int`.
/// Bridged HERE so no view ever writes `Int32(…)` around a rung number.
extension LetterDrill {
    func ceiling(dictation: Bool) -> Int { Int(maxLevel(dictationAvailable: dictation)) }

    func entryLevel(settled: Int) -> Int { Int(entryLevel(settledCards: Int32(settled))) }

    func winsToAdvance(settled: Int) -> Int { Int(winsToAdvance(settledCards: Int32(settled))) }

    func stage(level: Int) -> LetterStage { stageFor(level: Int32(level)) }

    func step(level: Int, winsAtLevel: Int, correct: Bool, clean: Bool,
              maxLevel: Int, winsRequired: Int) -> LetterDrill.LetterDrillProgress {
        advance(level: Int32(level), winsAtLevel: Int32(winsAtLevel),
                correct: correct, clean: clean,
                maxLevel: Int32(maxLevel), winsRequired: Int32(winsRequired))
    }
}

extension LetterDrill.LetterDrillProgress {
    var nextLevel: Int { Int(level) }
    var wins: Int { Int(winsAtLevel) }
}

// MARK: - Config

extension BoxConfig {
    /// Product calibration (contract §4/§5) — Kotlin default arguments don't
    /// cross the ObjC boundary, so the values are restated once, here.
    static func product() -> BoxConfig {
        BoxConfig(sessionCap: 25,
                  dueSoftCap: 30,
                  desiredRetention: 0.8,
                  maximumIntervalDays: 365,
                  settledStability: 2.0,
                  consolidatedStability: 6.0,
                  learningStepsSeconds: [KotlinLong(longLong: 120)],
                  relearningStepsSeconds: [KotlinLong(longLong: 600)])
    }

}

extension BoxState {
    func with(config: BoxConfig) -> BoxState {
        doCopy(config: config, cards: cards, joinStamp: joinStamp,
               scheduling: scheduling, enqueued: enqueued,
               newIntroduced: newIntroduced, settledCrossed: settledCrossed,
               dailyStats: dailyStats)
    }

    /// Calibration belongs to the app build, not to the stored document: steps,
    /// retention and caps are decisions this version makes, and a box written
    /// months ago would otherwise keep answering to the numbers that shipped the
    /// day it was created. Nothing survives the refresh — growth pacing is the
    /// engine's opinion, not a figure the learner tunes.
    func withProductCalibration() -> BoxState {
        with(config: .product())
    }
}
