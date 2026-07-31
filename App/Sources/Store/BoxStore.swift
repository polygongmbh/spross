import Foundation

/// File-backed persistence for the box document — one JSON document per
/// TARGET language (`box-<target>.json`), encoded/decoded by the Kern
/// `StoreCodec` on the caller's side; this actor only moves strings to disk.
/// Atomic writes, and the save cadence lives here: debounced ≥5 s after
/// answers, immediate at session end and scene background (`saveNow`).
actor BoxStore {
    private let directory: URL
    private var pendingSave: Task<Void, Never>?

    /// App-Group container so the widget can read the box; falls back to
    /// Documents when the group is unavailable (e.g. unit tests).
    static let appGroup = "group.net.spross.app"

    static func defaultDirectory() -> URL {
        let base = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("box", isDirectory: true)
    }

    init(directory: URL? = nil) {
        self.directory = directory ?? Self.defaultDirectory()
    }

    private func fileURL(target: String) -> URL {
        directory.appendingPathComponent("box-\(target).json")
    }

    func load(target: String) throws -> String? {
        let url = fileURL(target: target)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// Debounced save: coalesces bursts of answers into one write ≥5 s later.
    func save(json: String, target: String) {
        pendingSave?.cancel()
        pendingSave = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            try? await self?.saveNow(json: json, target: target)
        }
    }

    func saveNow(json: String, target: String) throws {
        pendingSave?.cancel()
        pendingSave = nil
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let tmp = directory.appendingPathComponent(".box-\(target).tmp")
        try Data(json.utf8).write(to: tmp, options: .atomic)
        _ = try FileManager.default.replaceItemAt(fileURL(target: target), withItemAt: tmp)
    }

    /// Kern `WidgetSnapshotBuilder` JSON for the decode-only iOS widget —
    /// written next to the box documents on every persist, always immediately
    /// (derived data; a stale box file cannot corrupt it).
    func saveWidgetSnapshot(json: String) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? Data(json.utf8).write(to: directory.appendingPathComponent("widget-snapshot.json"),
                                   options: .atomic)
    }
}
