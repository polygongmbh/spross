import Foundation
import DuoKern

/// File-backed persistence for `BoxState` — one JSON document per language pair.
/// Atomic writes; debounced saves per design.md (≥5 s after answers, immediate at
/// session end / scene background via `saveNow`).
actor BoxStore {
    private let directory: URL
    private var pendingSave: Task<Void, Never>?

    init(directory: URL? = nil) {
        self.directory = directory
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("box", isDirectory: true)
    }

    private func fileURL(for pair: LanguagePair) -> URL {
        directory.appendingPathComponent("box-\(pair.rawValue).json")
    }

    func load(pair: LanguagePair) throws -> BoxState? {
        let url = fileURL(for: pair)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let data = try Data(contentsOf: url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(BoxState.self, from: data)
    }

    /// Debounced save: coalesces bursts of answers into one write ≥5 s later.
    func save(_ state: BoxState) {
        pendingSave?.cancel()
        pendingSave = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            try? await self?.saveNow(state)
        }
    }

    func saveNow(_ state: BoxState) throws {
        pendingSave?.cancel()
        pendingSave = nil
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(state)
        let tmp = directory.appendingPathComponent(".box-\(state.config.pair.rawValue).tmp")
        try data.write(to: tmp, options: .atomic)
        _ = try FileManager.default.replaceItemAt(fileURL(for: state.config.pair), withItemAt: tmp)
    }
}
