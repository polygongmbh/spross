import XCTest
import DuoKern

/// The migration's parity gate: `CatalogImporter`, reading the language-agnostic
/// catalog, must reproduce the legacy `SeedImporter` output byte-for-byte on the
/// frozen legacy fixtures — every Card field, every index, for both pairs.
/// Scheduling history keys off card ids and seed order, so any drift here would
/// silently reset every early tester's box.
final class CatalogParityTests: XCTestCase {

    private func catalogDirectory() throws -> URL {
        guard let url = Bundle.module.url(
            forResource: "concepts", withExtension: "json",
            subdirectory: "Fixtures/catalog") else {
            XCTFail("Missing Fixtures/catalog")
            return URL(fileURLWithPath: "/dev/null")
        }
        return url.deletingLastPathComponent()
    }

    private func seedData(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(
            forResource: name, withExtension: "json", subdirectory: "Fixtures") else {
            XCTFail("Missing fixture \(name).json")
            return Data()
        }
        return try Data(contentsOf: url)
    }

    func testCatalogMatchesLegacySeedForBothPairs() throws {
        let dir = try catalogDirectory()
        for (pair, seedName) in [(LanguagePair.deSw, "vocab-de-sw"),
                                 (LanguagePair.deUk, "vocab-de-uk")] {
            let legacy = try SeedImporter.importSeed(json: try seedData(seedName))
            let catalog = try CatalogImporter.importCatalog(directory: dir, pair: pair)

            XCTAssertEqual(catalog.count, legacy.count, "\(pair) card count")
            XCTAssertEqual(catalog.count, 343, "\(pair) expected 343 cards")

            // Per-card field diff first (readable failure), then whole-array
            // equality as the strict backstop (catches ordering too).
            for (i, (c, l)) in zip(catalog, legacy).enumerated() {
                XCTAssertEqual(c, l, "\(pair) card #\(i) mismatch: catalog=\(c) legacy=\(l)")
            }
            XCTAssertEqual(catalog, legacy, "\(pair) full card array mismatch")
        }
    }
}
