import SwiftUI
import SprossKern

/// Everything in the box that came from the learner rather than from the catalog:
/// the words they wrote themselves, and the problems they filed against words they
/// did not.
///
/// It closes the Box, after the shelves and above the settings, and unlike a shelf
/// it is ALWAYS there — it carries the add button, which is the one way to write a
/// word that does not start from a search that found nothing.
///
/// Own words need no shelf of their own: they are packed the moment they are
/// written (`BoxEngine.addOwnWord`), so an area card offering to pack them would
/// say nothing. The studiable ones list as ordinary rows, keeping their standing
/// and their long-press menu; a SUGGESTION has no card at all — a word written in
/// one language joins nothing — so it lists as itself, naming the half the catalog
/// owes.
///
/// The two actions take the whole lot two ways: onto the clipboard, or into a mail
/// to whoever maintains the catalog (`FeedbackExportActions`).
struct BoxOwnContentSection: View {
    let model: AppModel

    /// The one screen this section can raise; a context menu is no place to
    /// present one from, so the rows hand the choice up here.
    @State private var sheet: Sheet?

    private enum Sheet: Identifiable {
        case writing
        case editing(OwnWord)
        case reporting(Card)
        case talking

        var id: String {
            switch self {
            case .writing: return "writing"
            case .talking: return "talking"
            case .editing(let word): return "edit:\(word.id)"
            case .reporting(let card): return "report:\(card.id)"
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            header
            if model.hasBriefing || !model.ownWords.isEmpty || !reports.isEmpty
                || model.hasFeedback(onlyNew: false) {
                card
            }
        }
        .sheet(item: $sheet) { sheetBody($0) }
    }

    private var header: some View {
        HStack(spacing: Theme.spacing.md) {
            Text("box.own.title")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
            Spacer(minLength: Theme.spacing.sm)
            Button {
                sheet = .writing
            } label: {
                Image(systemName: "plus")
            }
            .buttonStyle(IconButtonStyle())
            .accessibilityLabel("box.own.word.addAction")
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            if model.hasBriefing {
                briefingRow
            }
            if !model.ownWords.isEmpty {
                if model.hasBriefing { separator }
                wordList
            }
            if !reports.isEmpty {
                if model.hasBriefing || !model.ownWords.isEmpty { separator }
                reportList
            }
            // why: an empty complaints box is furniture — the actions appear once
            // there is something for them to carry, exactly as they always have.
            if model.hasFeedback(onlyNew: false) {
                if model.hasBriefing || !model.ownWords.isEmpty || !reports.isEmpty { separator }
                FeedbackExportActions(model: model)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.spacing.lg)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
    }

    private var separator: some View { Divider().overlay(Theme.colors.separator) }

    /// The box handed to a conversation the app does not host (`BriefingSheet`).
    /// It leads the section because it is the one entry here that goes OUT and comes
    /// back: the words below it are what a conversation writes home.
    private var briefingRow: some View {
        Button {
            sheet = .talking
        } label: {
            HStack(spacing: Theme.spacing.md) {
                Image(systemName: "bubble.left.and.text.bubble.right")
                    .foregroundStyle(Theme.colors.accent)
                VStack(alignment: .leading, spacing: 0) {
                    Text("briefing.title")
                        .font(Theme.typography.body)
                        .foregroundStyle(Theme.colors.textPrimary)
                    Text("briefing.row.subtitle")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
        }
        .buttonStyle(.plain)
    }

    private func blockTitle(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
    }

    // MARK: - The learner's own words

    private var wordList: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            blockTitle("box.own.shelf")
            ForEach(model.ownWords, id: \.id) { word in
                if let card = model.card(word.id) {
                    BoxCardRow(model: model, card: card)
                } else {
                    suggestionRow(word)
                }
            }
        }
    }

    private func suggestionRow(_ word: OwnWord) -> some View {
        HStack(spacing: Theme.spacing.md) {
            Text(verbatim: word.emoji ?? OwnWords.shared.EMOJI)
                .font(.title3)
                .accessibilityHidden(true)
            Text(verbatim: model.suggestionText(word))
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(1)
            Spacer(minLength: Theme.spacing.sm)
            // why: it is not a shortcoming of the word, it is the whole point of the
            // entry — the half that is missing is what the catalog owes.
            Text("box.own.word.needsTranslation")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
        .padding(.horizontal, Theme.spacing.md)
        .padding(.vertical, Theme.spacing.xs + 2)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .fill(Theme.colors.surfaceTint)
        )
        // A menu of its own, and a short one: with no card behind it there is
        // nothing to pack, forget or report — only the two halves to fix or drop.
        .contextMenu {
            Button("box.own.word.edit", systemImage: "pencil") { sheet = .editing(word) }
            Button("box.own.word.remove", systemImage: "trash", role: .destructive) {
                model.removeOwnWord(word.id)
            }
        }
    }

    // MARK: - What was reported

    /// The filed problems that have a word to name, catalog-side only
    /// (`AppModel.catalogReports`).
    private var reports: [(issue: ReportedIssue, card: Card)] {
        model.catalogReports.compactMap { issue in
            model.card(issue.cardId).map { (issue, $0) }
        }
    }

    private var reportList: some View {
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
                .accessibilityLabel("report.reported")
            VStack(alignment: .leading, spacing: 2) {
                // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
                Text(verbatim: "\(card.target.text) → \(card.source.text)")
                    .font(Theme.typography.body)
                    .foregroundStyle(Theme.colors.textPrimary)
                    .lineLimit(2)
                if let comment = issue.comment, !comment.isEmpty {
                    Text(verbatim: comment)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                        .lineLimit(3)
                }
            }
            Spacer(minLength: Theme.spacing.sm)
        }
        .padding(.horizontal, Theme.spacing.md)
        .padding(.vertical, Theme.spacing.xs + 2)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .fill(Theme.colors.surfaceTint)
        )
        .contextMenu {
            // One entry: withdrawing lives inside the form the edit opens.
            Button("report.edit", systemImage: "text.bubble") { sheet = .reporting(card) }
        }
    }

    // MARK: - Sheets

    @ViewBuilder
    private func sheetBody(_ which: Sheet) -> some View {
        switch which {
        case .writing:
            OwnWordFormView(model: model, seed: .query(""))
        case .editing(let word):
            OwnWordFormView(model: model, seed: .editing(word))
        case .talking:
            BriefingSheet(model: model)
        case .reporting(let card):
            ReportIssueSheet(model: model, card: card, learnerInput: "",
                             filed: model.reportedIssue(for: card.id)?.comment ?? "")
        }
    }
}
