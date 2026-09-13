import Foundation
import SprossKern

/// Carrying the boxes across a reinstall or to another phone: one file with every
/// language in it (`BoxBackup`, `kern/docs/snapshots.md`).
extension AppModel {
    /// The backup file's text — every language, without what belongs to this device alone.
    func backupJSON() async throws -> String {
        try await store.exportJSON()
    }

    /// The store a file carries, every card in it proven readable — or a throw, with
    /// nothing touched.
    func readBackup(_ json: String) async throws -> StoredBoxes {
        try await Task.detached { try BoxBackup.shared.decode(json: json) }.value
    }

    /// Writes the restored languages, then re-opens the pair on screen from the store,
    /// so the box drawn is the restored one and not the one it replaced.
    func restore(_ imported: StoredBoxes) async throws {
        try await store.restore(imported)
        guard let target = targetLanguage else { return }
        await activate(source: sourceLanguage, target: target)
    }
}
