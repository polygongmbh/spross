import Foundation

/// Snapshot persistence in the App-Group container — the watch app writes,
/// the watch widget extension reads (the group is valid within the watch).
/// On iOS this type is compiled but unused.
enum WatchSnapshotStore {
    static let appGroup = "group.dev.tj.duolernen"

    static func fileURL() -> URL {
        let base = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("watch-snapshot.json")
    }

    static func load() -> WatchSnapshot? {
        guard let data = try? Data(contentsOf: fileURL()) else { return nil }
        return try? WatchSnapshot.decode(data)
    }

    static func save(_ snapshot: WatchSnapshot) {
        guard let data = try? snapshot.encoded() else { return }
        try? data.write(to: fileURL(), options: .atomic)
    }
}
