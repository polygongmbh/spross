import SwiftUI

/// A settings section's title with the symbol that names its topic.
struct SettingTitle: View {
    private let title: LocalizedStringKey
    private let icon: String

    init(_ title: LocalizedStringKey, icon: String) {
        self.title = title
        self.icon = icon
    }

    var body: some View {
        Label {
            Text(title).foregroundStyle(Theme.colors.textPrimary)
        } icon: {
            Image(systemName: icon).foregroundStyle(Theme.colors.textSecondary)
        }
        .font(Theme.typography.headline)
    }
}
