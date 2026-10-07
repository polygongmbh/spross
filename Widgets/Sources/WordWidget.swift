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
/// the phone pre-ranks attention-worthy cards on every save that carries the snapshots.
struct WordWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SprossWordWidget", provider: WordProvider()) { entry in
            WordWidgetView(entry: entry)
                .containerBackground(Color(.systemBackground), for: .widget)
                // why: names the tap's destination instead of leaning on the default
                // host-app launch, so the tile still opens the app in the states where
                // it has nothing of the learner's to show.
                .widgetURL(URL(string: "spross://widget"))
                .environment(\.locale, GlanceChrome.locale(entry.chromeLanguage))
        }
        // The gallery has no snapshot to follow, so these two read the device language.
        .configurationDisplayName(Text("widget.name", tableName: GlanceChrome.table))
        .description(Text("widget.description", tableName: GlanceChrome.table))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryRectangular, .accessoryInline])
    }
}

/// A single vocab card projected into the widget (no snapshot types in the view).
struct WidgetWord {
    let emoji: String
    /// The article shown in front of `word`, nil where the box names none.
    var article: String? = nil
    /// What `article` marks — the tint reads this, never the article word.
    var gender: SnapshotGender? = nil
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
    /// Whether the flame carries a count.
    let streakHealth: StreakHealth
    /// The flame's grade for `streakHealth`, as kern ships it; full strength in the
    /// sample entries, which carry no snapshot.
    var flameOpacity: Double = 1
    var flameSaturation: Double = 1
    /// Active cards that have settled — the box's growth, not a retention score.
    let settled: Int
    /// Trailing fortnight of bars for the header strip, as kern sized them.
    let activityBars: [WidgetSnapshot.Bar]
    /// The height the strip's row reserves.
    var activityHeight: Double = 0
    /// The snapshot's chrome language; nil with no snapshot to read.
    var chromeLanguage: String? = nil

    // Convenience accessors for the compact families.
    var emoji: String { primary.emoji }
    var article: String? { primary.article }
    var word: String { primary.word }
    var meaning: String { primary.meaning }

    /// Nothing readable in the App Group yet: no file, or one this version cannot
    /// decode (a schema the app has not rewritten since the update). Sample words
    /// would pass for the learner's box here, so the sprout stands in instead.
    static let awaitingContent = WordEntry(
        date: .now,
        primary: WidgetWord(emoji: "🌱", word: "", meaning: ""),
        words: [], dueCount: 0, streak: 0, streakHealth: .noRun,
        settled: 0, activityBars: [])

    /// A timeline entry never carries an empty window otherwise — the provider
    /// drops out to `awaitingContent` before building one.
    var isAwaitingContent: Bool { words.isEmpty }

    static let placeholder = WordEntry(
        date: .now,
        primary: placeholderWords[0],
        // why: sorted like a real window, or the gallery would advertise a ragged
        // list the placed widget never shows.
        words: sortedForDisplay(placeholderWords),
        dueCount: 0, streak: 3, streakHealth: .earned, settled: 12,
        activityBars: placeholderBars, activityHeight: 16)

    private static let placeholderWords = [
        WidgetWord(emoji: "🧊", word: "friji", meaning: GlanceChrome.sample("widget.sample.fridge")),
        WidgetWord(emoji: "🍞", word: "mkate", meaning: GlanceChrome.sample("widget.sample.bread")),
        WidgetWord(emoji: "💧", word: "maji", meaning: GlanceChrome.sample("widget.sample.water")),
        WidgetWord(emoji: "🌙", word: "mwezi", meaning: GlanceChrome.sample("widget.sample.moon")),
        WidgetWord(emoji: "🏠", word: "nyumba", meaning: GlanceChrome.sample("widget.sample.house")),
        WidgetWord(emoji: "☀️", word: "jua", meaning: GlanceChrome.sample("widget.sample.sun")),
    ]

    /// A hand-written fortnight, as kern would size it, so the gallery snapshot and the previews
    /// draw a real strip rather than a flat rule.
    private static let placeholderBars: [WidgetSnapshot.Bar] = [
        (4, 6.3, 0.67), (0, 1.5, 0.45), (9, 9.4, 0.77), (3, 5.4, 0.64), (26, 16, 1), (6, 7.7, 0.71), (0, 1.5, 0.45),
        (5, 7, 0.69), (7, 8.3, 0.74), (0, 1.5, 0.45), (11, 10.4, 0.81), (8, 8.9, 0.76), (14, 11.7, 0.85), (5, 7, 0.69),
    ].map { WidgetSnapshot.Bar(reviews: $0.0, height: $0.1, fillOpacity: $0.2) }
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

    /// How long one window stands: the timeline hands WidgetKit an entry per quarter hour.
    private static let stepMillis: Int64 = 15 * 60 * 1000

    /// Up to 6 h of 15-minute entries cycling through attention-worthy cards.
    /// The compact families see one rotating card; the list families see a
    /// rotating window of up to `listSize` cards plus box stats.
    /// `nil` when there is no box to render — the caller falls back to the sprout.
    private func timelineEntries(from start: Date) -> [WordEntry]? {
        guard let snapshot = WidgetSnapshotReader.load(),
              !snapshot.entries.isEmpty else { return nil }
        let words = snapshot.entries.map {
            WidgetWord(emoji: $0.emoji ?? "🗂️", article: $0.article, gender: $0.gender,
                       word: $0.text, meaning: $0.sourceText)
        }
        let startMillis = Int64(start.timeIntervalSince1970 * 1000)
        let firstStep = startMillis / Self.stepMillis
        return (0..<24).map { slot in
            // The first entry is now; every later one opens a step, so the head moves
            // when the clock says it does rather than a fraction of a step after.
            let millis = slot == 0 ? startMillis : (firstStep + Int64(slot)) * Self.stepMillis
            let date = Date(timeIntervalSince1970: Double(millis) / 1000)
            // layer-ok: kern `WidgetRotation.window`, the one waived copy — the extension links
            // no Kotlin and the head moves with the render clock, so no snapshot can carry it.
            let head = Int((millis / Self.stepMillis) % Int64(words.count))
            let window = (0..<min(Self.listSize, words.count)).map { words[(head + $0) % words.count] }
            // A timeline can cross midnight, so every entry reads its own moment.
            let streakDay = snapshot.streakDay(now: date)
            return WordEntry(date: date,
                             primary: window[0],
                             words: sortedForDisplay(window),
                             dueCount: snapshot.dueCount(now: date),
                             streak: streakDay?.streak ?? 0,
                             streakHealth: streakDay?.health ?? .noRun,
                             flameOpacity: streakDay?.flameOpacity ?? 1,
                             flameSaturation: streakDay?.flameSaturation ?? 1,
                             settled: snapshot.allSettledCount,
                             activityBars: snapshot.activityBars(now: date),
                             activityHeight: snapshot.activityHeight,
                             chromeLanguage: snapshot.chromeLanguage)
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
