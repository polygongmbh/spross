import Foundation
import SprossKern
import SwiftUI

// Swift-side conveniences over the SprossKern (Kotlin) API. The engine
// boundary speaks epochMillis + tzId (kern/docs/build.md); everything Date-
// or Int-shaped is bridged HERE, never at call sites.

extension Date {
    var epochMillis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }

    init(epochMillis: Int64) {
        self.init(timeIntervalSince1970: Double(epochMillis) / 1000)
    }
}

/// Engine boundary time zone: device-current per call.
func currentTzId() -> String { TimeZone.current.identifier }

extension KotlinInstant {
    var date: Date { Date(epochMillis: toEpochMilliseconds()) }
}

// MARK: - Crossing threads

// Kern state is immutable: the engine returns a new `BoxState` rather than
// editing one, and Kotlin/Native shares objects between threads freely. Saying
// so lets the box be encoded off the main actor while the model still holds it
// — encoding walks every schedule and every review ever logged, and that is not
// work a tap should wait on (`App/Sources/Store/BoxStore.swift`).
extension BoxState: @retroactive @unchecked Sendable {}
// The store and one language's box in it: values kern hands over, read off the actor
// that holds them and opened on a background task.
extension StoredBoxes: @retroactive @unchecked Sendable {}
extension StoredBox: @retroactive @unchecked Sendable {}
extension OpenedBox: @retroactive @unchecked Sendable {}
// The catalog is parsed once and never written again; the pool report is a
// value kern hands back. Both cross to a background sweep and back.
extension Catalog: @retroactive @unchecked Sendable {}
extension ListeningPool.Report: @retroactive @unchecked Sendable {}

// MARK: - SwiftUI conformances

extension Card: @retroactive Identifiable {}

extension AvailableTarget: @retroactive Identifiable {
    public var id: String { code }
}

extension AudioCredit: @retroactive Identifiable {
    /// Credits group per (language, author, license) — BY and BY-SA by one
    /// author are two rows, so the license belongs in the identity.
    public var id: String { "\(language)|\(author)|\(license)" }
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
    var streakDays: Int { Int(streak) }
    var allSettledCards: Int { Int(allSettledCount) }
    var allGrowingCards: Int { Int(allGrowingCount) }
}

extension AreaStatistics {
    var activeCards: Int { Int(active) }
    var allSettledCards: Int { Int(allSettled) }
    var lockedPhrases: Int { Int(phrasesLocked) }
}

/// Kotlin's own `Random`, which every draw in a run is spent out of. Named for
/// what it is at the call site: the drills never seed one of their own.
/// A DEBUG launch may pass `-uitest-seed N` (N > 0) to pin every draw of the run.
var drillRandom: KotlinRandom {
    #if DEBUG
    let seed = UserDefaults.standard.integer(forKey: "uitest-seed")
    // why: a fresh seeded instance per access — SwiftUI may re-run a view's init,
    // so every draw is a function of (seed, intents so far), not of render count.
    if seed > 0 { return Numbers.shared.seededRandom(seed: Int32(seed)) }
    #endif
    return KotlinRandom.companion
}

/// Sprossen are `Int` everywhere in the drill UI; the ladder is Kotlin `Int`.
/// Bridged HERE so no view ever writes `Int32(…)` around a Sprosse number.
extension CountryDrill {
    var ceiling: Int { Int(MAX_SPROSSE) }
}

/// Same bridge for the dates drill. Its ladder has no constant ceiling: how
/// tall it is depends on what the pair's content carries and which way round
/// the run asks, so every question here takes both.
extension DateDrill {
    func ceiling(content: DateDrillContent, reverse: Bool) -> Int {
        Int(maxSprosse(content: content, reverse: reverse))
    }

    func kinds(content: DateDrillContent, sprosse: Int, reverse: Bool) -> [DateTaskKind] {
        kinds(content: content, sprosse: Int32(sprosse), reverse: reverse)
    }
}

/// The pricing both pair drills share, on the drill's own ceiling.
extension PairDrill {
    /// Whether the top Sprosse has EVER been stood on — the whole price of fast
    /// mode, kept in kern so the page never spells the number out itself.
    func fastUnlocked(bestSprosse: Int, top: Int) -> Bool {
        fastUnlocked(bestSprosse: Int32(bestSprosse), top: Int32(top))
    }

    func fastPrice(top: Int) -> Int { Int(fastPrice(top: Int32(top))) }
}

// The product calibration and `withProductCalibration()` are Kern's
// (`model/Config.kt`, `store/Calibration.kt`) — a Swift copy of the table would
// drift from the engine that answers to it.
