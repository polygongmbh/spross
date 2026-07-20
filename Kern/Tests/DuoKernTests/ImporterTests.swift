import XCTest
import DuoKern

/// Behaviour of the live importer (`CatalogImporter`, reading the content
/// catalog) — everything the app relies on is pinned here.
final class ImporterTests: XCTestCase {

    // MARK: - Fixture loading

    private func catalogDirectory() throws -> URL {
        guard let url = Bundle.module.url(
            forResource: "concepts", withExtension: "json",
            subdirectory: "Fixtures/catalog") else {
            XCTFail("Missing Fixtures/catalog")
            return URL(fileURLWithPath: "/dev/null")
        }
        return url.deletingLastPathComponent()
    }

    private func cards(_ pair: LanguagePair) throws -> [Card] {
        try CatalogImporter.importCatalog(directory: try catalogDirectory(), pair: pair)
    }

    // MARK: - Counts

    func testCardCountsPerPair() throws {
        for pair in LanguagePair.allCases {
            let cards = try cards(pair)
            let nouns = cards.filter { $0.kind == .noun }.count
            let verbs = cards.filter { $0.kind == .verb }.count
            let phrases = cards.filter { $0.kind == .phrase }.count
            // Full catalog incl. basics + amt/arzt/arbeit packs.
            XCTAssertEqual(nouns, 133, "\(pair) noun count")
            XCTAssertEqual(verbs, 105, "\(pair) verb count")
            XCTAssertEqual(phrases, 105, "\(pair) phrase count")
            XCTAssertEqual(cards.count, 343, "\(pair) total count")
        }
    }

    // MARK: - Determinism & uniqueness

    func testDeterministicIDs() throws {
        let first = try cards(.deSw).map(\.id)
        let second = try cards(.deSw).map(\.id)
        XCTAssertEqual(first, second)
    }

    func testNoDuplicateIDs() throws {
        for pair in LanguagePair.allCases {
            let ids = try cards(pair).map(\.id)
            XCTAssertEqual(Set(ids).count, ids.count, "\(pair) has duplicate ids")
        }
    }

    /// The shared German realization drives every pair identically: both
    /// pairs get "grillen" (id derives from the German string). Pinned so the
    /// card id can't silently re-key and orphan its scheduling history.
    func testPinnedSharedRealizationIDs() throws {
        let sw = try cards(.deSw).first { $0.id == "de-sw-outside-verb-grillen" }
        XCTAssertEqual(sw?.german, "grillen")
        let uk = try cards(.deUk).first { $0.id == "de-uk-outside-verb-grillen" }
        XCTAssertEqual(uk?.german, "grillen")
    }

    // MARK: - Field shape

    func testArticlesOnlyOnNouns() throws {
        for card in try cards(.deSw) {
            if card.kind == .noun {
                XCTAssertNotNil(card.article, "noun \(card.german) missing article")
            } else {
                XCTAssertNil(card.article, "\(card.kind) \(card.german) should not have an article")
            }
        }
    }

    // MARK: - Phrase → word linking

    /// Two kitchen phrases ("Das schmeckt so lecker!", "Kannst du bitte den
    /// Tisch decken?") use no word that exists anywhere in the vocab at all
    /// ("schmecken"/"decken"/"Tisch" are absent even outside kitchen), so
    /// same-area headword matching cannot link them. Flagged as a content gap
    /// rather than silently asserted away.
    func testKitchenPhrasesLinkAtLeastOneComponent() throws {
        let cards = try cards(.deSw)
        let kitchenPhrases = cards.filter { $0.area == "kitchen" && $0.kind == .phrase }
        XCTAssertEqual(kitchenPhrases.count, 8)

        let knownUnlinkable: Set<String> = ["Das schmeckt so lecker!", "Kannst du bitte den Tisch decken?"]
        let unexpectedlyUnlinked = kitchenPhrases
            .filter { $0.componentIDs.isEmpty && !knownUnlinkable.contains($0.german) }
            .map(\.german)
        XCTAssertTrue(unexpectedlyUnlinked.isEmpty, "unexpectedly unlinked: \(unexpectedlyUnlinked)")

        let linkedCount = kitchenPhrases.filter { !$0.componentIDs.isEmpty }.count
        XCTAssertEqual(linkedCount, kitchenPhrases.count - knownUnlinkable.count)
    }

    func testKochenReisPhraseLinksToKochenVerb() throws {
        let cards = try cards(.deSw)
        guard let phrase = cards.first(where: { $0.kind == .phrase && $0.german == "Kochst du heute Reis?" }) else {
            XCTFail("catalog missing 'Kochst du heute Reis?'")
            return
        }
        guard let kochen = cards.first(where: {
            $0.kind == .verb && $0.area == phrase.area && $0.german == "kochen"
        }) else {
            XCTFail("catalog missing 'kochen' verb in \(phrase.area)")
            return
        }
        XCTAssertTrue(
            phrase.componentIDs.contains(kochen.id),
            "expected \(phrase.componentIDs) to contain \(kochen.id)"
        )
    }

    // MARK: - Diagnostics

    func testLinkReport() throws {
        let report = CatalogImporter.linkReport(cards: try cards(.deSw))
        XCTAssertEqual(report.phrases, 105)
        XCTAssertGreaterThanOrEqual(report.linked, 29)
        XCTAssertGreaterThan(report.avgComponents, 0)
    }

    // MARK: - Loading both pairs

    func testLoadBothPairsFromCatalog() throws {
        let all = try LanguagePair.allCases.flatMap { try cards($0) }
        XCTAssertEqual(all.count, 343 * 2)
        let ids = all.map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count, "duplicate ids across pairs")
        XCTAssertEqual(Set(all.map(\.pair)), [.deSw, .deUk])
    }
}
