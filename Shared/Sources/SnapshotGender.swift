import Foundation

/// The gender a snapshot entry's article marks, resolved phone-side against the
/// target language (kern `SnapshotSupport.kt`, `wireGender`) — so fr `le` and it `le`
/// arrive as the two genders they are. The decode-only surfaces tint from this and
/// never read a gender off the article word.
enum SnapshotGender: String, Codable, Sendable {
    case masculine, feminine, neuter
}
