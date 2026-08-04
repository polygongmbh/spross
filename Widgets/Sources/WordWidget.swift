import WidgetKit
import SwiftUI

@main
struct SprossWidgets: WidgetBundle {
    var body: some Widget {
        WordWidget()
    }
}

/// Passive exposure: a rotating word from the box, fresh every 15 minutes.
/// Decode-only Swift over the app-written `WidgetSnapshot` (no engine link);
/// the phone pre-ranks attention-worthy cards on every persist.
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

/// A single vocab card projected into the widget (no snapshot types in the view).
struct WidgetWord {
    let emoji: String
    /// Article tint ("der"/"die"/"das") — doubles as the rendered article word.
    let tint: String?
    /// TARGET-side text (exposure surfaces always show the learned language).
    let word: String
    /// Source meaning (♀ marker pre-baked by the phone).
    let meaning: String
}

struct WordEntry: TimelineEntry {
    let date: Date
    /// The rotating card for the compact families (small/medium/lock screen).
    let primary: WidgetWord
    /// Attention-worthy cards for the large family's list; primary is words.first.
    let words: [WidgetWord]
    let dueCount: Int
    let streak: Int
    /// Active cards that have consolidated — the box's growth, not a retention score.
    let consolidated: Int

    // Convenience accessors for the compact families.
    var emoji: String { primary.emoji }
    var tint: String? { primary.tint }
    var word: String { primary.word }
    var meaning: String { primary.meaning }

    static let placeholder = WordEntry(
        date: .now,
        primary: WidgetWord(emoji: "🧊", tint: nil, word: "friji", meaning: "Kühlschrank"),
        words: [
            WidgetWord(emoji: "🧊", tint: nil, word: "friji", meaning: "Kühlschrank"),
            WidgetWord(emoji: "🍞", tint: nil, word: "mkate", meaning: "Brot"),
            WidgetWord(emoji: "💧", tint: nil, word: "maji", meaning: "Wasser"),
            WidgetWord(emoji: "🌙", tint: nil, word: "mwezi", meaning: "Mond"),
            WidgetWord(emoji: "🏠", tint: nil, word: "nyumba", meaning: "Haus"),
            WidgetWord(emoji: "☀️", tint: nil, word: "jua", meaning: "Sonne"),
            WidgetWord(emoji: "🐟", tint: nil, word: "samaki", meaning: "Fisch"),
            WidgetWord(emoji: "📖", tint: nil, word: "kitabu", meaning: "Buch"),
            WidgetWord(emoji: "🌳", tint: nil, word: "mti", meaning: "Baum"),
            WidgetWord(emoji: "🚪", tint: nil, word: "mlango", meaning: "Tür"),
            WidgetWord(emoji: "🔥", tint: nil, word: "moto", meaning: "Feuer"),
        ],
        dueCount: 0, streak: 3, consolidated: 12)
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
    private static let listSize = 9

    /// Up to 6 h of 15-minute entries cycling through attention-worthy cards.
    /// The compact families see one rotating card; the large family sees a
    /// rotating window of up to `listSize` cards plus box stats.
    private func timelineEntries(from start: Date) -> [WordEntry] {
        guard let snapshot = WidgetSnapshotReader.load(),
              !snapshot.entries.isEmpty else { return [.placeholder] }
        let words = snapshot.entries.map {
            WidgetWord(emoji: $0.emoji ?? "🗂️", tint: $0.articleTint,
                       word: $0.text, meaning: $0.sourceText)
        }
        let dueCount = snapshot.dueCount(now: start)
        let streak = snapshot.streak(now: start)
        return (0..<24).map { slot in
            // Rotate a window of `listSize` words; primary is the window head.
            // why: a short box would otherwise wrap and repeat a word in one tile.
            let window = (0..<min(Self.listSize, words.count))
                .map { words[(slot + $0) % words.count] }
            return WordEntry(date: start.addingTimeInterval(Double(slot) * 15 * 60),
                             primary: window[0],
                             words: window,
                             dueCount: dueCount,
                             streak: streak,
                             consolidated: snapshot.consolidatedCount)
        }
    }
}
