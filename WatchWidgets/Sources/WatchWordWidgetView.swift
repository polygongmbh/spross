import WidgetKit
import SwiftUI

/// Complication faces: rectangular = tinted target word + source meaning;
/// circular/corner = emoji + due count.
struct WatchWordWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchWordEntry

    var body: some View {
        switch family {
        case .accessoryRectangular:
            rectangular
        case .accessoryCorner:
            corner
        default:
            circular
        }
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 4) {
                Text(entry.emoji)
                if let tint = entry.tint {
                    Text(tint)
                        .foregroundStyle(tintColor(tint))
                }
                Text(entry.word)
                    .foregroundStyle(entry.tint.map(tintColor) ?? .white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .font(.system(.headline, design: .rounded, weight: .bold))
            Text(entry.meaning)
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var circular: some View {
        VStack(spacing: 0) {
            Text(entry.emoji)
                .font(.system(size: 20))
            Text("\(entry.dueCount)")
                .font(.system(.caption2, design: .rounded, weight: .bold))
        }
    }

    private var corner: some View {
        Text(entry.emoji)
            .font(.system(size: 24))
            .widgetLabel {
                Text(entry.dueCount > 0 ? "\(entry.dueCount) fällig" : "Alles sitzt")
                    .font(.system(.caption2, design: .rounded))
            }
    }

    /// Article-tint colors, dark variants of the phone palette; the article set
    /// each hue answers for is `Theme.swift`'s (a two-gender language folds onto
    /// masculine-blue and feminine-berry, and never reaches the neuter).
    private func tintColor(_ tint: String) -> Color {
        switch tint.lowercased() {
        case "der", "el", "los", "un": return Color(red: 0x74 / 255.0, green: 0xC0 / 255.0, blue: 0xFC / 255.0)
        case "die", "la", "las", "una": return Color(red: 0xF7 / 255.0, green: 0x83 / 255.0, blue: 0xAC / 255.0)
        case "das": return Color(red: 0x69 / 255.0, green: 0xDB / 255.0, blue: 0x7C / 255.0)
        default: return .white
        }
    }
}
