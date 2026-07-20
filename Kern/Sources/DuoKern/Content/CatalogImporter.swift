import Foundation

/// Imports `Card`s for one language pair directly from the content catalog
/// (`data/catalog/`, see docs/content-format.md) — the single live import path.
public enum CatalogImporter {

    /// Builds every card for `pair` from the catalog rooted at `directory`.
    ///
    /// Order (mirrors `tools/catalog.py emit`): iterate areas in `areas.json`
    /// order; per area emit that area's concepts in `concepts.json` order (nouns
    /// then verbs, already interleaved), then that pair's phrases in file order.
    /// `seedIndex` is the global position.
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
                        id: cardID(pair: pair.rawValue, area: area.area,
                                   kind: .noun, german: german),
                        kind: .noun, pair: pair, area: area.area,
                        german: german, article: de.grammar?.gender,
                        plural: strippedPlural(de.grammar?.plural),
                        emoji: concept.emoji, translation: translation, note: tr.notes.de
                    ))
                case .verb:
                    areaCards.append(Card(
                        id: cardID(pair: pair.rawValue, area: area.area,
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
                    id: cardID(pair: pair.rawValue, area: area.area,
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

    // MARK: - Card construction helpers

    /// Deterministic, stable card id: lowercase slug of "pair/area/kind/germanHeadword".
    static func cardID(pair: String, area: String, kind: CardKind, german: String) -> String {
        Slug.slugify("\(pair)/\(area)/\(kind.rawValue)/\(german)")
    }

    /// Strips the "Pl. " prefix where present ("Pl. die Kühlschränke" → "die
    /// Kühlschränke"); other forms ("nur Singular", "♀ die Lehrerin") pass through raw.
    static func strippedPlural(_ raw: String?) -> String? {
        guard let raw else { return nil }
        if raw.hasPrefix("Pl. ") {
            return String(raw.dropFirst(4))
        }
        return raw
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
}
