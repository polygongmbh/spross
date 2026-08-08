import Foundation
import SprossKern
import SwiftUI

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

/// Kotlin's own `Random`, which every draw in a run is spent out of. Named for
/// what it is at the call site: the drills never seed one of their own.
var drillRandom: KotlinRandom { KotlinRandom.companion }

// MARK: - Kern → Design value types
//
// `App/Sources/Design` is kern-free by design, so every rule it renders arrives
// as one of its own value types. These are the only places the two meet — bar
// the Design files where the rendered thing IS kern's own value and a copy would
// drift: `AutoAdvance` (the two beat lengths), `NumberReferenceTable` (the primer
// rows) and `SessionCompletionView` (the round's tally parts).

extension SessionOutcome {
    /// The bar segment one answer draws. The bucketing is kern's (`AnswerTone`).
    init(_ tone: AnswerTone) {
        switch tone {
        case .right: self = .right
        case .tough: self = .tough
        case .wrong: self = .wrong
        }
    }

    /// What the three self-grade buttons SAY. The rating it earns is kern's:
    /// the clock behind `SelfGrading` decides whether a Knew came instantly.
    var verdict: SelfGrading.Verdict {
        switch self {
        case .right: return .knew
        case .tough: return .tough
        case .wrong: return .unknown
        }
    }
}

extension DrillRunResult {
    /// The figures a closed run leaves, as the tile above the picks wears them.
    /// Which of them there are is kern's (`DrillRunSummary`); what the run was
    /// CALLED is chrome, so it arrives beside them.
    init(_ summary: DrillRunSummary, title: LocalizedStringKey) {
        self.init(doneCount: Int(summary.done), bestStreak: Int(summary.bestStreak),
                  newRecord: summary.newRecord, title: title)
    }
}

extension AnswerInputView.Feedback {
    /// The field's face for where kern says the answer stands. `almost` is the
    /// only state carrying anything — the form the card owes back.
    init(_ feedback: TurnFeedback) {
        switch onEnum(of: feedback) {
        case .neutral: self = .neutral
        case .correct: self = .correct
        case .almost(let hold): self = .almost(correctForm: hold.correctForm,
                                               reason: .init(hold.reason))
        case .revealed: self = .revealed
        }
    }
}

extension AnswerInputView.AlmostReason {
    init(_ reason: SprossKern.AlmostReason) {
        switch reason {
        case .typo: self = .typo
        case .heard: self = .heard
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
