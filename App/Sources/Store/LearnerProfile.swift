import Foundation

/// Who is learning, as far as the app needs to know: ONE name, for the greeting to use.
/// It belongs to the person, not to a language pair — every box on disk is greeted by
/// the same one — so it lives in UserDefaults beside the pair (`AppModel`) rather than
/// in a box document (`BoxStore`).
///
/// Absent and blank are the same state: an empty field stores nothing and reads back as
/// nil, so the greeting falls to its own no-name wording instead of addressing a
/// placeholder. Whitespace is trimmed on the way in, never on the way out of the field.
enum LearnerProfile {
    static let nameKey = "learnerName"

    static var name: String? {
        get {
            let stored = UserDefaults.standard.string(forKey: nameKey)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return (stored?.isEmpty ?? true) ? nil : stored
        }
        set {
            let trimmed = newValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if trimmed.isEmpty {
                UserDefaults.standard.removeObject(forKey: nameKey)
            } else {
                UserDefaults.standard.set(trimmed, forKey: nameKey)
            }
        }
    }
}
