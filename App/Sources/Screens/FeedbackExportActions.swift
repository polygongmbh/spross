import SwiftUI
import SprossKern

/// What can be done with the learner's own content, in one row.
///
/// Three of the four send it back to whoever maintains the catalog: onto the clipboard, into
/// a mail, and emptying the outbox once it has gone. Both ways out offer everything, only
/// what is new, or only what the catalog is owed, and a full copy marks itself taken so the
/// next "new" means what it says.
///
/// The fourth goes the other way and reads the catalog against these words
/// (`CatalogMatchSheet`): a word written by hand because the catalog had none is one the
/// catalog may have grown since. It stands here rather than as a row of its own because it
/// is one more thing to do with the words above it, not a place to go.
struct FeedbackExportActions: View {
    let model: AppModel
    /// Raised by the section: a sheet is presented where the section owns it, not here.
    let checkCatalog: () -> Void

    @Environment(\.openURL) private var openURL
    @State private var confirmingClear = false

    var body: some View {
        // The three one-word actions share a line, evenly spread rather than stacked
        // against the left edge; the merge takes the line under them, since its name is
        // the one that cannot be said in a word. No spacing between the two: each line
        // is already a thumb's height of its own, and a gap on top of that reads as a
        // gulf rather than as two lines of one block.
        VStack(spacing: 0) {
            outbox
            merge
        }
    }

    /// Handing the learner's content on, and emptying it once it has gone.
    @ViewBuilder
    private var outbox: some View {
        if model.hasFeedback(onlyNew: false) {
            spread {
                column {
                    scopedButton("common.copy", icon: "doc.on.doc") { onlyNew, scope in
                        UIPasteboard.general.string = model.reportText(onlyNew: onlyNew, scope: scope)
                        model.markExported(scope: scope)
                    }
                }
                column {
                    scopedButton("report.export.send", icon: "envelope") { onlyNew, scope in
                        guard let url = model.reportMailURL(onlyNew: onlyNew, scope: scope) else { return }
                        openURL(url)
                        model.markExported(scope: scope)
                    }
                }
                if model.clearableCount > 0 { column { clearButton } }
            }
        }
    }

    /// Moving the learner's words onto the catalog words that caught up with them. It names
    /// what it compares because the other three say what they do in a verb and this one
    /// cannot — and both kinds are compared, the finished words and the suggestions alike.
    @ViewBuilder
    private var merge: some View {
        if !model.ownWordPairs.isEmpty || !model.suggestions.isEmpty {
            spread {
                column {
                    Button(action: checkCatalog) {
                        actionLabel("box.own.match.action", icon: "arrow.triangle.merge")
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// One line of actions, each taking an equal share of the panel — so a line of two
    /// sits balanced and a line of one sits centered, rather than piling up on the left.
    private func spread<Content: View>(@ViewBuilder _ actions: () -> Content) -> some View {
        HStack(spacing: Theme.spacing.md) { actions() }
    }

    /// An equal share of the row for one action. `Menu` sizes itself to its content
    /// no matter what frame is asked of it, so an HStack of bare menus and buttons
    /// packs to the left instead of spreading — a `Color` behind it takes the equal
    /// share instead, and the control only centers inside that.
    private func column<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        Color.clear
            .frame(maxWidth: .infinity, minHeight: 44)
            .overlay(content())
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
            .menuStyle(.borderlessButton)
        } else {
            Button { run(false, .everything) } label: { actionLabel(title, icon: icon) }
                .buttonStyle(.plain)
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
            Label("common.clear", systemImage: "trash")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.wrong)
                .linkAffordance()
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .buttonStyle(.plain)
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
            .linkAffordance()
            .lineLimit(1)
            .minimumScaleFactor(0.7)
    }
}
