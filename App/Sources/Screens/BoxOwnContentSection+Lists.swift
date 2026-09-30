import SwiftUI
import SprossKern

/// The section's lists: the learner's own words, the half-words and notes, and the reports.
extension BoxOwnContentSection {

    // MARK: - The learner's own words

    /// The words written in two languages or more. Those this profile can pair ARE cards
    /// and read as ones — badge, 💤, 🚩 and the full menu — so nothing there says they were
    /// hand-written. One it cannot pair has no card to read off and lists as its own two
    /// halves instead, in the section it has always belonged to: the learner finished it,
    /// and a changed known language does not unfinish it (`OwnWord.isPair`, kern §6).
    var pairList: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            blockTitle("box.own.shelf")
            ForEach(model.ownWordPairs, id: \.id) { word in
                if let card = model.card(word.id) {
                    BoxCardRow(model: model, card: card)
                } else {
                    otherPairRow(word)
                }
            }
        }
    }

    /// A finished word the open pair cannot ask. The tail is the FLAG of the language it
    /// carries that this pair does not name, which is the whole of why it stands here
    /// rather than as a card — and a flag says it in the space a sentence would not fit.
    private func otherPairRow(_ word: OwnWord) -> some View {
        entryRow(word, lines: 1, said: word.comment, tail: model.otherPairFlags(word),
                 copying: model.otherPairText(word), opening: .editing(word)) {
            Text(verbatim: model.otherPairText(word))
        }
    }

    /// The words still carrying one half. They stand apart from the pairs above rather
    /// than among them: a suggestion has no standing to compare, and what it waits for is
    /// what the reports below it are waiting for.
    var suggestionList: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            blockTitle("box.own.suggestions")
            ForEach(model.suggestions, id: \.id) { word in
                suggestionRow(word)
            }
        }
    }

    private func suggestionRow(_ word: OwnWord) -> some View {
        entryRow(word, lines: 1, said: word.comment,
                 // why: a missing half is not a shortcoming of the word, it is the whole
                 // point of the entry — it is what the catalog owes.
                 tail: Text("box.own.word.needsTranslation"),
                 copying: model.suggestionText(word), opening: .entry(word)) {
            Text(verbatim: model.suggestionText(word))
        }
    }

    /// The notes that name no word. They suggest nothing and owe nothing — what they are
    /// about may be no word in the catalog at all — so they list apart from the words the
    /// catalog owes an answer to, with no tail saying what is missing from them.
    var noteList: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            blockTitle("box.own.notes")
            ForEach(model.remarks, id: \.id) { note in
                entryRow(note, lines: 3, said: nil, tail: nil,
                         copying: note.comment ?? "", opening: .entry(note)) {
                    Text(verbatim: note.comment ?? "")
                }
            }
        }
    }

    /// One entry with no card behind it. `said` is the note under the line where the
    /// entry has one, `tail` what the row has left to say about it — what the catalog
    /// still owes, or the language this pair cannot read it in.
    private func entryRow<Lead: View>(
        _ word: OwnWord, lines: Int, said: String?, tail: Text?,
        copying: String, opening: Sheet,
        @ViewBuilder lead: () -> Lead
    ) -> some View {
        HStack(spacing: Theme.spacing.md) {
            Text(verbatim: word.emoji ?? OwnWords.shared.EMOJI)
                .font(.title3)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                lead()
                    .font(Theme.typography.body)
                    .foregroundStyle(Theme.colors.textPrimary)
                    .lineLimit(lines)
                SaidLine(said)
            }
            Spacer(minLength: Theme.spacing.sm)
            if let tail {
                tail
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
        }
        .entrySurface()
        // A menu of its own, and a short one: with no card behind it there is nothing to
        // queue, forget or report — the entry to fix, what it says to take elsewhere, and
        // the way to drop it.
        .contextMenu {
            Button(opening.editLabel, systemImage: "pencil") { sheet = opening }
            Button("common.copy", systemImage: "doc.on.doc") {
                UIPasteboard.general.string = copying
            }
            Button("box.own.word.remove", systemImage: "trash", role: .destructive) {
                model.removeOwnWord(word.id)
            }
        }
    }

    // MARK: - What was reported

    var reportList: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            blockTitle("box.own.reported")
            ForEach(reports, id: \.issue.cardId) { report in
                reportRow(report.issue, card: report.card)
            }
        }
    }

    private func reportRow(_ issue: ReportedIssue, card: Card) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.md) {
            Text(verbatim: "🚩")
                .accessibilityLabel("a11y.report.reported")
            VStack(alignment: .leading, spacing: 2) {
                // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
                Text(verbatim: "\(card.target.text) → \(card.source.text)")
                    .font(Theme.typography.body)
                    .foregroundStyle(Theme.colors.textPrimary)
                    .lineLimit(2)
                SaidLine(issue.comment)
            }
            Spacer(minLength: Theme.spacing.sm)
        }
        .entrySurface()
        .contextMenu {
            // One entry: withdrawing lives inside the form the edit opens.
            Button("report.edit", systemImage: "text.bubble") { sheet = .reporting(card) }
        }
    }
}

/// The note under an entry's line, where it has one.
private struct SaidLine: View {
    let said: String?

    init(_ said: String?) { self.said = said }

    var body: some View {
        if let said, !said.isEmpty {
            Text(verbatim: said)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .lineLimit(3)
        }
    }
}

private extension View {
    /// The tinted slab every row with no card behind it stands on.
    func entrySurface() -> some View {
        padding(.horizontal, Theme.spacing.md)
            .padding(.vertical, Theme.spacing.xs + 2)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                    .fill(Theme.colors.surfaceTint)
            )
    }
}
