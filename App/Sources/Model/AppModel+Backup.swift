import Foundation
import SprossKern

/// Carrying the boxes across a reinstall or to another phone: one file with every
/// language in it (`BoxBackup`, `kern/docs/snapshots.md`).
extension AppModel {
    /// The backup file's text — `only`, or every language, without what belongs to this
    /// device alone.
    func backupJSON(only: String? = nil) async throws -> String {
        try await store.exportJSON(only: only)
    }

    /// The languages an export would carry: a box the learner only ever opened is not one
    /// of them, so the export neither offers it nor lands it empty on the other phone.
    func backupLanguages() async -> [String] {
        await store.carriedLanguages()
    }

    /// The store a file carries, every card in it proven readable — or a throw, with
    /// nothing touched.
    func readBackup(_ json: String) async throws -> StoredBoxes {
        try await Task.detached { try BoxBackup.shared.decode(json: json) }.value
    }

    /// Writes the restored languages, then re-opens the pair on screen from the store,
    /// so the box drawn is the restored one and not the one it replaced.
    ///
    /// The pair it opens is the one the FILE names (`StoredBox.source`): the progress was
    /// made under that known language, and re-reading it under this device's would leave
    /// every own word written in the old one unpaired and untrained. A file from before the
    /// store recorded it names none, and the device's own setting stands.
    func restore(_ imported: StoredBoxes) async throws {
        try await store.restore(imported)
        guard let target = targetLanguage else { return }
        let restored = imported.boxes[target]?.source ?? sourceLanguage
        await activate(source: restored, target: target)
    }
}
