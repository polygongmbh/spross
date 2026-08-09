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
                // why: names the tap's destination instead of leaning on the default
                // host-app launch, so the tile still opens the app in the states where
                // it has nothing of the learner's to show.
                .widgetURL(URL(string: "spross://widget"))
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
    /// The rotating card for the compact families (small/lock screen).
    let primary: WidgetWord
    /// Attention-worthy cards for the list families, ordered shortest first for
    /// the page; `primary` is the rotation head, which the sort moves out of place.
    let words: [WidgetWord]
    let dueCount: Int
    let streak: Int
    /// Drives the flame's icon/color/count — see `FlameState`.
    let flameState: FlameState
    /// Active cards that have consolidated — the box's growth, not a retention score.
    let consolidated: Int
    /// Trailing fortnight of review counts for the header strip.
    let activityDays: [ActivityDay]

    // Convenience accessors for the compact families.
    var emoji: String { primary.emoji }
    var tint: String? { primary.tint }
    var word: String { primary.word }
    var meaning: String { primary.meaning }

    /// Nothing readable in the App Group yet: no file, or one this version cannot
    /// decode (a schema the app has not rewritten since the update). Sample words
    /// would pass for the learner's box here, so the sprout stands in instead.
    static let awaitingContent = WordEntry(
        date: .now,
        primary: WidgetWord(emoji: "🌱", tint: nil, word: "", meaning: ""),
        words: [], dueCount: 0, streak: 0, flameState: .unlit,
        consolidated: 0, activityDays: [])

    /// A timeline entry never carries an empty window otherwise — the provider
    /// drops out to `awaitingContent` before building one.
    var isAwaitingContent: Bool { words.isEmpty }

    static let placeholder = WordEntry(
        date: .now,
        primary: WidgetWord(emoji: "🧊", tint: nil, word: "friji", meaning: "Kühlschrank"),
        // why: sorted like a real window, or the gallery would advertise a ragged
        // list the placed widget never shows.
        words: sortedForDisplay(placeholderWords),
        dueCount: 0, streak: 3, flameState: .lit, consolidated: 12,
        activityDays: placeholderDays)

    private static let placeholderWords = [
        WidgetWord(emoji: "🧊", tint: nil, word: "friji", meaning: "Kühlschrank"),
        WidgetWord(emoji: "🍞", tint: nil, word: "mkate", meaning: "Brot"),
        WidgetWord(emoji: "💧", tint: nil, word: "maji", meaning: "Wasser"),
        WidgetWord(emoji: "🌙", tint: nil, word: "mwezi", meaning: "Mond"),
        WidgetWord(emoji: "🏠", tint: nil, word: "nyumba", meaning: "Haus"),
        WidgetWord(emoji: "☀️", tint: nil, word: "jua", meaning: "Sonne"),
    ]

    /// A hand-written fortnight so the gallery snapshot and the previews draw a
    /// real strip rather than a flat rule.
    private static let placeholderDays: [ActivityDay] = {
        let counts = [4, 0, 9, 3, 26, 6, 0, 5, 7, 0, 11, 8, 14, 5]
        let calendar = Calendar(identifier: .gregorian)
        let today = calendar.startOfDay(for: .now)
        return counts.enumerated().compactMap { offset, reviews in
            guard let day = calendar.date(byAdding: .day, value: offset - 13, to: today)
            else { return nil }
            return ActivityDay(day: day, reviews: reviews, isToday: offset == counts.count - 1)
        }
    }()
}

struct WordProvider: TimelineProvider {
    func placeholder(in context: Context) -> WordEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (WordEntry) -> Void) {
        // why: the gallery advertises what the widget does, so it keeps the sample
        // box even on a phone whose app has never written a snapshot.
        if context.isPreview { return completion(.placeholder) }
        completion(timelineEntries(from: .now)?.first ?? .awaitingContent)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WordEntry>) -> Void) {
        guard let entries = timelineEntries(from: .now) else {
            // why: `.atEnd` on a single entry dated now asks for a reload at once and
            // burns the refresh budget; the app rewriting the snapshot reloads the
            // timeline itself, so this is only the fallback for a phone never opened.
            return completion(Timeline(entries: [.awaitingContent],
                                       policy: .after(Date.now.addingTimeInterval(3600))))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    /// Cells in the large family's poster grid (2 × 3).
    private static let listSize = 6

    /// Up to 6 h of 15-minute entries cycling through attention-worthy cards.
    /// The compact families see one rotating card; the list families see a
    /// rotating window of up to `listSize` cards plus box stats.
    /// `nil` when there is no box to render — the caller falls back to the sprout.
    private func timelineEntries(from start: Date) -> [WordEntry]? {
        guard let snapshot = WidgetSnapshotReader.load(),
              !snapshot.entries.isEmpty else { return nil }
        let words = snapshot.entries.map {
            WidgetWord(emoji: $0.emoji ?? "🗂️", tint: $0.articleTint,
                       word: $0.text, meaning: $0.sourceText)
        }
        let dueCount = snapshot.dueCount(now: start)
        let streak = snapshot.streak(now: start)
        let flameState = snapshot.flameState(streak: streak, now: start)
        let activityDays = snapshot.recentDays(count: 14, now: start)
        return (0..<24).map { slot in
            // Rotate a window of `listSize` words; the head is the compact families'
            // card, and each quarter-hour hands the spot to the next word.
            // why: a short box would otherwise wrap and repeat a word in one tile.
            let window = (0..<min(Self.listSize, words.count))
                .map { words[(slot + $0) % words.count] }
            return WordEntry(date: start.addingTimeInterval(Double(slot) * 15 * 60),
                             primary: window[0],
                             words: sortedForDisplay(window),
                             dueCount: dueCount,
                             streak: streak,
                             flameState: flameState,
                             consolidated: snapshot.consolidatedCount,
                             activityDays: activityDays)
        }
    }

}

/// Shortest pair first, so a list opens into a cone around its emoji spine and the
/// grid fills reading order short-to-long. Which cards travel stays kern's attention
/// ranking; only where they land in the tile is decided here.
func sortedForDisplay(_ window: [WidgetWord]) -> [WidgetWord] {
    window.sorted {
        let left = ($0.word.count + $0.meaning.count, $0.word.count)
        let right = ($1.word.count + $1.meaning.count, $1.word.count)
        return left < right
    }
}
