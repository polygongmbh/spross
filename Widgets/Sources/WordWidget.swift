import WidgetKit
import SwiftUI
import DuoKern

@main
struct DuoLernenWidgets: WidgetBundle {
    var body: some Widget {
        WordWidget()
    }
}

/// Passive exposure: a rotating word from the box, fresh every 15 minutes.
/// Prefers cards that need attention (learning phase, low stability, due soon).
struct WordWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SprossWordWidget", provider: WordProvider()) { entry in
            WordWidgetView(entry: entry)
                .containerBackground(Color(.systemBackground), for: .widget)
        }
        .configurationDisplayName("Wort des Moments")
        .description("Zeigt alle 15 Minuten eine Vokabel aus deiner Box.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryRectangular, .accessoryInline])
    }
}

/// A single vocab card projected into the widget (no DuoKern types in the view).
struct WidgetWord {
    let emoji: String
    let article: String?
    let german: String
    let translation: String
}

struct WordEntry: TimelineEntry {
    let date: Date
    /// The rotating card for the compact families (small/medium/lock screen).
    let primary: WidgetWord
    /// Attention-worthy cards for the large family's list; primary is words.first.
    let words: [WidgetWord]
    let dueCount: Int
    let streak: Int
    let retrievability: Double?

    // Convenience accessors for the compact families.
    var emoji: String { primary.emoji }
    var article: String? { primary.article }
    var german: String { primary.german }
    var translation: String { primary.translation }

    static let placeholder = WordEntry(
        date: .now,
        primary: WidgetWord(emoji: "🧊", article: "der", german: "Kühlschrank", translation: "friji"),
        words: [
            WidgetWord(emoji: "🧊", article: "der", german: "Kühlschrank", translation: "friji"),
            WidgetWord(emoji: "🍞", article: "das", german: "Brot", translation: "mkate"),
            WidgetWord(emoji: "💧", article: "das", german: "Wasser", translation: "maji"),
            WidgetWord(emoji: "🌙", article: "der", german: "Mond", translation: "mwezi"),
            WidgetWord(emoji: "🏠", article: "das", german: "Haus", translation: "nyumba"),
        ],
        dueCount: 0, streak: 3, retrievability: 0.9)
}

struct WordProvider: TimelineProvider {
    func placeholder(in context: Context) -> WordEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (WordEntry) -> Void) {
        completion(timelineEntries(from: .now).first ?? .placeholder)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WordEntry>) -> Void) {
        let entries = timelineEntries(from: .now)
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    /// Number of words shown in the large family's list.
    private static let listSize = 5

    /// Up to 6 h of 15-minute entries cycling through attention-worthy cards.
    /// The compact families see one rotating card; the large family sees a
    /// rotating window of `listSize` cards plus box stats.
    private func timelineEntries(from start: Date) -> [WordEntry] {
        guard let state = WidgetBoxReader.loadState() else { return [.placeholder] }
        let cards = BoxEngine.exposureCards(state: state, now: start, limit: 24)
        guard !cards.isEmpty else { return [.placeholder] }
        let words = cards.map {
            WidgetWord(emoji: $0.emoji ?? "🗂️", article: $0.article,
                       german: $0.german, translation: $0.translation)
        }
        let stats = BoxEngine.statistics(state: state, now: start, calendar: .current)
        return (0..<24).map { slot in
            // Rotate a window of `listSize` words; primary is the window head.
            let window = (0..<Self.listSize).map { words[(slot + $0) % words.count] }
            return WordEntry(date: start.addingTimeInterval(Double(slot) * 15 * 60),
                             primary: window[0],
                             words: window,
                             dueCount: stats.dueCount,
                             streak: stats.streak,
                             retrievability: stats.averageRetrievability)
        }
    }
}

enum WidgetBoxReader {
    /// Most recently modified box document in the shared App-Group container.
    static func loadState() -> BoxState? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: "group.net.spross.app") else { return nil }
        let dir = container.appendingPathComponent("box", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: [.contentModificationDateKey]))?
            .filter { $0.lastPathComponent.hasPrefix("box-") && $0.pathExtension == "json" }
            .sorted { (modDate($0) ?? .distantPast) > (modDate($1) ?? .distantPast) }
        guard let newest = files?.first, let data = try? Data(contentsOf: newest) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(BoxState.self, from: data)
    }

    private static func modDate(_ url: URL) -> Date? {
        try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate
    }
}
