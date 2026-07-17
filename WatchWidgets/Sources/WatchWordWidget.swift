import WidgetKit
import SwiftUI

@main
struct DuoLernenWatchWidgets: WidgetBundle {
    var body: some Widget {
        WatchWordWidget()
    }
}

/// "Wort des Moments" as a watch complication: passive exposure to
/// attention-worthy cards from the phone snapshot, fresh every 15 minutes.
struct WatchWordWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchWordWidget", provider: WatchWordProvider()) { entry in
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
    let article: String?
    let german: String
    let translation: String
    let dueCount: Int

    static let placeholder = WatchWordEntry(date: .now, emoji: "🧊", article: "der",
                                            german: "Kühlschrank", translation: "friji",
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

    /// Up to 6 h of 15-minute entries cycling through the snapshot's
    /// exposure cards (learning-phase first, then lowest stability —
    /// mirrors the iOS widget's ranking, against WatchSnapshot).
    private func timelineEntries(from start: Date) -> [WatchWordEntry] {
        guard let snapshot = WatchSnapshotStore.load() else { return [.placeholder] }
        let cards = snapshot.exposureCards(limit: 24)
        guard !cards.isEmpty else { return [.placeholder] }
        let due = snapshot.dueCardIDs(now: start).count
        return (0..<24).map { slot in
            let card = cards[slot % cards.count]
            return WatchWordEntry(date: start.addingTimeInterval(Double(slot) * 15 * 60),
                                  emoji: card.emoji ?? "🗂️",
                                  article: card.article,
                                  german: card.german,
                                  translation: card.translation,
                                  dueCount: due)
        }
    }
}
