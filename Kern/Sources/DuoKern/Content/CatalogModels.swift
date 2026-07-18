import Foundation

// Wire format for the language-agnostic content catalog (data/catalog/,
// see docs/content-format.md). Kept private to Content — callers only ever see
// the resulting [Card] from CatalogImporter.importCatalog.

/// Errors surfaced while importing the content catalog.
public enum CatalogImportError: Error, Sendable, Equatable {
    /// A concept referenced an area, but the German or target realization for
    /// its id was absent from the corresponding realizations file.
    case missingRealization(concept: String, lang: String)
}

/// One shared word concept (`concepts.json`, ordered — order IS seed order).
struct CatalogConcept: Decodable {
    let id: String
    let kind: CardKind
    let area: String
    let emoji: String?
}

/// One area's key + per-language titles (`areas.json`, ordered — the canonical
/// area/introduction order). Titles are unused for card building.
struct CatalogArea: Decodable {
    let area: String
}

/// Language-specific grammar bag; de carries `gender`/`plural`, other languages
/// carry unrelated keys that card building ignores.
struct CatalogGrammar: Decodable {
    let gender: String?
    let plural: String?
}

/// Explanation-language-keyed notes (today only "de").
struct CatalogNotes: Decodable {
    let de: String?
}

/// One language's realization of a concept (`realizations/<lang>.json`, keyed
/// by concept id).
struct CatalogRealization: Decodable {
    let text: String
    let variants: [String]
    let grammar: CatalogGrammar?
    let notes: CatalogNotes
    /// Rare pair-tuned German (e.g. de-uk "grillen" vs the shared "Fleisch
    /// grillen"), keyed "de-<lang>". Only present on de realizations.
    let pairOverrides: [String: String]?
}

/// One pair-authored phrase entry (`phrases/de-<lang>.json`, ordered per area).
struct CatalogPhrase: Decodable {
    let id: String
    let area: String
    let de: String
    let target: String
    let targetVariants: [String]
    let notes: CatalogNotes
}
