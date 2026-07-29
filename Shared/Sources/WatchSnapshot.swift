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
        /// Phone-ranked wrong options for THIS entry's role, already on the
        /// side the question asks for (kern `MultipleChoice`). Absent in
        /// pre-v3 snapshots and empty for a box with nothing else to offer.
        var distractors: [String]?
        /// This entry's own option, when the form it is OFFERED in differs from
        /// the form it is taught in — a bound stem without its dash, a verb
        /// without its citation prefix (kern `MultipleChoice.optionForm`).
        /// Absent for every other card, and for the reveal, which teaches.
        var optionForm: String?

        var id: String { cardId }

        var isRecognize: Bool { nextRole == "recognize" }
    }

    var schemaVersion: Int
    /// Epoch milliseconds of the build.
    var generated: Int64
    var entries: [Entry]
    /// TARGET language the mirrored box belongs to. Kern's builder JSON is
    /// profile-agnostic; the phone stamps this before pushing.
    var target: String = ""
    /// Card ids the watch already answered against THIS snapshot (queued as
    /// events, removed from the local due list). Absent in phone-built JSON.
    var answeredCardIDs: [String] = []

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, generated, entries, target, answeredCardIDs
    }

    init(schemaVersion: Int, generated: Int64, entries: [Entry],
         target: String = "", answeredCardIDs: [String] = []) {
        self.schemaVersion = schemaVersion
        self.generated = generated
        self.entries = entries
        self.target = target
        self.answeredCardIDs = answeredCardIDs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decode(Int.self, forKey: .schemaVersion)
        generated = try container.decode(Int64.self, forKey: .generated)
        entries = try container.decode([Entry].self, forKey: .entries)
        target = try container.decodeIfPresent(String.self, forKey: .target) ?? ""
        answeredCardIDs = try container.decodeIfPresent([String].self,
                                                        forKey: .answeredCardIDs) ?? []
    }

    // MARK: - Queries (watch side)

    func entry(id: String) -> Entry? {
        entries.first { $0.cardId == id }
    }

    /// Due entries (`due <= now`), phone-ranked order, minus locally answered.
    func dueEntries(now: Date) -> [Entry] {
        let answered = Set(answeredCardIDs)
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return entries.filter { $0.due <= nowMillis && !answered.contains($0.cardId) }
    }

    /// Not-yet-due entries, soonest-due first, minus locally answered — the
    /// session continues into these once the due list drains (review-ahead).
    func reviewAheadEntries(now: Date) -> [Entry] {
        let answered = Set(answeredCardIDs)
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return entries
            .filter { $0.due > nowMillis && !answered.contains($0.cardId) }
            .sorted { $0.due < $1.due }
    }

    /// Entries due by tomorrow evening (mirrors the phone's tomorrow count).
    func tomorrowDueCount(now: Date, calendar: Calendar) -> Int {
        guard let end = calendar.date(byAdding: .day, value: 2,
                                      to: calendar.startOfDay(for: now)) else { return 0 }
        return dueEntries(now: end).count
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
