import XCTest
import DuoKern

final class ImporterTests: XCTestCase {

    // MARK: - Fixture loading

    private func fixtureData(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures") else {
            XCTFail("Missing fixture \(name).json")
            return Data()
        }
        return try Data(contentsOf: url)
    }

    private func fixtureDirectory() throws -> URL {
        guard let url = Bundle.module.url(forResource: "vocab-de-sw", withExtension: "json", subdirectory: "Fixtures") else {
            XCTFail("Missing fixtures directory")
            return URL(fileURLWithPath: "/dev/null")
        }
        return url.deletingLastPathComponent()
    }

    // MARK: - Counts

    func testCardCountsPerPair() throws {
        for name in ["vocab-de-sw", "vocab-de-uk"] {
            let cards = try SeedImporter.importSeed(json: try fixtureData(name))
            let nouns = cards.filter { $0.kind == .noun }.count
            let verbs = cards.filter { $0.kind == .verb }.count
            let phrases = cards.filter { $0.kind == .phrase }.count
            // Full seed incl. basics + amt/arzt/arbeit packs (merged 2026-07-17).
            XCTAssertEqual(nouns, 133, "\(name) noun count")
            XCTAssertEqual(verbs, 105, "\(name) verb count")
            XCTAssertEqual(phrases, 105, "\(name) phrase count")
            XCTAssertEqual(cards.count, 343, "\(name) total count")
        }
    }

    // MARK: - Determinism & uniqueness

    func testDeterministicIDs() throws {
        let data = try fixtureData("vocab-de-sw")
        let first = try SeedImporter.importSeed(json: data).map(\.id)
        let second = try SeedImporter.importSeed(json: data).map(\.id)
        XCTAssertEqual(first, second)
    }

    func testNoDuplicateIDs() throws {
        for name in ["vocab-de-sw", "vocab-de-uk"] {
            let cards = try SeedImporter.importSeed(json: try fixtureData(name))
            let ids = cards.map(\.id)
            XCTAssertEqual(Set(ids).count, ids.count, "\(name) has duplicate ids")
        }
    }

    // MARK: - Field shape

    func testArticlesOnlyOnNouns() throws {
        let cards = try SeedImporter.importSeed(json: try fixtureData("vocab-de-sw"))
        for card in cards {
            if card.kind == .noun {
                XCTAssertNotNil(card.article, "noun \(card.german) missing article")
            } else {
                XCTAssertNil(card.article, "\(card.kind) \(card.german) should not have an article")
            }
        }
    }

    // MARK: - Phrase → word linking

    /// Two kitchen phrases ("Das schmeckt so lecker!", "Kannst du bitte den
    /// Tisch decken?") use no word that exists anywhere in the seed vocab at
    /// all ("schmecken"/"decken"/"Tisch" are absent even outside kitchen —
    /// "Tisch" only ever appears as "Esstisch"/"Schreibtisch"/"Nachttisch" in
    /// other areas), so same-area headword matching cannot link them no
    /// matter how the matcher is tuned. Flagged in the importer's final
    /// report as a content gap rather than silently asserted away.
    func testKitchenPhrasesLinkAtLeastOneComponent() throws {
        let cards = try SeedImporter.importSeed(json: try fixtureData("vocab-de-sw"))
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
        let cards = try SeedImporter.importSeed(json: try fixtureData("vocab-de-sw"))
        guard let phrase = cards.first(where: { $0.kind == .phrase && $0.german == "Kochst du heute Reis?" }) else {
            XCTFail("fixture missing 'Kochst du heute Reis?'")
            return
        }
        guard let kochen = cards.first(where: {
            $0.kind == .verb && $0.area == phrase.area && $0.german == "kochen"
        }) else {
            XCTFail("fixture missing 'kochen' verb in \(phrase.area)")
            return
        }
        XCTAssertTrue(
            phrase.componentIDs.contains(kochen.id),
            "expected \(phrase.componentIDs) to contain \(kochen.id)"
        )
    }

    // MARK: - Diagnostics

    func testLinkReport() throws {
        let cards = try SeedImporter.importSeed(json: try fixtureData("vocab-de-sw"))
        let report = SeedImporter.linkReport(cards: cards)
        XCTAssertEqual(report.phrases, 105)
        XCTAssertGreaterThanOrEqual(report.linked, 29)
        XCTAssertGreaterThan(report.avgComponents, 0)
    }

    // MARK: - SeedContent

    func testLoadAllFromDirectory() throws {
        let cards = try SeedContent.loadAll(from: try fixtureDirectory())
        XCTAssertEqual(cards.count, 343 * 2)
        let ids = cards.map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count, "duplicate ids across pairs")
        let pairs = Set(cards.map(\.pair))
        XCTAssertEqual(pairs, [.deSw, .deUk])
    }
}
