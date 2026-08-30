import Foundation
import SprossKern

/// File-backed persistence for the box document — one JSON document per
/// TARGET language (`box-<target>.json`).
///
/// The Kern `StoreCodec` is called HERE, on the actor, not by the caller: the
/// document carries every schedule and every review ever logged, and encoding it
/// is the most expensive thing a save does. Answering a card hands the state
/// over and returns; a burst of answers leaves one state waiting and pays for
/// one encode, ≥5 s later. Atomic writes; `saveNow` at session end and scene
/// background skips the wait.
actor BoxStore {
    private let directory: URL
    private var pendingSave: Task<Void, Never>?
    /// The last state handed over by a debounced save, waiting to be written.
    /// Latest wins — the ones it replaces were never encoded.
    private var waiting: (state: BoxState, target: String)?

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

    /// Debounced save: coalesces bursts of answers into one encode and one write ≥5 s later.
    func save(state: BoxState, target: String) {
        waiting = (state, target)
        pendingSave?.cancel()
        pendingSave = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            try? await self?.flush()
        }
    }

    /// Write whatever a debounced save left waiting; nothing waiting is nothing to do.
    func flush() throws {
        guard let waiting else { return }
        clearPending()
        try write(StoreCodec.shared.encode(state: waiting.state), target: waiting.target)
    }

    func saveNow(state: BoxState, target: String) throws {
        clearPending()
        try write(StoreCodec.shared.encode(state: state), target: target)
    }

    private func clearPending() {
        waiting = nil
        pendingSave?.cancel()
        pendingSave = nil
    }

    private func write(_ json: String, target: String) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let tmp = directory.appendingPathComponent(".box-\(target).tmp")
        try Data(json.utf8).write(to: tmp, options: .atomic)
        _ = try FileManager.default.replaceItemAt(fileURL(target: target), withItemAt: tmp)
    }

    /// Kern `WidgetSnapshotBuilder` JSON for the decode-only iOS widget, written
    /// next to the box documents. Built here for the same reason the box is:
    /// it walks the exposure ranking, the active cards and every day the box has
    /// ever tallied (`kern/docs/snapshots.md`).
    func saveWidgetSnapshot(
        state: BoxState,
        nowEpochMillis: Int64,
        otherLanguagesDailyStats: [[String: DayStats]],
    ) {
        let json = WidgetSnapshotBuilder.shared.build(
            state: state, nowEpochMillis: nowEpochMillis,
            exposureLimit: WidgetSnapshotBuilder.shared.DEFAULT_EXPOSURE_LIMIT,
            otherLanguagesDailyStats: otherLanguagesDailyStats)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? Data(json.utf8).write(to: directory.appendingPathComponent("widget-snapshot.json"),
                                   options: .atomic)
    }
}
