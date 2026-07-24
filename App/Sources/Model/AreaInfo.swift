import Foundation
import SwiftUI
import SprossKern

/// Display metadata for the catalog's area keys: titles come from the
/// catalog per source language (`AppModel.areaTitle`); the emoji
/// illustration is app-curated here. Unknown keys degrade gracefully.
enum AreaInfo {
    static func emoji(for key: String) -> String {
        emojis[key] ?? "📦"
    }

    private static let emojis: [String: String] = [
        "basics": "👋",
        "kitchen": "🍳",
        "living": "🛋️",
        "bath": "🛁",
        "desk": "✏️",
        "bedroom": "🛏️",
        "hall": "🚪",
        "outside": "🌳",
        "school": "🎒",
        "work": "💼",
        "health": "🩺",
        "admin": "🗂️",
    ]
}

/// Language display names for chrome: localized exonym when a chrome string
/// exists (de/en catalogs), else the language's own name from languages.json.
enum LanguageNames {
    private static let chromeKeys: [String: String] = [
        "de": "Deutsch",
        "en": "Englisch",
        "sw": "Swahili",
        "uk": "Ukrainisch",
    ]

    static func display(_ code: String, locale: Locale, catalog: Catalog?) -> String {
        if let key = chromeKeys[code] {
            return DLChrome.string(key, locale: locale)
        }
        return catalog?.languages[code]?.name ?? code.uppercased()
    }

    /// Language PICKERS use the language's own name (languages.json): a speaker
    /// must recognize their language regardless of chrome locale ("Українська",
    /// never "Ukrainisch"). Sentence chrome keeps `display`.
    static func native(_ code: String, catalog: Catalog?) -> String {
        catalog?.languages[code]?.name ?? code.uppercased()
    }
}

extension Card {
    /// Leading list marker: the seed emoji when present, else a neutral
    /// per-kind category glyph (verbs/phrases carry no seed emoji). Used only
    /// for row rhythm in lists — the card face shows the seed emoji or nothing.
    var displayEmoji: String {
        if let emoji, !emoji.isEmpty { return emoji }
        switch kind {
        case .noun: return "🧩"
        case .verb: return "⚡"
        case .phrase: return "💬"
        }
    }
}

/// Target-side grammar rendering (contract §2): article and plural lines
/// render only for the TARGET realization; suffix plurals dictionary-style,
/// sentinel values via localized chrome strings.
enum CardDisplay {

    /// The realization's article for inline coloring (de `gender` carries the
    /// article itself: "der"/"die"/"das").
    static func article(of realization: Realization) -> String? {
        realization.grammar["gender"]
    }

    /// "der Kühlschrank" — citation form with its article where present.
    static func citation(of realization: Realization) -> String {
        guard let article = article(of: realization) else { return realization.text }
        return "\(article) \(realization.text)"
    }

    /// Dictionary-style plural line: suffix → "Lehrerin, -nen"; "=" → "= Pl.";
    /// "only" → "nur Pl."; full form shown as-is.
    static func plural(of realization: Realization, locale: Locale) -> String? {
        guard let raw = realization.grammar["plural"], !raw.isEmpty else { return nil }
        switch raw {
        case "=": return DLChrome.string("= Pl.", locale: locale)
        case "only": return DLChrome.string("nur Pl.", locale: locale)
        default:
            return raw.hasPrefix("-") ? "\(realization.text), \(raw)" : raw
        }
    }

    /// "auch: Amt / Verwaltung" — the realization's remaining family beyond
    /// `shown`, for reveal display. Variants stay silent (grading only).
    static func alternates(of realization: Realization, shown: String,
                           locale: Locale) -> String? {
        let family = ([realization.text] + realization.synonyms).filter { $0 != shown }
        guard !family.isEmpty else { return nil }
        return String(format: DLChrome.string("auch: %@", locale: locale),
                      family.joined(separator: " / "))
    }
}
