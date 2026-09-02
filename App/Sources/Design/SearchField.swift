import SwiftUI

/// The one text field that is not an answer: a query line with its glass on the
/// left and, once there is anything to undo, a clear button on the right.
///
/// It takes the keyboard on appear. A search surface exists to be typed into,
/// and every other field in the app is reached by choosing to answer.
struct DLSearchField: View {
    let placeholder: LocalizedStringKey
    @Binding var text: String

    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: Theme.spacing.sm) {
            Image(systemName: "magnifyingglass")
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textSecondary)
                .accessibilityHidden(true)
            TextField(placeholder, text: $text)
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($focused)
            if !text.isEmpty {
                Button {
                    text = ""
                    focused = true
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(Theme.typography.body)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("box.search.clear")
            }
        }
        .padding(.horizontal, Theme.spacing.lg)
        .frame(minHeight: 52)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .strokeBorder(Theme.colors.separator, lineWidth: 1)
        )
        .onAppear { focused = true }
    }
}
