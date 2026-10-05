import Foundation
import SprossKern
import WidgetKit

/// File-backed persistence for the box — one document per TARGET language
/// (`box-<target>.json`), since only one language is ever active and a save should touch
/// only what moved. A v1 document converts as it is read and is written back under the very
/// same name (`kern/docs/snapshots.md`).
///
/// The store also decides when a save reaches the disk — what it writes is Kern's `SaveScope`.
/// The Kern `StoreCodec` is called HERE, on the actor, not by the caller: the document
/// carries every card's log, and encoding it is the most expensive thing a save does.
/// `save` hands the box over and returns; every save is written, and one the actor has not
/// started yet gives way to a newer one, the two scopes added up. `saveNow` writes before it
/// returns, for the caller that waits on the write or its error. Atomic writes.
///
/// The widget's snapshot is built and written here too, beside the box, and its timeline
/// reloaded once it is on disk.
actor BoxStore {
    private struct Waiting {
        let state: BoxState
        let box: StoredBox
        let scope: SaveScope
        let at: Int64
    }

    private struct SiblingDays {
        let target: String
        let tzId: String
        let days: [String: KotlinInt]
    }

    private let directory: URL
    /// Saves handed over and not written yet, by target. Latest wins.
    private var waiting: [String: Waiting] = [:]
    /// What has been read or written this launch, by target.
    private var held: [String: StoredBox] = [:]
    /// The last `answerDays` asked.
    private var siblingDays: SiblingDays?

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
    func load(target: String) throws -> StoredBox? {
        if let cached = held[target] { return cached }
        guard let text = try? String(contentsOf: fileURL(target: target), encoding: .utf8)
        else { return nil }
        let loaded = try StoreCodec.shared.load(json: text)
        held[target] = loaded.box
        // why: a conversion that never reaches disk converts again on every launch.
        if loaded.converted { try write(loaded.box, target: target) }
        return loaded.box
    }

    /// Hands `state` over to be written behind the caller, with what `scope` carries.
    func save(state: BoxState, scope: SaveScope) {
        hold(state, scope: scope)
        Task { [weak self] in try? await self?.flush() }
    }

    /// Writes `state`, with what `scope` carries, before it returns.
    func saveNow(state: BoxState, scope: SaveScope) throws {
        hold(state, scope: scope)
        try flush()
    }

    /// Write whatever a save left waiting; nothing waiting is nothing to do.
    func flush() throws {
        let due = waiting
        waiting = [:]
        for (target, save) in due {
            if save.scope.writesBox { try write(save.box, target: target) }
            if save.scope.writesSnapshots { writeWidgetSnapshot(state: save.state, at: save.at) }
        }
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

    /// A restore: the languages the file carries replace the ones held and are written, the
    /// rest stay. A save still waiting goes out first, so the restored box is the one left on disk.
    func restore(_ imported: StoredBoxes) throws {
        try flush()
        siblingDays = nil
        for (target, box) in imported.boxes {
            held[target] = box
            try write(box, target: target)
        }
    }

    /// Answers per day in every language but `target` — the cross-language streak's input.
    /// A sibling that cannot be read is skipped: its own load path surfaces the real error
    /// when the learner switches to it.
    func answerDays(excluding target: String, tzId: String) -> [String: KotlinInt] {
        let days = everyLanguage().answerDaysExcept(target: target, tzId: tzId)
        siblingDays = SiblingDays(target: target, tzId: tzId, days: days)
        return days
    }

    private func everyLanguage() -> StoredBoxes {
        for name in boxFileNames() {
            let target = String(name.dropFirst("box-".count).dropLast(".json".count))
            _ = try? load(target: target)
        }
        return StoredBoxes(boxes: held)
    }

    private func hold(_ state: BoxState, scope: SaveScope) {
        let target = state.joinStamp.target
        let box = StoredBox.companion.of(state: state)
        held[target] = box
        let owed = waiting[target].map { $0.scope.plus(other: scope) } ?? scope
        waiting[target] = Waiting(state: state, box: box, scope: owed, at: Date().epochMillis)
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

    /// Kern `WidgetSnapshotBuilder` JSON for the decode-only iOS widget, written next to the
    /// box documents. Built here for the same reason the box is encoded here: it walks the
    /// exposure ranking, the active cards and every day the logs carry
    /// (`kern/docs/snapshots.md`). Carries the other languages' days because the run is one
    /// commitment across every box.
    private func writeWidgetSnapshot(state: BoxState, at nowEpochMillis: Int64) {
        let target = state.joinStamp.target
        let tzId = currentTzId()
        let cached = siblingDays.flatMap { $0.target == target && $0.tzId == tzId ? $0.days : nil }
        let json = WidgetSnapshotBuilder.shared.build(
            state: state, nowEpochMillis: nowEpochMillis, tzId: tzId,
            exposureLimit: WidgetSnapshotBuilder.shared.DEFAULT_EXPOSURE_LIMIT,
            otherLanguagesAnswerDays: cached ?? answerDays(excluding: target, tzId: tzId))
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? Data(json.utf8).write(to: directory.appendingPathComponent("widget-snapshot.json"),
                                   options: .atomic)
        // why: a placed widget keeps drawing its old timeline until it is rebuilt.
        WidgetCenter.shared.reloadTimelines(ofKind: "SprossWordWidget")
    }
}
