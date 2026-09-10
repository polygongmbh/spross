import Foundation
import SprossKern

/// Carrying the boxes across a reinstall or to another phone: one file with every
/// target's document (`BoxBackup`, `kern/docs/snapshots.md`).
extension AppModel {
    /// The backup file's text, every box on disk in it.
    func backupJSON() async throws -> String {
        let documents = try await store.allDocuments()
        return try await Task.detached {
            try BoxBackup.shared.encode(documents: documents)
        }.value
    }

    /// Every box the file carries, each proven readable — or a throw, with nothing touched.
    func readBackup(_ json: String) async throws -> [String: String] {
        try await Task.detached { try BoxBackup.shared.decode(json: json) }.value
    }

    /// Writes the restored boxes, then re-opens the pair on screen from disk,
    /// so the box drawn is the restored one and not the one it replaced.
    func restore(_ documents: [String: String]) async throws {
        try await store.replace(documents: documents)
        guard let target = targetLanguage else { return }
        await activate(source: sourceLanguage, target: target)
    }
}
