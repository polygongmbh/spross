import Foundation

/// Imports `Card`s from raw seed vocabulary JSON (see `content/vocab-*.json`).
public enum SeedImporter {

    /// Decodes one seed JSON file into cards, with deterministic IDs and
    /// same-area phrase→word component linking. Same JSON in → same IDs out,
    /// always (see `Slug.slugify`).
    public static func importSeed(json: Data) throws -> [Card] {
        let file = try JSONDecoder().decode(SeedFile.self, from: json)

        guard let pair = LanguagePair(rawValue: file.pair) else {
            throw SeedImportError.invalidPair(file.pair)
        }

        var cards: [Card] = []
        for area in file.areas {
            var areaCards: [Card] = []

            for noun in area.nouns {
                guard let translation = noun.translation else {
                    throw SeedImportError.missingTranslation(area: area.area, kind: "noun", german: noun.de)
                }
                areaCards.append(Card(
                    id: cardID(pair: file.pair, area: area.area, kind: .noun, german: noun.de),
                    kind: .noun, pair: pair, area: area.area,
                    german: noun.de, article: noun.article, plural: strippedPlural(noun.plural),
                    emoji: noun.emoji, translation: translation, note: noun.note
                ))
            }

            for verb in area.verbs {
                guard let translation = verb.translation else {
                    throw SeedImportError.missingTranslation(area: area.area, kind: "verb", german: verb.de)
                }
                areaCards.append(Card(
                    id: cardID(pair: file.pair, area: area.area, kind: .verb, german: verb.de),
                    kind: .verb, pair: pair, area: area.area,
                    german: verb.de, translation: translation, note: verb.note
                ))
            }

            for phrase in area.phrases {
                guard let translation = phrase.translation else {
                    throw SeedImportError.missingTranslation(area: area.area, kind: "phrase", german: phrase.de)
                }
                let componentIDs = PhraseLinker.components(forPhrase: phrase.de, in: areaCards)
                areaCards.append(Card(
                    id: cardID(pair: file.pair, area: area.area, kind: .phrase, german: phrase.de),
                    kind: .phrase, pair: pair, area: area.area,
                    german: phrase.de, translation: translation, note: phrase.note,
                    componentIDs: componentIDs
                ))
            }

            cards.append(contentsOf: areaCards)
        }
        // seedIndex = position in the curated file (areas in file order,
        // nouns → verbs → phrases within an area); drives introduction order.
        for i in cards.indices {
            cards[i].seedIndex = i
        }
        return cards
    }

    /// Diagnostics for the phrase→word linker: how many phrase cards linked at
    /// least one component, and the average number of components per phrase.
    public static func linkReport(cards: [Card]) -> (phrases: Int, linked: Int, avgComponents: Double) {
        let phraseCards = cards.filter { $0.kind == .phrase }
        let linked = phraseCards.filter { !$0.componentIDs.isEmpty }.count
        let totalComponents = phraseCards.reduce(0) { $0 + $1.componentIDs.count }
        let avg = phraseCards.isEmpty ? 0 : Double(totalComponents) / Double(phraseCards.count)
        return (phrases: phraseCards.count, linked: linked, avgComponents: avg)
    }

    /// Deterministic, stable card id: lowercase slug of "pair/area/kind/germanHeadword".
    static func cardID(pair: String, area: String, kind: CardKind, german: String) -> String {
        Slug.slugify("\(pair)/\(area)/\(kind.rawValue)/\(german)")
    }

    /// Strips the "Pl. " prefix where present ("Pl. die Kühlschränke" → "die
    /// Kühlschränke"); other forms ("nur Singular", "♀ die Lehrerin") pass through raw.
    /// Shared with `CatalogImporter` so both reconstruct plurals identically.
    static func strippedPlural(_ raw: String?) -> String? {
        guard let raw else { return nil }
        if raw.hasPrefix("Pl. ") {
            return String(raw.dropFirst(4))
        }
        return raw
    }
}
