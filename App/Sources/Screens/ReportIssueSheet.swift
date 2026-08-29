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
struct ReportIssueSheet: View {
    let model: AppModel
    let card: Card
    /// What the learner had typed when they opened this; empty on recognition.
    let learnerInput: String

    @Environment(\.dismiss) private var dismiss
    @State private var comment = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DL.Space.xl) {
                    pair
                    commentField
                    if !learnerInput.isEmpty {
                        typedLine
                    }
                    Text("report.explainer")
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                .padding(DL.Space.xl)
            }
            .background(Color.dlBackground.ignoresSafeArea())
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
        .tint(.dlAccent)
        .onAppear { focused = true }
    }

    /// The word as it stood, so the report names what it is about without the
    /// learner having to retype it.
    private var pair: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(card.target.text)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
            Text(card.source.text)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var commentField: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("report.comment")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            TextField(text: $comment, axis: .vertical) { EmptyView() }
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(3...6)
                .focused($focused)
                .padding(DL.Space.l)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                        .fill(Color.dlSurface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                        .strokeBorder(Color.dlSeparator, lineWidth: 1)
                )
        }
    }

    private var typedLine: some View {
        HStack(spacing: DL.Space.xs) {
            Text("report.typed")
            Text(verbatim: learnerInput)
                .foregroundStyle(Color.dlTextPrimary)
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
    }

    private func file() {
        model.reportIssue(cardID: card.id, comment: comment, learnerInput: learnerInput)
        dismiss()
    }
}
