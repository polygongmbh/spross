import WidgetKit
import SwiftUI

/// Widget rendering. Self-contained styling (the app's design tokens live in
/// the app target); article colors mirror Theme.swift.
struct WordWidgetView: View {
    let entry: WordEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryRectangular: lockScreen
        case .accessoryInline: inline
        case .systemLarge: large
        case .systemMedium: medium
        default: small
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(entry.emoji).font(.system(size: 40))
                Spacer()
                if entry.dueCount > 0 {
                    Text("\(entry.dueCount)")
                        .font(.caption2.bold())
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Capsule().fill(.orange.opacity(0.2)))
                        .foregroundStyle(.orange)
                }
            }
            Spacer(minLength: 0)
            germanLine(font: .title3.bold())
            Text(entry.translation)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Divider()
            statsFooter
        }
    }

    private var medium: some View {
        VStack(alignment: .leading, spacing: 7) {
            statsHeader
            Divider()
            VStack(alignment: .leading, spacing: 7) {
                ForEach(Array(entry.words.prefix(3).enumerated()), id: \.offset) { _, word in
                    wordRow(word)
                }
            }
            Spacer(minLength: 0)
        }
    }

    /// Compact streak · retrievability line for the bottom of the small tile.
    private var statsFooter: some View {
        HStack(spacing: 8) {
            if entry.streak > 0 {
                Label("\(entry.streak)", systemImage: "flame.fill")
                    .foregroundStyle(.orange)
            }
            Spacer()
            if let retrievability = entry.retrievability {
                Text("\(Int((retrievability * 100).rounded())) %")
                    .foregroundStyle(.secondary)
            }
        }
        .font(.caption2.weight(.semibold))
    }

    private var large: some View {
        VStack(alignment: .leading, spacing: 10) {
            statsHeader
            Divider()
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(entry.words.enumerated()), id: \.offset) { _, word in
                    wordRow(word)
                }
            }
            Spacer(minLength: 0)
        }
    }

    /// 🔥 streak · N fällig · retrievability — glanceable box health.
    private var statsHeader: some View {
        HStack(spacing: 12) {
            if entry.streak > 0 {
                Label("\(entry.streak)", systemImage: "flame.fill")
                    .foregroundStyle(.orange)
            }
            if entry.dueCount > 0 {
                Label("\(entry.dueCount) fällig", systemImage: "tray.full")
                    .foregroundStyle(.orange)
            }
            Spacer()
            if let retrievability = entry.retrievability {
                Text("\(Int((retrievability * 100).rounded())) %")
                    .foregroundStyle(.secondary)
            }
        }
        .font(.caption.weight(.semibold))
    }

    private func wordRow(_ word: WidgetWord) -> some View {
        HStack(spacing: 10) {
            Text(word.emoji).font(.title3)
            germanLine(for: word, font: .body.weight(.semibold))
            Spacer(minLength: 6)
            Text(word.translation)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }

    private var lockScreen: some View {
        VStack(alignment: .leading, spacing: 1) {
            germanLine(font: .headline)
            Text(entry.translation)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    /// Single tint-only line for above the clock; no color or translation.
    private var inline: some View {
        Text("\(entry.emoji) \(entry.article.map { "\($0) " } ?? "")\(entry.german)")
    }

    private func germanLine(font: Font) -> some View {
        germanLine(for: entry.primary, font: font)
    }

    private func germanLine(for word: WidgetWord, font: Font) -> some View {
        (articleText(word.article) + Text(word.german))
            .font(font)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
    }

    private func articleText(_ article: String?) -> Text {
        guard let article else { return Text("") }
        return Text("\(article) ").foregroundStyle(articleColor(article))
    }

    private func articleColor(_ article: String) -> Color {
        switch article {
        case "der": Color(red: 0.10, green: 0.44, blue: 0.76)
        case "die": Color(red: 0.76, green: 0.15, blue: 0.36)
        case "das": Color(red: 0.12, green: 0.48, blue: 0.20)
        default: .secondary
        }
    }
}
