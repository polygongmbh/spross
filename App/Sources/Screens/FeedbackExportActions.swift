import SwiftUI
import SprossKern

/// Sending the learner's own content back to whoever maintains the catalog: the
/// suggestions they wrote with only one half, and the problems they filed.
///
/// Two ways with the same lot — onto the clipboard, or into a mail. Both offer
/// "everything" or "only what is new", and both mark the copy taken, so the next
/// "new" means what it says.
struct FeedbackExportActions: View {
    let model: AppModel

    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(spacing: DL.Space.l) {
            scopedButton("feedback.copy", icon: "doc.on.doc") { onlyNew in
                UIPasteboard.general.string = model.reportText(onlyNew: onlyNew)
                model.markExported()
            }
            scopedButton("feedback.send", icon: "envelope") { onlyNew in
                guard let url = model.reportMailURL(onlyNew: onlyNew) else { return }
                openURL(url)
                model.markExported()
            }
        }
    }

    /// One action, offered over the whole lot or only what is new. Until a copy has
    /// ever been taken the two would be the same list, so it stays a plain button
    /// and asks nothing.
    @ViewBuilder
    private func scopedButton(_ title: LocalizedStringKey, icon: String,
                              run: @escaping (Bool) -> Void) -> some View {
        if model.hasExportedBefore {
            Menu {
                Button("feedback.scope.new") { run(true) }
                    .disabled(!model.hasFeedback(onlyNew: true))
                Button("feedback.scope.all") { run(false) }
            } label: {
                Label(title, systemImage: icon)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlAccent)
            }
        } else {
            Button { run(false) } label: {
                Label(title, systemImage: icon)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlAccent)
            }
        }
    }
}
