import Foundation
import SprossKern

/// Path-based `CatalogSource` over the bundled catalog folder reference:
/// the Xcode project bundles the repo's catalog/ folder, and kern parses it through this reader.
final class BundleCatalogSource: NSObject, CatalogSource {
    private let directory: URL

    private init(directory: URL) {
        self.directory = directory
    }

    func read(path: String) -> String? {
        try? String(contentsOf: directory.appendingPathComponent(path), encoding: .utf8)
    }

    /// The bundled catalog, parsed; nil where the folder is missing from the bundle.
    // why: ~350 JSON files, most of a megabyte, parsed and fingerprinted — the
    // longest single thing a cold start does, and nothing about it needs the
    // main actor.
    static func load() async -> Catalog? {
        guard let directory = Bundle.main.url(forResource: "catalog", withExtension: nil)
        else { return nil }
        return await Task.detached {
            Catalog.companion.load(source: BundleCatalogSource(directory: directory))
        }.value
    }
}
