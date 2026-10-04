import Foundation

/// Kern's `StreakHealth` (`box/Statistics.kt`) for the widget, which links no Kotlin:
/// the same cases in Swift's casing, decoded from the case name kern serializes.
/// Which day puts a run in which case is kern's ruling (`kern/docs/reports.md`).
enum StreakHealth: String, Decodable {
    case earned, bridgeable, ending, noRun

    init(from decoder: Decoder) throws {
        let name = try decoder.singleValueContainer().decode(String.self)
        guard let health = Self(rawValue: name.prefix(1).lowercased() + name.dropFirst()) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath,
                                                    debugDescription: "Unknown StreakHealth \(name)"))
        }
        self = health
    }
}
