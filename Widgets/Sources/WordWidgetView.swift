import WidgetKit
import SwiftUI

/// Widget rendering. Self-contained styling (the app's design tokens live in
/// the app target); article-tint colors mirror Theme.swift.
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
            wordLine(font: .title3.bold())
            Text(entry.meaning)
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
            wordLine(for: word, font: .body.weight(.semibold))
            Spacer(minLength: 6)
            Text(word.meaning)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
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

    private func tintColor(_ tint: String) -> Color {
        switch tint {
        case "der": Color(red: 0.10, green: 0.44, blue: 0.76)
        case "die": Color(red: 0.76, green: 0.15, blue: 0.36)
        case "das": Color(red: 0.12, green: 0.48, blue: 0.20)
        default: .secondary
        }
    }
}
