import Foundation
import SprossKern

/// File-backed persistence for the box — one document per TARGET language
/// (`box-<target>.json`), since only one language is ever active and a save should touch
/// only what moved. A v1 document converts as it is read and is written back under the very
/// same name (`kern/docs/snapshots.md`).
///
/// The Kern `StoreCodec` is called HERE, on the actor, not by the caller: the document
/// carries every card's log, and encoding it is the most expensive thing a save does.
/// Answering a card hands the state over and returns; a burst of answers leaves one box
/// waiting and pays for one encode, ≥5 s later. Atomic writes; `saveNow` at session end and
/// scene background skips the wait.
actor BoxStore {
    private let directory: URL
    private var pendingSave: Task<Void, Never>?
    /// The box a debounced save left waiting, by target. Latest wins.
    private var waiting: (box: StoredBox, target: String)?
    /// What has been read or written this launch, by target.
    private var held: [String: StoredBox] = [:]

    /// App-Group container so the widget can read the box; falls back to
    /// Documents when the group is unavailable (e.g. unit tests).
    static let appGroup = AppGroup.identifier

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

    /// One language's stored box, or nil where the device holds none. Throws where the file
    /// exists but cannot be read: Home says so rather than bootstrapping over it.
    func box(target: String) throws -> StoredBox? {
        if let cached = held[target] { return cached }
        guard let text = try? String(contentsOf: fileURL(target: target), encoding: .utf8)
        else { return nil }
        let loaded = try StoreCodec.shared.load(json: text)
        held[target] = loaded.box
        // why: a conversion that never reaches disk converts again on every launch.
        if loaded.converted { try write(loaded.box, target: target) }
        return loaded.box
    }

    /// Debounced save: coalesces bursts of answers into one encode and one write ≥5 s later.
    func save(state: BoxState) {
        hold(state)
        pendingSave?.cancel()
        pendingSave = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            try? await self?.flush()
        }
    }

    func saveNow(state: BoxState) throws {
        hold(state)
        try flush()
    }

    /// Write whatever a debounced save left waiting; nothing waiting is nothing to do.
    func flush() throws {
        guard let waiting else { return }
        clearPending()
        try write(waiting.box, target: waiting.target)
    }

    /// The export's text — `only`, or every language worth carrying, minus what belongs to
    /// this device alone.
    func exportJSON(only: String?) throws -> String {
        try flush()
        return BoxBackup.shared.encode(boxes: everyLanguage(), only: only)
    }

    /// The languages an export would carry — what it can offer to narrow to.
    func carriedLanguages() -> [String] {
        BoxBackup.shared.carried(boxes: everyLanguage())
    }

    /// A restore: the languages the file carries replace the ones held, the rest stay. A
    /// debounced save still waiting holds a replaced box and would write it back, so it goes.
    func restore(_ imported: StoredBoxes) throws {
        clearPending()
        for (target, box) in imported.boxes {
            held[target] = box
            try write(box, target: target)
        }
    }

    /// Answers per day in every language but `target` — the cross-language streak's input.
    /// A sibling that cannot be read is skipped: its own load path surfaces the real error
    /// when the learner switches to it.
    func answerDays(excluding target: String, tzId: String) -> [String: KotlinInt] {
        everyLanguage().answerDaysExcept(target: target, tzId: tzId)
    }

    private func everyLanguage() -> StoredBoxes {
        for name in boxFileNames() {
            let target = String(name.dropFirst("box-".count).dropLast(".json".count))
            _ = try? box(target: target)
        }
        return StoredBoxes(boxes: held)
    }

    private func hold(_ state: BoxState) {
        let box = StoredBox.companion.of(state: state)
        held[state.joinStamp.target] = box
        waiting = (box, state.joinStamp.target)
    }

    private func clearPending() {
        waiting = nil
        pendingSave?.cancel()
        pendingSave = nil
    }

    private func write(_ box: StoredBox, target: String) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let tmp = directory.appendingPathComponent(".box-\(target).tmp")
        try Data(StoreCodec.shared.encode(box: box).utf8).write(to: tmp, options: .atomic)
        _ = try FileManager.default.replaceItemAt(fileURL(target: target), withItemAt: tmp)
    }

    private func boxFileNames() -> [String] {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return names.filter { $0.hasPrefix("box-") && $0.hasSuffix(".json") }
    }

    /// Kern `WidgetSnapshotBuilder` JSON for the decode-only iOS widget, written
    /// next to the box documents. Built here for the same reason the box is: it walks the
    /// exposure ranking, the active cards and every day the logs carry
    /// (`kern/docs/snapshots.md`).
    func saveWidgetSnapshot(
        state: BoxState,
        nowEpochMillis: Int64,
        tzId: String,
        otherLanguagesAnswerDays: [String: KotlinInt],
    ) {
        let json = WidgetSnapshotBuilder.shared.build(
            state: state, nowEpochMillis: nowEpochMillis, tzId: tzId,
            exposureLimit: WidgetSnapshotBuilder.shared.DEFAULT_EXPOSURE_LIMIT,
            otherLanguagesAnswerDays: otherLanguagesAnswerDays)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? Data(json.utf8).write(to: directory.appendingPathComponent("widget-snapshot.json"),
                                   options: .atomic)
    }
}
