import Foundation

/// Compact phone → watch state transfer ("snapshot down, events up"), v6:
/// decode-only mirror of Kern's `WatchSnapshotBuilder` JSON. One entry per
/// CARD with both sides pre-resolved — the watch never joins, never types,
/// and links no Kotlin. The phone is the source of truth; the watch only
/// drains this snapshot's due list and queues answer events back.
struct WatchSnapshot: Codable, Sendable, Equatable {

    /// One drainable card. `nextRole` "produce": prompt `sourceText`
    /// (+ ♀ badge when `femMarker`), reveal the target family. "recognize":
    /// prompt `promptForm` (the rotated target form), reveal `sourceText`.
    /// The picture arrives under the key that names when it may be seen —
    /// `emoji` from frame one, `revealEmoji` only after the answer.
    struct Entry: Codable, Sendable, Equatable, Identifiable {
        var cardId: String
        var sourceText: String
        var targetText: String
        var emoji: String?
        /// The same picture where the policy holds it back until the answer is
        /// out. Never rendered before a tile is tapped — that is the whole
        /// reason it travels under its own key instead of in `emoji`.
        var revealEmoji: String?
        /// The article shown in front of `targetText` — never in front of a
        /// rotated `promptForm`, which may be a word of another gender.
        var article: String?
        /// What the article marks; the tint reads this, never `article`.
        var gender: SnapshotGender?
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

    /// The one version this build reads (kern `WatchSnapshotBuilder.SCHEMA_VERSION`).
    static let currentSchemaVersion = 6

    var schemaVersion: Int
    /// The language the watch's and the complication's chrome is written in,
    /// the one the phone's own chrome follows.
    var chromeLanguage: String
    /// Epoch milliseconds of the build.
    var generated: Int64
    var entries: [Entry]
    /// Card ids the watch already answered against THIS snapshot (queued as
    /// events, removed from the local due list). Absent in phone-built JSON.
    var answeredCardIDs: [String] = []

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, chromeLanguage, generated, entries, answeredCardIDs
    }

    init(schemaVersion: Int, chromeLanguage: String, generated: Int64, entries: [Entry],
         answeredCardIDs: [String] = []) {
        self.schemaVersion = schemaVersion
        self.chromeLanguage = chromeLanguage
        self.generated = generated
        self.entries = entries
        self.answeredCardIDs = answeredCardIDs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decode(Int.self, forKey: .schemaVersion)
        // why: a snapshot of another version — one the watch stored before an update —
        // is refused whole, as the widget refuses its own: the watch shows its
        // waiting face until the phone pushes a fresh one, never half a schema.
        guard schemaVersion == Self.currentSchemaVersion else {
            throw DecodingError.dataCorruptedError(forKey: .schemaVersion, in: container,
                                                   debugDescription: "unsupported schemaVersion")
        }
        chromeLanguage = try container.decode(String.self, forKey: .chromeLanguage)
        generated = try container.decode(Int64.self, forKey: .generated)
        entries = try container.decode([Entry].self, forKey: .entries)
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
