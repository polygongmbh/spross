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
        case .systemMedium: medium
        default: small
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(entry.emoji).font(.title2)
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
            germanLine(font: .headline)
            Text(entry.translation)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }

    private var medium: some View {
        HStack(spacing: 14) {
            Text(entry.emoji)
                .font(.system(size: 44))
            VStack(alignment: .leading, spacing: 3) {
                germanLine(font: .title3.bold())
                Text(entry.translation)
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if entry.dueCount > 0 {
                VStack {
                    Text("\(entry.dueCount)")
                        .font(.headline)
                        .foregroundStyle(.orange)
                    Text("fällig")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
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

    private func germanLine(font: Font) -> some View {
        (articleText + Text(entry.german))
            .font(font)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
    }

    private var articleText: Text {
        guard let article = entry.article else { return Text("") }
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
