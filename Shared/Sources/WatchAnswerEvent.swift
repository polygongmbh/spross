import Foundation
import DuoKern

/// One watch answer, sent watch → phone via `transferUserInfo` (queued,
/// guaranteed delivery, possibly duplicated — hence the UUID for dedup).
/// The phone applies events ON RECEIPT in date order with `now:` = the
/// event's date, so FSRS elapsed time stays honest.
struct WatchAnswerEvent: Sendable, Equatable {
    var id: UUID
    var cardID: String
    var rating: Rating
    var date: Date

    init(id: UUID = UUID(), cardID: String, rating: Rating, date: Date) {
        self.id = id
        self.cardID = cardID
        self.rating = rating
        self.date = date
    }

    // MARK: - transferUserInfo payload (property-list types only)

    enum Key {
        static let events = "answerEvents"
        static let id = "id"
        static let cardID = "cardID"
        static let rating = "rating"
        static let date = "date"
    }

    var userInfoEntry: [String: Any] {
        [Key.id: id.uuidString,
         Key.cardID: cardID,
         Key.rating: rating.rawValue,
         Key.date: date]
    }

    static func userInfo(events: [WatchAnswerEvent]) -> [String: Any] {
        [Key.events: events.map(\.userInfoEntry)]
    }

    /// Decode a `transferUserInfo` payload; malformed entries are dropped.
    static func decode(userInfo: [String: Any]) -> [WatchAnswerEvent] {
        guard let entries = userInfo[Key.events] as? [[String: Any]] else { return [] }
        return entries.compactMap { entry in
            guard let idString = entry[Key.id] as? String,
                  let id = UUID(uuidString: idString),
                  let cardID = entry[Key.cardID] as? String,
                  let raw = entry[Key.rating] as? Int,
                  let rating = Rating(rawValue: raw),
                  let date = entry[Key.date] as? Date else { return nil }
            return WatchAnswerEvent(id: id, cardID: cardID, rating: rating, date: date)
        }
    }
}

/// Keys shared by both WCSession sides.
enum WatchSyncKey {
    /// `updateApplicationContext` / `transferFile` metadata key holding the
    /// JSON-encoded `WatchSnapshot`.
    static let snapshot = "snapshot"
}
