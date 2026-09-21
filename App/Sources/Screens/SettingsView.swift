import SwiftUI

/// The third section: the pair being learnt, whether words are read aloud, the
/// backup, the one destructive door, and the way to who spoke the recordings.
///
/// What stands here is `BoxSettingsSection`'s; this screen only gives it the page.
struct SettingsView: View {
    let model: AppModel

    var body: some View {
        ScrollView {
            BoxSettingsSection(model: model)
                .padding(Theme.spacing.xl)
        }
        .background(Theme.colors.background.ignoresSafeArea())
        .toolbarBackground(.hidden, for: .navigationBar)
    }
}
