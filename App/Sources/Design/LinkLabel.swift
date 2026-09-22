import SwiftUI

/// The link-styled tap target used across Settings, Credits and the box's own-content
/// row: tinted text, an optional icon, and the underline iOS's own "Show Borders"
/// accessibility setting means to add. Drawn by hand rather than left to the system,
/// because SwiftUI's automatic support for that setting treats `Button` and `Menu`
/// labels of identical shape inconsistently — one reading of the trait, applied here
/// once, keeps every such label in step with the setting and with each other,
/// whether it sits in a `Button`, a `Menu`, or a `Link`.
struct LinkLabel: View {
    @Environment(\.accessibilityShowButtonShapes) private var showButtonShapes
    private let text: Text
    private let icon: String?
    private let color: Color
    private let font: Font?

    init(_ title: LocalizedStringKey, icon: String? = nil,
         color: Color = Theme.colors.accent, font: Font? = nil) {
        self.text = Text(title)
        self.icon = icon
        self.color = color
        self.font = font
    }

    init(verbatim: String, icon: String? = nil,
         color: Color = Theme.colors.accent, font: Font? = nil) {
        self.text = Text(verbatim: verbatim)
        self.icon = icon
        self.color = color
        self.font = font
    }

    var body: some View {
        Group {
            if let icon {
                Label { text } icon: { Image(systemName: icon) }
            } else {
                text
            }
        }
        .font(font)
        .foregroundStyle(color)
        .underline(showButtonShapes)
    }
}
