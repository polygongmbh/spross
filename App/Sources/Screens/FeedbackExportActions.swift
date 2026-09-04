import SwiftUI
import SprossKern

/// Sending the learner's own content back to whoever maintains the catalog: the
/// suggestions they wrote with only one half, and the problems they filed.
///
/// Two ways with the same lot — onto the clipboard, or into a mail. Both offer everything,
/// only what is new, or only what the catalog is owed, and a full copy marks itself taken so
/// the next "new" means what it says. Both also offer to empty the outbox on the way out,
/// since handing it over is the whole reason those entries were kept.
struct FeedbackExportActions: View {
    let model: AppModel

    @Environment(\.openURL) private var openURL
    @State private var confirmingClear = false

    var body: some View {
        HStack(spacing: Theme.spacing.lg) {
            scopedButton("report.export.copy", icon: "doc.on.doc") { onlyNew, scope in
                UIPasteboard.general.string = model.reportText(onlyNew: onlyNew, scope: scope)
                model.markExported(scope: scope)
            }
            scopedButton("report.export.send", icon: "envelope") { onlyNew, scope in
                guard let url = model.reportMailURL(onlyNew: onlyNew, scope: scope) else { return }
                openURL(url)
                model.markExported(scope: scope)
            }
            if model.clearableCount > 0 { clearButton }
        }
    }

    /// One action, offered over the whole lot, over what is new, over what the catalog is
    /// owed, or over the whole lot with the outbox emptied behind it. It stays a plain
    /// button while it has only the one thing to offer — before any copy has been taken
    /// "new" is the same list as "everything", with no word pair written so is the outbox,
    /// and with nothing clearable the last says nothing.
    @ViewBuilder
    private func scopedButton(_ title: LocalizedStringKey, icon: String,
                              run: @escaping (Bool, FeedbackScope) -> Void) -> some View {
        let clearable = model.clearableCount > 0
        if model.hasExportedBefore || clearable {
            Menu {
                if model.hasExportedBefore {
                    Button("report.export.scope.new") { run(true, .everything) }
                        .disabled(!model.hasFeedback(onlyNew: true))
                }
                if offersOutbox {
                    Button("report.export.scope.outbox") { run(false, .outbox) }
                }
                Button("report.export.scope.all") { run(false, .everything) }
                if clearable {
                    // why: the lot has just gone to the clipboard or into a draft, so
                    // there is nothing left to lose and nothing to ask about.
                    Button("report.export.scope.allClear", role: .destructive) {
                        run(false, .everything)
                        model.clearFeedback()
                    }
                }
            } label: {
                actionLabel(title, icon: icon)
            }
        } else {
            Button { run(false, .everything) } label: { actionLabel(title, icon: icon) }
        }
    }

    /// Whether the narrower offer says anything the wider one does not: with no word pair
    /// written, the outbox IS the lot, and two entries carrying the same text is a choice
    /// the learner has to read twice to find there is none.
    private var offersOutbox: Bool {
        model.hasFeedback(onlyNew: false, scope: .outbox) && !model.ownWordPairs.isEmpty
    }

    /// Emptying the outbox on its own, with nothing copied first — the one place in
    /// this section that can lose something unread, so it asks.
    private var clearButton: some View {
        Button(role: .destructive) {
            confirmingClear = true
        } label: {
            Label("report.export.clear", systemImage: "trash")
                .font(Theme.typography.subheadline)
        }
        .confirmationDialog("report.export.clear.confirm \(model.clearableCount)",
                            isPresented: $confirmingClear, titleVisibility: .visible) {
            Button("common.clear", role: .destructive) { model.clearFeedback() }
            Button("common.cancel", role: .cancel) {}
        }
    }

    private func actionLabel(_ title: LocalizedStringKey, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(Theme.typography.subheadline)
            .foregroundStyle(Theme.colors.accent)
    }
}
