import SwiftUI
import SprossKern

/// Sending the learner's own content back to whoever maintains the catalog: the
/// suggestions they wrote with only one half, and the problems they filed.
///
/// Two ways with the same lot — onto the clipboard, or into a mail. Both offer
/// "everything" or "only what is new", and both mark the copy taken, so the next
/// "new" means what it says. Both also offer to empty the outbox on the way out,
/// since handing it over is the whole reason those entries were kept.
struct FeedbackExportActions: View {
    let model: AppModel

    @Environment(\.openURL) private var openURL
    @State private var confirmingClear = false

    var body: some View {
        HStack(spacing: DL.Space.l) {
            scopedButton("report.export.copy", icon: "doc.on.doc") { onlyNew in
                UIPasteboard.general.string = model.reportText(onlyNew: onlyNew)
                model.markExported()
            }
            scopedButton("report.export.send", icon: "envelope") { onlyNew in
                guard let url = model.reportMailURL(onlyNew: onlyNew) else { return }
                openURL(url)
                model.markExported()
            }
            if model.clearableCount > 0 { clearButton }
        }
    }

    /// One action, offered over the whole lot, only over what is new, or over the
    /// whole lot with the outbox emptied behind it. It stays a plain button while it
    /// has only the one thing to offer — before any copy has been taken the first two
    /// would be the same list, and with nothing clearable the third says nothing.
    @ViewBuilder
    private func scopedButton(_ title: LocalizedStringKey, icon: String,
                              run: @escaping (Bool) -> Void) -> some View {
        let clearable = model.clearableCount > 0
        if model.hasExportedBefore || clearable {
            Menu {
                if model.hasExportedBefore {
                    Button("report.export.scope.new") { run(true) }
                        .disabled(!model.hasFeedback(onlyNew: true))
                }
                Button("report.export.scope.all") { run(false) }
                if clearable {
                    // why: the lot has just gone to the clipboard or into a draft, so
                    // there is nothing left to lose and nothing to ask about.
                    Button("report.export.scope.allClear", role: .destructive) {
                        run(false)
                        model.clearFeedback()
                    }
                }
            } label: {
                actionLabel(title, icon: icon)
            }
        } else {
            Button { run(false) } label: { actionLabel(title, icon: icon) }
        }
    }

    /// Emptying the outbox on its own, with nothing copied first — the one place in
    /// this section that can lose something unread, so it asks.
    private var clearButton: some View {
        Button(role: .destructive) {
            confirmingClear = true
        } label: {
            Label("report.export.clear", systemImage: "trash")
                .font(DL.Fonts.subheadline)
        }
        .confirmationDialog("report.export.clear.confirm \(model.clearableCount)",
                            isPresented: $confirmingClear, titleVisibility: .visible) {
            Button("common.clear", role: .destructive) { model.clearFeedback() }
            Button("common.cancel", role: .cancel) {}
        }
    }

    private func actionLabel(_ title: LocalizedStringKey, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(DL.Fonts.subheadline)
            .foregroundStyle(Color.dlAccent)
    }
}
