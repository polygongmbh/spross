import SwiftUI

/// A line to write a name on: the recessed field the language pickers stand in
/// (`BoxSettingsSection`), with a caret in it instead of a menu behind it.
///
/// Both places that ask for the name take it — the box's settings and the first run —
/// so the question cannot come to look like two different questions.
struct NameField: View {
    let placeholder: LocalizedStringKey
    @Binding var text: String

    var body: some View {
        TextField(placeholder, text: $text)
            .font(Theme.typography.body)
            .foregroundStyle(Theme.colors.textPrimary)
            // why: the opposite of the own-word form, where the automatic capital
            // misspells the word being stored — a name is spelled with one, and no
            // dictionary knows better how it goes on from there.
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled()
            .submitLabel(.done)
            .padding(.vertical, Theme.spacing.sm)
            .padding(.horizontal, Theme.spacing.md)
            .frame(minHeight: 44, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surfaceTint)
            )
    }
}
