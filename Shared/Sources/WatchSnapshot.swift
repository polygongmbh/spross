import Foundation

/// Compact phone → watch state transfer ("snapshot down, events up"), v2:
/// decode-only mirror of Kern's `WatchSnapshotBuilder` JSON. One entry per
/// CARD with both sides pre-resolved — the watch never joins, never types,
/// and links no Kotlin. The phone is the source of truth; the watch only
/// drains this snapshot's due list and queues answer events back.
struct WatchSnapshot: Codable, Sendable, Equatable {

    /// One drainable card. `nextRole` "produce": prompt `sourceText`
    /// (+ ♀ badge when `femMarker`), reveal the target family. "recognize":
    /// prompt `promptForm` (the rotated target form), reveal `sourceText`.
    /// `emoji` is pre-gated by the phone (emoji policy); `accepted` lists the
    /// full target family for reveal display.
    struct Entry: Codable, Sendable, Equatable, Identifiable {
        var cardId: String
        var sourceText: String
        var targetText: String
        var accepted: [String]
        var emoji: String?
        var articleTint: String?
        var femMarker: Bool
        /// Epoch milliseconds (trivial Swift decoding, no date strategy).
        var due: Int64
        var stability: Double
        var nextRole: String
        var promptForm: String

        var id: String { cardId }
    }

    var schemaVersion: Int
    /// Epoch milliseconds of the build.
    var generated: Int64
    var entries: [Entry]
    /// Card ids the watch already answered against THIS snapshot (queued as
    /// events, removed from the local due list). Absent in phone-built JSON.
    var answeredCardIds: [String] = []

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, generated, entries, answeredCardIds
    }

    init(schemaVersion: Int, generated: Int64, entries: [Entry],
         answeredCardIds: [String] = []) {
        self.schemaVersion = schemaVersion
        self.generated = generated
        self.entries = entries
        self.answeredCardIds = answeredCardIds
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decode(Int.self, forKey: .schemaVersion)
        generated = try container.decode(Int64.self, forKey: .generated)
        entries = try container.decode([Entry].self, forKey: .entries)
        answeredCardIds = try container.decodeIfPresent([String].self,
                                                        forKey: .answeredCardIds) ?? []
    }

    // MARK: - Queries (watch side)

    /// Due entries (`due <= now`), phone-ranked order, minus locally answered.
    func dueEntries(now: Date) -> [Entry] {
        let answered = Set(answeredCardIds)
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return entries.filter { $0.due <= nowMillis && !answered.contains($0.cardId) }
    }

    /// Attention-worthy entries for the complication: the phone already ranks
    /// due-first, then exposure tiers — just take the head.
    func exposureEntries(limit: Int) -> [Entry] {
        Array(entries.prefix(limit))
    }

    // MARK: - JSON

    static func decode(_ data: Data) throws -> WatchSnapshot {
        try JSONDecoder().decode(WatchSnapshot.self, from: data)
    }

    func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(self)
    }
}
