import Foundation
import SwiftUI
import SprossKern

/// Language display names for chrome: localized exonym when a chrome string
/// exists (de/en catalogs), else the language's own name from languages.json.
enum LanguageNames {
    private static let chromeKeys: [String: String] = [
        "de": "lang.de",
        "en": "lang.en",
        "es": "lang.es",
        "sw": "lang.sw",
        "uk": "lang.uk",
    ]

    static func display(_ code: String, locale: Locale, catalog: Catalog?) -> String {
        if let key = chromeKeys[code] {
            return DLChrome.string(key, locale: locale)
        }
        return catalog?.languages[code]?.name ?? code.uppercased()
    }

    /// Sentence chrome outside pickers ("Alle Lernfortschritte für …"):
    /// the language's own name from languages.json.
    static func native(_ code: String, catalog: Catalog?) -> String {
        catalog?.languages[code]?.name ?? code.uppercased()
    }

    /// Language PICKER rows: "🇺🇦 Українська · Ukrainian" — flag, the language's
    /// own name, and the English exonym. Both, because a flag beside a script
    /// you cannot read is easy to mistake for a neighbouring language; the
    /// exonym is what makes the row identifiable either way. Collapsed to one
    /// name when the two are identical ("🇬🇧 English").
    static func pickerRow(_ code: String, catalog: Catalog?) -> String {
        guard let info = catalog?.languages[code] else { return code.uppercased() }
        guard info.name != info.englishName else { return "\(info.flag) \(info.name)" }
        return "\(info.flag) \(info.name) · \(info.englishName)"
    }

    /// Collapsed form for a dropdown's own label, which has half a row to live
    /// in: flag + English exonym, the shorter of the two names and the one that
    /// stays identifiable to everyone.
    static func pickerLabel(_ code: String, catalog: Catalog?) -> String {
        guard let info = catalog?.languages[code] else { return code.uppercased() }
        return "\(info.flag) \(info.englishName)"
    }
}

/// Surfaces that name a language in their own chrome, and label a typed-answer
/// field with it. One implementation, so two screens can never start naming the
/// same language two different ways.
// why: @MainActor — three of the four conformers read the name out of the
// AppModel's catalog, which is main-actor isolated.
@MainActor
protocol LanguageNaming {
    var locale: Locale { get }
    /// Where a language with no chrome exonym finds its own name.
    var namingCatalog: Catalog? { get }
}

extension LanguageNaming {
    func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: locale, catalog: namingCatalog)
    }

    /// "Auf Suaheli …" — what the answer field asks for. A runtime `%@`, so it
    /// resolves through `DLChrome` rather than the environment locale.
    func answerPlaceholder(_ code: String) -> String {
        String(format: DLChrome.string("session.answer.placeholder %@", locale: locale),
               languageName(code))
    }
}

extension Card {
    /// A form this card lists as a synonym or a variant — the right word, just
    /// not the one that played. Amber, never wrong: the reveal itself teaches
    /// these forms ("auch: …"), so failing one would contradict the card.
    func alsoAccepts(_ input: String) -> Bool {
        let typed = speechKey(form: input)
        return (target.synonyms + target.variants).contains { speechKey(form: $0) == typed }
    }

    /// Leading list marker: the seed emoji when present, else a neutral
    /// per-kind category glyph (verbs/phrases carry no seed emoji). Used only
    /// for row rhythm in lists — the card face shows the seed emoji or nothing.
    var displayEmoji: String {
        if let emoji, !emoji.isEmpty { return emoji }
        switch kind {
        case .noun: return "🧩"
        case .verb: return "⚡"
        case .adjective: return "✨"
        case .phrase: return "💬"
        // Card.emoji is never nil for idioms (Catalog.kt applies the fixed
        // IDIOM_EMOJI at join time), so this branch is defensive/unreachable —
        // still required for switch exhaustiveness. Keep in sync with kern's
        // IDIOM_EMOJI (Card.kt).
        case .idiom: return "🎭"
        }
    }
}

/// Target-side grammar rendering (contract §2): article and plural lines
/// render only for the TARGET realization; suffix plurals dictionary-style,
/// sentinel values via localized chrome strings.
enum CardDisplay {

    /// The realization's authored article (de `gender` carries the article
    /// itself: "der"/"die"/"das").
    private static func article(of realization: Realization) -> String? {
        realization.grammar["gender"]
    }

    /// The article a card face may show in front of `shown`, with the gender it
    /// marks — both rules are the box's (`model/Article.kt`): a rotated synonym
    /// is a different word, so the card's article steps aside rather than
    /// mislabel it, and which article marks which gender is stated there once.
    static func articleLabel(of realization: Realization, shown: String) -> DLArticle? {
        guard let article = shownArticle(article: article(of: realization),
                                         shownForm: shown,
                                         targetText: realization.text)
        else { return nil }
        return DLArticle(article, gender: DLGender(articleGender(article: article)))
    }

    /// "der Kühlschrank" — citation form with its article where present.
    static func citation(of realization: Realization) -> String {
        guard let article = article(of: realization) else { return realization.text }
        return "\(article) \(realization.text)"
    }

    /// Labelled plural line: every real form gets the "Pl." label, with suffixes
    /// resolved against the word ("-nen" → "Pl. Lehrerinnen") rather than shown
    /// dictionary-style; "=" → "= Pl.", "only" → "nur Pl.".
    static func plural(of realization: Realization, locale: Locale) -> String? {
        guard let raw = realization.grammar["plural"], !raw.isEmpty else { return nil }
        switch raw {
        case "=": return DLChrome.string("grammar.plural.equals", locale: locale)
        case "only": return DLChrome.string("grammar.plural.only", locale: locale)
        default:
            let form = raw.hasPrefix("-") ? realization.text + raw.dropFirst() : raw
            return String(format: DLChrome.string("grammar.plural %@", locale: locale), form)
        }
    }

    /// "auch: Amt / Verwaltung" — the realization's remaining family beyond
    /// `shown`, for reveal display. Variants stay silent (grading only).
    static func alternates(of realization: Realization, shown: String,
                           locale: Locale) -> String? {
        let family = ([realization.text] + realization.synonyms).filter { $0 != shown }
        guard !family.isEmpty else { return nil }
        return String(format: DLChrome.string("grammar.also %@", locale: locale),
                      family.joined(separator: " / "))
    }
}
