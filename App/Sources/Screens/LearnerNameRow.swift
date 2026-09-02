import SwiftUI

/// What the greeting calls the learner. Free text, and empty is an answer of its
/// own: clearing the field takes the name away again, and the greeting has its own
/// wording for a learner it cannot name.
///
/// A row of its own, not a section of `BoxSettingsSection`, because of where the
/// draft is read from — see below.
struct LearnerNameRow: View {
    let model: AppModel

    /// What is typed, spaces and all — the store is what trims, so a space before a
    /// second name does not vanish under the finger that typed it.
    ///
    /// why: seeded from the store, never from `model.learnerName` — a read of the
    /// observable while the Box screen builds its body would enroll the whole screen
    /// in every keystroke's write, and the area list (a kern query per area) would
    /// rebuild letter by letter.
    @State private var nameDraft = LearnerProfile.name ?? ""

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text("settings.name.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            NameField(placeholder: "settings.name.placeholder", text: $nameDraft)
                .onChange(of: nameDraft) { _, typed in model.setLearnerName(typed) }
            Text("settings.name.hint")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }
}
