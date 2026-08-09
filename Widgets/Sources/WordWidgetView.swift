import WidgetKit
import SwiftUI

/// Widget rendering. Self-contained styling (the app's design tokens live in
/// the app target); article-tint colors mirror Theme.swift.
struct WordWidgetView: View {
    let entry: WordEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if entry.isAwaitingContent {
            awaitingContent
        } else {
            switch family {
            case .accessoryRectangular: lockScreen
            case .accessoryInline: inline
            case .systemLarge: large
            case .systemMedium: medium
            default: small
            }
        }
    }

    /// The box lives in the app; the widget only reads what the app last handed
    /// over, and an update can leave that unreadable until the app next runs.
    /// The sprout keeps the tile recognisably Spross and names the way back in —
    /// stats and sample words would both be inventions here.
    @ViewBuilder
    private var awaitingContent: some View {
        switch family {
        case .accessoryInline:
            Text(verbatim: "🌱 Spross öffnen")
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Text("Spross öffnen").font(.headline)
                Text("für frische Wörter").font(.caption).foregroundStyle(.secondary)
            }
        default:
            VStack(spacing: 4) {
                Spacer(minLength: 0)
                Text(verbatim: "🌱").font(.system(size: 44))
                Text("Spross öffnen")
                    .font(.title3.bold())
                Text("für frische Wörter")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
        }
    }

    /// One word with room to be looked at: the picture leads, the stats are a
    /// single quiet line, and nothing rules the square into sections.
    private var small: some View {
        VStack(spacing: 4) {
            Spacer(minLength: 0)
            Text(entry.emoji).font(.system(size: 44))
            wordLine(font: .title3.bold())
            Text(entry.meaning)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            statsFooter
        }
        .frame(maxWidth: .infinity)
    }

    private var medium: some View {
        VStack(alignment: .leading, spacing: 8) {
            statsHeader
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(entry.words.prefix(3).enumerated()), id: \.offset) { _, word in
                    wordRow(word)
                }
            }
            Spacer(minLength: 0)
        }
    }

    /// Flame and due count on one line for the bottom of the small tile.
    private var statsFooter: some View {
        HStack(spacing: 10) {
            flameLabel
            if entry.dueCount > 0 {
                Label("\(entry.dueCount)", systemImage: "tray.full")
                    .foregroundStyle(.orange)
            }
        }
        .font(.caption2.weight(.semibold))
    }

    /// A poster rather than a longer list: the cell is the unit, so a pair is read
    /// in place instead of scanned across the tile, and stacking gives each side
    /// the full column width. Rows are spaced apart rather than stacked from the
    /// top — a poster that ends two thirds up the tile reads as a truncated list.
    private var large: some View {
        VStack(alignment: .leading, spacing: 0) {
            statsHeader
            Divider().padding(.top, 8)
            ForEach(Array(posterRows.enumerated()), id: \.offset) { _, row in
                Spacer(minLength: 10)
                HStack(alignment: .top, spacing: 12) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, word in
                        posterCell(word)
                    }
                    // why: a final odd cell must keep its column width, not spread
                    // across the row and break the poster's grid.
                    if row.count == 1 { Color.clear.frame(maxWidth: .infinity) }
                }
            }
        }
    }

    /// The poster's words in rows of two.
    private var posterRows: [[WidgetWord]] {
        let words = Array(entry.words.prefix(6))
        return stride(from: 0, to: words.count, by: 2).map {
            Array(words[$0..<min($0 + 2, words.count)])
        }
    }

    /// 🔥 streak · N fällig, with the fortnight's activity on the right — the
    /// header has the room the bottom of a tile does not.
    private var statsHeader: some View {
        HStack(spacing: 10) {
            flameLabel
            if entry.dueCount > 0 {
                Label("\(entry.dueCount) fällig", systemImage: "tray.full")
                    .foregroundStyle(.orange)
            }
            Spacer(minLength: 8)
            WidgetActivityStrip(days: entry.activityDays)
        }
        .font(.caption.weight(.semibold))
    }

    /// Streak flame — icon/color/count vary with `entry.flameState`; count is
    /// omitted for `.unlit` (bare restart nudge, nothing to count yet).
    @ViewBuilder
    private var flameLabel: some View {
        switch entry.flameState {
        case .lit:
            Label("\(entry.streak)", systemImage: "flame.fill").foregroundStyle(.orange)
        case .dwindling:
            Label("\(entry.streak)", systemImage: "flame.fill").foregroundStyle(.orange.opacity(0.5))
        case .atRisk:
            Label("\(entry.streak)", systemImage: "flame").foregroundStyle(.orange)
        case .unlit:
            Image(systemName: "flame").foregroundStyle(.secondary)
        }
    }

    /// Word and meaning meet at the picture instead of at the tile's two edges,
    /// and the equal side frames put every row's emoji on the same centre line.
    private func wordRow(_ word: WidgetWord) -> some View {
        HStack(spacing: 8) {
            wordLine(for: word, font: .title3.weight(.semibold))
                .frame(maxWidth: .infinity, alignment: .trailing)
            // why: emoji advance widths differ (☀️ against 🚪), so an unsized column
            // would let the spine wander from row to row.
            Text(word.emoji).font(.title3).frame(width: 22)
            Text(word.meaning)
                .font(.body)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// One poster cell: picture, target, meaning, stacked and left-aligned.
    private func posterCell(_ word: WidgetWord) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(word.emoji).font(.system(size: 30))
            wordLine(for: word, font: .headline)
            Text(word.meaning)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var lockScreen: some View {
        VStack(alignment: .leading, spacing: 1) {
            wordLine(font: .headline)
            Text(entry.meaning)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    /// Single tint-only line for above the clock; no color or meaning.
    private var inline: some View {
        Text("\(entry.emoji) \(entry.tint.map { "\($0) " } ?? "")\(entry.word)")
    }

    private func wordLine(font: Font) -> some View {
        wordLine(for: entry.primary, font: font)
    }

    /// Target word with the article word prefixed and colored when the
    /// snapshot carries an `articleTint`; genderless targets render plain.
    private func wordLine(for word: WidgetWord, font: Font) -> some View {
        (articleText(word.tint) + Text(word.word))
            .font(font)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
    }

    private func articleText(_ tint: String?) -> Text {
        guard let tint else { return Text("") }
        return Text("\(tint) ").foregroundStyle(tintColor(tint))
    }

    /// The article set each hue answers for is `Theme.swift`'s: a two-gender
    /// language folds onto masculine-blue and feminine-berry (its plural and
    /// indefinite articles with it) and never reaches the neuter.
    private func tintColor(_ tint: String) -> Color {
        switch tint.lowercased() {
        case "der", "el", "los", "un": Color(red: 0.10, green: 0.44, blue: 0.76)
        case "die", "la", "las", "una": Color(red: 0.76, green: 0.15, blue: 0.36)
        case "das": Color(red: 0.12, green: 0.48, blue: 0.20)
        default: .secondary
        }
    }
}

// Placing a widget on a simulator home screen is the only other way to see these,
// so each family previews from the placeholder box.

#Preview("Klein", as: .systemSmall) {
    WordWidget()
} timeline: {
    WordEntry.placeholder
}

#Preview("Mittel", as: .systemMedium) {
    WordWidget()
} timeline: {
    WordEntry.placeholder
}

#Preview("Groß", as: .systemLarge) {
    WordWidget()
} timeline: {
    WordEntry.placeholder
}

#Preview("Ohne Box", as: .systemSmall) {
    WordWidget()
} timeline: {
    WordEntry.awaitingContent
}
