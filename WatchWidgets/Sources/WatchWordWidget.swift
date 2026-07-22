import WidgetKit
import SwiftUI

@main
struct SprossWatchWidgets: WidgetBundle {
    var body: some Widget {
        WatchWordWidget()
    }
}

/// "Wort des Moments" as a watch complication: passive exposure to
/// attention-worthy cards from the phone snapshot, fresh every 15 minutes.
struct WatchWordWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SprossWatchWordWidget", provider: WatchWordProvider()) { entry in
            WatchWordWidgetView(entry: entry)
                .containerBackground(.black, for: .widget)
        }
        .configurationDisplayName("Wort des Moments")
        .description("Zeigt alle 15 Minuten eine Vokabel aus deiner Box.")
        .supportedFamilies([.accessoryRectangular, .accessoryCircular, .accessoryCorner])
    }
}

struct WatchWordEntry: TimelineEntry {
    let date: Date
    let emoji: String
    /// Snapshot `articleTint` ("der"/"die"/"das"); doubles as the article word.
    let tint: String?
    /// TARGET-side text (exposure surfaces always show the learned language).
    let word: String
    /// Source meaning (the known language).
    let meaning: String
    let dueCount: Int

    static let placeholder = WatchWordEntry(date: .now, emoji: "🧊", tint: nil,
                                            word: "friji", meaning: "Kühlschrank",
                                            dueCount: 0)
}

struct WatchWordProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchWordEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (WatchWordEntry) -> Void) {
        completion(timelineEntries(from: .now).first ?? .placeholder)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchWordEntry>) -> Void) {
        completion(Timeline(entries: timelineEntries(from: .now), policy: .atEnd))
    }

    /// Up to 6 h of 15-minute entries cycling through the stored snapshot's
    /// exposure entries (phone-ranked due-first, then exposure tiers).
    private func timelineEntries(from start: Date) -> [WatchWordEntry] {
        guard let snapshot = WatchSnapshotStore.load() else { return [.placeholder] }
        let exposure = snapshot.exposureEntries(limit: 24)
        guard !exposure.isEmpty else { return [.placeholder] }
        let due = snapshot.dueEntries(now: start).count
        return (0..<24).map { slot in
            let entry = exposure[slot % exposure.count]
            return WatchWordEntry(date: start.addingTimeInterval(Double(slot) * 15 * 60),
                                  emoji: entry.emoji ?? "🗂️",
                                  tint: entry.articleTint,
                                  word: entry.targetText,
                                  meaning: entry.sourceText,
                                  dueCount: due)
        }
    }
}
