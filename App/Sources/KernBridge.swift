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
    var consolidatedCards: Int { Int(consolidatedCount) }
    var learningCards: Int { Int(learningCount) }
}

extension AreaStatistics {
    var activeCards: Int { Int(active) }
    var consolidatedCards: Int { Int(consolidated) }
    var lockedPhrases: Int { Int(phrasesLocked) }

    /// The area's three buckets and what they are measured against, as the
    /// design system reads them — which card falls in which bucket, and how a
    /// stale total is clamped, are the engine's rulings (`box/Statistics.kt`).
    var progress: AreaProgress {
        AreaProgress(consolidated: Int(consolidated), learning: Int(learning),
                     notIntroduced: Int(notIntroduced), progressTotal: Int(progressTotal))
    }
}

extension DayStats {
    var reviewCount: Int { Int(reviews) }
}

/// Levels are `Int` everywhere in the drill UI; the ladder is Kotlin `Int`.
/// Bridged HERE so no view ever writes `Int32(…)` around a rung number.
extension LetterDrill {
    func ceiling(dictation: Bool) -> Int { Int(maxLevel(dictationAvailable: dictation)) }

    func entryLevel(consolidated: Int) -> Int { Int(entryLevel(consolidatedCards: Int32(consolidated))) }

    func winsToAdvance(consolidated: Int) -> Int { Int(winsToAdvance(consolidatedCards: Int32(consolidated))) }

    func stage(level: Int) -> LetterStage { stageFor(level: Int32(level)) }
}

/// Same bridge for the atlas drill: its ladder is Kotlin `Int`, its ceiling and
/// its rung length are its own, and no view of it writes `Int32(…)`.
extension CountryDrill {
    var ceiling: Int { Int(MAX_LEVEL) }

    func step(level: Int, winsAtLevel: Int, correct: Bool, clean: Bool) -> DrillRamp.RungStep {
        step(level: Int32(level), winsAtLevel: Int32(winsAtLevel), correct: correct, clean: clean)
    }

    func sample(content: CountryDrillContent, level: Int, reverse: Bool,
                avoidId: String?, rng: KotlinRandom) -> CountryDrillTask {
        sample(content: content, level: Int32(level), reverse: reverse,
               avoidId: avoidId, rng: rng)
    }
}

/// The one rung ramp both drills answer to. How long a rung is stays theirs
/// (`LetterDrill.winsToAdvance` counts a vocabulary, `Trainer.winsToAdvance`
/// reads the Fast modifier); what a rung does with an answer is kern's.
extension DrillRamp {
    func step(level: Int, winsAtLevel: Int, correct: Bool, clean: Bool,
              maxLevel: Int, winsRequired: Int) -> DrillRamp.RungStep {
        step(level: Int32(level), winsAtLevel: Int32(winsAtLevel),
             correct: correct, clean: clean,
             maxLevel: Int32(maxLevel), winsRequired: Int32(winsRequired))
    }
}

extension DrillRamp.RungStep {
    var nextLevel: Int { Int(level) }
    var wins: Int { Int(winsAtLevel) }
}

// MARK: - Kern → Design value types
//
// `App/Sources/Design` is kern-free by design, so every rule it renders arrives
// as one of its own value types. These are the only places the two meet.

extension SessionOutcome {
    /// The bar segment one answer draws. The bucketing is kern's (`AnswerTone`).
    init(_ tone: AnswerTone) {
        switch tone {
        case .right: self = .right
        case .tough: self = .tough
        case .wrong: self = .wrong
        }
    }
}

extension DLGender {
    /// The palette's twin of the box's `Gender` — kern names the gender an
    /// article marks (`model/Article.kt`), the design system names the hue.
    init?(_ gender: Gender?) {
        guard let gender else { return nil }
        switch gender {
        case .masculine: self = .masculine
        case .feminine: self = .feminine
        case .neuter: self = .neuter
        }
    }
}

extension ActivityColumn {
    /// One day of the box's activity window. Earned and bridged days alike are
    /// covered by the run the flame counts — the strip never walks it itself.
    init(_ day: ActivityDay) {
        self.init(day: Date(epochMillis: day.dayStartEpochMillis),
                  reviews: Int(day.reviews),
                  inStreak: day.role != .outside)
    }
}

// The product calibration and `withProductCalibration()` are Kern's
// (`model/Config.kt`, `store/Calibration.kt`) — a Swift copy of the table would
// drift from the engine that answers to it.
