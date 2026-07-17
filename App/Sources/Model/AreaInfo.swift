import Foundation
import DuoKern

/// Display metadata for the seed's area keys. The seed JSON carries localized
/// area titles, but the importer keeps only the key — this maps keys to warm
/// German labels plus an emoji illustration. Unknown keys degrade gracefully.
struct AreaInfo {
    let name: String
    let emoji: String

    static func info(for key: String) -> AreaInfo {
        infos[key] ?? AreaInfo(name: key.capitalized, emoji: "📦")
    }

    private static let infos: [String: AreaInfo] = [
        "kitchen": AreaInfo(name: "Küche", emoji: "🍳"),
        "living": AreaInfo(name: "Wohnzimmer", emoji: "🛋️"),
        "bath": AreaInfo(name: "Bad", emoji: "🛁"),
        "desk": AreaInfo(name: "Schreibtisch", emoji: "✏️"),
        "bedroom": AreaInfo(name: "Schlafzimmer", emoji: "🛏️"),
        "hall": AreaInfo(name: "Flur", emoji: "🚪"),
        "outside": AreaInfo(name: "Draußen", emoji: "🌳"),
        "school": AreaInfo(name: "Schule", emoji: "🎒"),
    ]
}

extension LanguagePair {
    /// Name of the base language (the pair's non-target side; German for every
    /// current pair, switched here so future pairs can differ).
    var baseName: String {
        switch self {
        case .deSw, .deUk: return "Deutsch"
        }
    }

    /// Name of the target language.
    var targetName: String {
        switch self {
        case .deSw: return "Swahili"
        case .deUk: return "Ukrainisch"
        }
    }

    var flag: String {
        switch self {
        case .deSw: return "🇹🇿"
        case .deUk: return "🇺🇦"
        }
    }

    /// Short code of the target language for compact pickers ("SW", "UK").
    var targetShort: String {
        switch self {
        case .deSw: return "SW"
        case .deUk: return "UK"
        }
    }
}

extension Card {
    /// Emoji illustration with warm per-kind fallbacks (verbs and phrases
    /// have no emoji in the seed).
    var displayEmoji: String {
        if let emoji, !emoji.isEmpty { return emoji }
        switch kind {
        case .noun: return "🧩"
        case .verb: return "🏃"
        case .phrase: return "💬"
        }
    }

    /// "der Kühlschrank" — headword with its article where present.
    var germanWithArticle: String {
        if let article { return "\(article) \(german)" }
        return german
    }
}
