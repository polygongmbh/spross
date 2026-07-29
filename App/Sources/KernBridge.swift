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
    var sittingCards: Int { Int(sittingCount) }
    /// Active cards not settled yet — the fresh half of the Fortschritt split.
    var freshCards: Int { max(0, activeCards - sittingCards) }
}

extension AreaStatistics {
    var totalCards: Int { Int(total) }
    var activeCards: Int { Int(active) }
    var sittingCards: Int { Int(sitting) }
    var lockedPhrases: Int { Int(phrasesLocked) }
    var unlockedPhrases: Int { Int(phrasesUnlocked) }
}

extension DayStats {
    var reviewCount: Int { Int(reviews) }
}

// MARK: - Config

extension BoxConfig {
    /// Product calibration (contract §4/§5) — Kotlin default arguments don't
    /// cross the ObjC boundary, so the values are restated once, here.
    static func product() -> BoxConfig {
        BoxConfig(maxUnsettled: 20,
                  sessionCap: 30,
                  dueSoftCap: 30,
                  desiredRetention: 0.8,
                  maximumIntervalDays: 365,
                  sittingStability: 2.0,
                  learningStepsSeconds: [KotlinLong(longLong: 60), KotlinLong(longLong: 600)],
                  relearningStepsSeconds: [KotlinLong(longLong: 600)])
    }

    func with(maxUnsettled: Int) -> BoxConfig {
        doCopy(maxUnsettled: Int32(maxUnsettled),
               sessionCap: sessionCap,
               dueSoftCap: dueSoftCap,
               desiredRetention: desiredRetention,
               maximumIntervalDays: maximumIntervalDays,
               sittingStability: sittingStability,
               learningStepsSeconds: learningStepsSeconds,
               relearningStepsSeconds: relearningStepsSeconds)
    }
}

extension BoxState {
    func with(config: BoxConfig) -> BoxState {
        doCopy(config: config, cards: cards, joinStamp: joinStamp,
               scheduling: scheduling, enqueued: enqueued,
               newIntroduced: newIntroduced, dailyStats: dailyStats)
    }
}
