import Foundation

/// Imports `Card`s for one language pair directly from the content catalog
/// (`data/catalog/`, see docs/content-format.md). Reconstructs exactly what the
/// legacy `SeedImporter` produced from the fused `vocab-de-<lang>.json` seeds —
/// same cards, same ids, same order — so scheduling history survives untouched.
public enum CatalogImporter {

    /// Builds every card for `pair` from the catalog rooted at `directory`.
    ///
    /// Reconstruction (mirrors `tools/catalog.py emit` + `SeedImporter`):
    /// iterate areas in `areas.json` order; per area emit that area's concepts
    /// in `concepts.json` order (nouns then verbs, already interleaved), then
    /// that pair's phrases in file order. `seedIndex` is the global position.
    public static func importCatalog(directory: URL, pair: LanguagePair) throws -> [Card] {
        let lang = targetLang(for: pair)
        let concepts: [CatalogConcept] = try load(directory, "concepts.json")
        let areas: [CatalogArea] = try load(directory, "areas.json")
        let deReal: [String: CatalogRealization] =
            try load(directory, "realizations/de.json")
        let targetReal: [String: CatalogRealization] =
            try load(directory, "realizations/\(lang).json")
        let phrases: [CatalogPhrase] = try load(directory, "phrases/de-\(lang).json")

        var cards: [Card] = []
        for area in areas {
            var areaCards: [Card] = []

            for concept in concepts where concept.area == area.area {
                guard let de = deReal[concept.id] else {
                    throw CatalogImportError.missingRealization(concept: concept.id, lang: "de")
                }
                guard let tr = targetReal[concept.id] else {
                    throw CatalogImportError.missingRealization(concept: concept.id, lang: lang)
                }
                let german = de.text
                let translation = joined(tr.text, tr.variants)
                switch concept.kind {
                case .noun:
                    areaCards.append(Card(
                        id: SeedImporter.cardID(pair: pair.rawValue, area: area.area,
                                                kind: .noun, german: german),
                        kind: .noun, pair: pair, area: area.area,
                        german: german, article: de.grammar?.gender,
                        plural: SeedImporter.strippedPlural(de.grammar?.plural),
                        emoji: concept.emoji, translation: translation, note: tr.notes.de
                    ))
                case .verb:
                    areaCards.append(Card(
                        id: SeedImporter.cardID(pair: pair.rawValue, area: area.area,
                                                kind: .verb, german: german),
                        kind: .verb, pair: pair, area: area.area,
                        german: german, translation: translation, note: tr.notes.de
                    ))
                case .phrase:
                    continue // phrases never appear in concepts.json
                }
            }

            for phrase in phrases where phrase.area == area.area {
                let translation = joined(phrase.target, phrase.targetVariants)
                let componentIDs = PhraseLinker.components(forPhrase: phrase.de, in: areaCards)
                areaCards.append(Card(
                    id: SeedImporter.cardID(pair: pair.rawValue, area: area.area,
                                            kind: .phrase, german: phrase.de),
                    kind: .phrase, pair: pair, area: area.area,
                    german: phrase.de, translation: translation, note: phrase.notes.de,
                    componentIDs: componentIDs
                ))
            }

            cards.append(contentsOf: areaCards)
        }
        for i in cards.indices {
            cards[i].seedIndex = i
        }
        return cards
    }

    /// The target-language key for a pair's catalog files ("sw" / "uk").
    private static func targetLang(for pair: LanguagePair) -> String {
        switch pair {
        case .deSw: return "sw"
        case .deUk: return "uk"
        }
    }

    /// Legacy "/"-encoding of a canonical form plus its accepted variants —
    /// `Card.translation` keeps this contract (AnswerNormalizer splits on "/").
    private static func joined(_ text: String, _ variants: [String]) -> String {
        variants.isEmpty ? text : ([text] + variants).joined(separator: " / ")
    }

    private static func load<T: Decodable>(_ directory: URL, _ relativePath: String) throws -> T {
        let url = directory.appendingPathComponent(relativePath)
        return try JSONDecoder().decode(T.self, from: try Data(contentsOf: url))
    }
}
