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
        HStack(spacing: DL.Space.s) {
            Image(systemName: "magnifyingglass")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .accessibilityHidden(true)
            TextField(placeholder, text: $text)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
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
                        .font(DL.Fonts.body)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("search.clear")
            }
        }
        .padding(.horizontal, DL.Space.l)
        .frame(minHeight: 52)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .strokeBorder(Color.dlSeparator, lineWidth: 1)
        )
        .onAppear { focused = true }
    }
}
