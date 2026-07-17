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
        StaticConfiguration(kind: "WordWidget", provider: WordProvider()) { entry in
            WordWidgetView(entry: entry)
                .containerBackground(Color(.systemBackground), for: .widget)
        }
        .configurationDisplayName("Wort des Moments")
        .description("Zeigt alle 15 Minuten eine Vokabel aus deiner Box.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

struct WordEntry: TimelineEntry {
    let date: Date
    let emoji: String
    let article: String?
    let german: String
    let translation: String
    let dueCount: Int

    static let placeholder = WordEntry(date: .now, emoji: "🧊", article: "der",
                                       german: "Kühlschrank", translation: "friji", dueCount: 0)
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

    /// Up to 6 h of 15-minute entries cycling through attention-worthy cards.
    private func timelineEntries(from start: Date) -> [WordEntry] {
        guard let state = WidgetBoxReader.loadState() else { return [.placeholder] }
        let cards = WidgetBoxReader.exposureCards(state: state, now: start, limit: 24)
        guard !cards.isEmpty else { return [.placeholder] }
        let due = BoxEngine.dueNow(state: state, now: start).count
        return (0..<24).map { slot in
            let card = cards[slot % cards.count]
            return WordEntry(date: start.addingTimeInterval(Double(slot) * 15 * 60),
                             emoji: card.emoji ?? "🗂️",
                             article: card.article,
                             german: card.german,
                             translation: card.translation,
                             dueCount: due)
        }
    }
}

enum WidgetBoxReader {
    /// Most recently modified box document in the shared App-Group container.
    static func loadState() -> BoxState? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: "group.dev.tj.duolernen") else { return nil }
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

    /// Attention-worthy cards: learning/relearning first, then review cards by
    /// lowest stability; falls back to any active card. Deterministic order.
    static func exposureCards(state: BoxState, now: Date, limit: Int) -> [Card] {
        let direction = state.config.direction
        let active = state.scheduling.values
            .filter { $0.direction == direction && !$0.suspended && $0.memory != nil }
        let ranked = active.sorted { a, b in
            let aLearning = a.phase == .learning || a.phase == .relearning
            let bLearning = b.phase == .learning || b.phase == .relearning
            if aLearning != bLearning { return aLearning }
            let aStab = a.memory?.stability ?? 0
            let bStab = b.memory?.stability ?? 0
            return (aStab, a.cardID) < (bStab, b.cardID)
        }
        return ranked.prefix(limit).compactMap { state.cards[$0.cardID] }
    }
}
