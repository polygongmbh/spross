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

    /// Article-tint colors; the article set each hue answers for is `Theme.swift`'s
    /// (a two-gender language folds onto masculine-blue and feminine-berry, and
    /// never reaches the neuter).
    private func tintColor(_ tint: String) -> Color {
        switch tint.lowercased() {
        case "der", "el", "los", "un": return WatchWidgetColors.der
        case "die", "la", "las", "una": return WatchWidgetColors.die
        case "das": return WatchWidgetColors.das
        default: return .white
        }
    }
}

// Article tints, copied from the canonical table in `App/Sources/Design/Theme.swift` —
// kern's `PaletteParityTest` fails the fast gate when this copy drifts from it. A
// complication extension links neither the app's design tokens nor the watch app's.
private extension Color {
    init(complicationHex hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }

    /// Both canonical columns, resolved to the dark one at build time: a
    /// complication always sits on the watch's black, and watchOS has no dynamic
    /// UIColor provider to pick with. The light half is carried so this copy can
    /// be held to the WHOLE table rather than to half of it.
    init(light: UInt32, dark: UInt32) {
        self.init(complicationHex: dark)
    }
}

private enum WatchWidgetColors {
    static let der = Color(light: 0x134E85, dark: 0x90CBFF)
    static let die = Color(light: 0x9A2050, dark: 0xFF9EC0)
    static let das = Color(light: 0x18602C, dark: 0x6FDC85)
}
