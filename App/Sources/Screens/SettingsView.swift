import SwiftUI

/// Settings on their own screen, reached from the gear in Heute's bar.
///
/// They used to ride at the bottom of the Box screen, under every area in the
/// catalog — the only place they could go when the box was a screen. The box
/// is Heute's forest now, so the settings own the push the box gave up.
struct SettingsView: View {
    let model: AppModel

    var body: some View {
        ScrollView {
            BoxSettingsSection(model: model)
                .padding(DL.Space.xl)
        }
        .background(Color.dlBackground.ignoresSafeArea())
        .toolbarBackground(.hidden, for: .navigationBar)
    }
}
