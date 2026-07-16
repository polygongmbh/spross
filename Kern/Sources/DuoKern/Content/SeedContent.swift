import Foundation

/// Convenience for loading every bundled seed vocabulary file at once.
public enum SeedContent {

    /// Reads and imports every `vocab-*.json` file in `directory`, in
    /// filename-sorted order.
    public static func loadAll(from directory: URL) throws -> [Card] {
        let entries = try FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: nil
        )
        let files = entries
            .filter { $0.lastPathComponent.hasPrefix("vocab-") && $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }

        var cards: [Card] = []
        for file in files {
            let data = try Data(contentsOf: file)
            cards.append(contentsOf: try SeedImporter.importSeed(json: data))
        }
        return cards
    }
}
