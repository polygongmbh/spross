import SwiftUI
import SprossKern

/// Rewriting an entry the box holds no card for: a suggestion still waiting for its
/// other half, or a note that names no word at all.
///
/// Neither is a word pair, and neither is edited as one. What the learner wrote is free
/// text — a half-remembered form, a question, a sentence about the catalog — so it is
/// written back into boxes that grow with what goes in them, the half in the language it
/// was written in and the note under it.
///
/// The word form's second language field is deliberately not here: an entry has one side
/// by definition, and an empty field beside the written one is how that half gets cleared
/// without the learner meaning to (`OwnWordFormView` is what a finished pair opens).
///
/// The entry keeps its id, and with it anything filed against it
/// (`BoxEngine.updateOwnWord`).
struct OwnEntrySheet: View {
    let model: AppModel
    let entry: OwnWord

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var comment = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                    if let language {
                        box(label(language), text: $text, lines: 1...4)
                    }
                    box("box.own.word.comment", text: $comment, lines: 3...6)
                    Text(explainer)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
                .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle("box.own.entry.edit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("box.own.word.save") { save() }
                        .disabled(!hasAnything)
                }
            }
        }
        .tint(Theme.colors.accent)
        .onAppear {
            text = language.flatMap { entry.texts[$0] } ?? ""
            comment = entry.comment ?? ""
        }
    }

    /// The one language the entry is written in — none at all for a note, which is why
    /// a note is edited as its comment and nothing else.
    private var language: String? { entry.languages.first }

    private var hasAnything: Bool { written(text) || written(comment) }

    private var explainer: LocalizedStringKey {
        written(text) ? "box.own.word.explainer.suggestion" : "box.own.word.explainer.remark"
    }

    private func written(_ text: String) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// The language's flag in front of its own name for itself, as the word form asks it.
    private func label(_ code: String) -> LocalizedStringKey {
        let name = LanguageNames.display(code, catalog: model.catalog)
        let flag = model.languageInfo(code)?.flag
        return "box.own.word.inLanguage \(flag.map { "\($0) \(name)" } ?? name)"
    }

    private func box(
        _ label: LocalizedStringKey, text: Binding<String>, lines: ClosedRange<Int>
    ) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text(label)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            TextField(text: text, axis: .vertical) { EmptyView() }
                .lineLimit(lines)
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
                .padding(Theme.spacing.lg)
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                        .fill(Theme.colors.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                        .strokeBorder(Theme.colors.separator, lineWidth: 1)
                )
        }
    }

    private func save() {
        model.updateOwnEntry(entry, text: text, comment: comment)
        dismiss()
    }
}
