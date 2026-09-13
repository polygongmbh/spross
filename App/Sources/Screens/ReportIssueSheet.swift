import SwiftUI
import SprossKern

/// Saying what is wrong with a word: a wrong translation, a synonym the catalog
/// ought to accept, a prompt that reads badly.
///
/// The comment is optional and the sheet says why — for the commonest report there
/// is nothing to write. What the learner typed rides along on its own and is shown
/// rather than offered: the answer the catalog rejected IS the report in that case,
/// and a box they have to tick is one they will not.
///
/// Filing changes nothing about the schedule. Putting the word to sleep is the
/// menu's OTHER entry, deliberately not a switch in here.
///
/// Reopened on a report already on file it arrives carrying what was written — the
/// comment AND the answer it rode in with — and sending replaces it: one report per
/// card, never a second one beside the first.
/// Withdrawing is in HERE for the same reason: the menu that opens this form offers
/// one entry, not a fork between editing and dropping, and a learner deciding which
/// they want is already reading the report.
struct ReportIssueSheet: View {
    let model: AppModel
    let card: Card
    /// What the learner had typed when they opened this; empty on recognition and
    /// on a row, where nothing was being answered.
    let learnerInput: String

    @Environment(\.dismiss) private var dismiss
    @State private var comment = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                    pair
                    commentField
                    if !carriedInput.isEmpty {
                        typedLine
                    }
                    Text("report.explainer")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                    if onFile {
                        withdraw
                    }
                }
                .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle("report.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("report.send") { file() }
                }
            }
        }
        .tint(Theme.colors.accent)
        .onAppear {
            // why: read off the report itself rather than passed in — every caller
            // opens the same form, and one that forgot to hand the comment over
            // would silently blank what the learner wrote.
            comment = onFileIssue?.comment ?? ""
            focused = true
        }
    }

    /// The report this form opened on, when there was one already filed — the one
    /// state where there is something to withdraw, and where what it already carries
    /// is what the form starts from.
    private var onFileIssue: ReportedIssue? { model.reportedIssue(for: card.id) }

    private var onFile: Bool { onFileIssue != nil }

    /// The answer the report travels with: what stands in the field now, or — filing
    /// again from a row, where nothing was being answered — what the first report
    /// already rode in with. Editing a report never drops it.
    private var carriedInput: String {
        learnerInput.isEmpty ? (onFileIssue?.learnerInput ?? "") : learnerInput
    }

    /// Dropping the report, last and set apart: it is the only thing in the form
    /// that cannot be taken back by editing again.
    private var withdraw: some View {
        Button(role: .destructive) {
            model.dismissReportedIssue(cardID: card.id)
            dismiss()
        } label: {
            Label("report.dismiss", systemImage: "checkmark.bubble")
                .font(Theme.typography.body)
        }
    }

    /// The word as it stood, so the report names what it is about without the
    /// learner having to retype it.
    private var pair: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(card.target.text)
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
            Text(card.source.text)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }

    private var commentField: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text("report.comment")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            TextField(text: $comment, axis: .vertical) { EmptyView() }
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(3...6)
                .focused($focused)
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

    private var typedLine: some View {
        HStack(spacing: Theme.spacing.xs) {
            Text("report.typed")
            Text(verbatim: carriedInput)
                .foregroundStyle(Theme.colors.textPrimary)
        }
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
    }

    private func file() {
        model.reportIssue(cardID: card.id, comment: comment, learnerInput: carriedInput)
        dismiss()
    }
}
