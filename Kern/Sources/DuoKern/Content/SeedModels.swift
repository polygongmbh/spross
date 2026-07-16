import Foundation

// Wire format for content/vocab-*.json seed files. Kept private to Content —
// callers only ever see the resulting [Card] from SeedImporter.importSeed.

/// Errors surfaced while importing seed vocabulary JSON.
public enum SeedImportError: Error, Sendable, Equatable {
    /// `pair` did not match any known `LanguagePair` raw value (e.g. "de-sw").
    case invalidPair(String)
    /// An entry had neither an "sw" nor a "uk" translation field.
    case missingTranslation(area: String, kind: String, german: String)
}

struct SeedFile: Decodable {
    let pair: String
    let source: String?
    let areas: [SeedArea]
}

struct SeedArea: Decodable {
    let area: String
    let title: SeedTitle
    let nouns: [SeedNoun]
    let verbs: [SeedVerb]
    let phrases: [SeedPhrase]
}

struct SeedTitle: Decodable {
    let de: String
    let sw: String?
    let uk: String?
}

struct SeedNoun: Decodable {
    let emoji: String?
    let article: String?
    let de: String
    let plural: String?
    let sw: String?
    let uk: String?

    /// The sw/uk key is generic across files — a given seed file only ever
    /// populates one of the two, so trying both resolves it.
    var translation: String? { sw ?? uk }
}

struct SeedVerb: Decodable {
    let de: String
    let sw: String?
    let uk: String?

    var translation: String? { sw ?? uk }
}

struct SeedPhrase: Decodable {
    let de: String
    let sw: String?
    let uk: String?
    let note: String?

    var translation: String? { sw ?? uk }
}
