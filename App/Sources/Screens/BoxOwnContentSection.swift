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
/// Own words need no shelf of their own: they are queued the moment they are
/// written (`BoxEngine.addOwnWord`), so an area card offering to queue them would
/// say nothing. The studiable ones list as ordinary rows, keeping their standing
/// and their long-press menu. A SUGGESTION has no card at all — a word written in
/// one language joins nothing — so it lists in a block of its own, beside the
/// reports, naming the half the catalog owes. A NOTE gets a third block: it names no
/// word, so it suggests none either, and what it is about need not be in the catalog
/// at all.
///
/// The two actions take the whole lot two ways: onto the clipboard, or into a mail
/// to whoever maintains the catalog (`FeedbackExportActions`).
struct BoxOwnContentSection: View {
    let model: AppModel

    /// The one screen this section can raise; a context menu is no place to
    /// present one from, so the rows hand the choice up here.
    // why: internal, not private — the rows in +Lists.swift raise it.
    @State var sheet: Sheet?

    enum Sheet: Identifiable {
        case writing
        case editing(OwnWord)
        /// A suggestion or a note, which is free text and is edited as such.
        case entry(OwnWord)
        case reporting(Card)
        case talking
        case matching

        /// What the action that opens it is called: a word form rewrites a WORD, the
        /// entry sheet an entry that names half of one or none at all.
        var editLabel: LocalizedStringKey {
            if case .entry = self { return "box.own.entry.edit" }
            return "box.own.word.edit"
        }

        var id: String {
            switch self {
            case .writing: return "writing"
            case .talking: return "talking"
            case .matching: return "matching"
            case .editing(let word): return "edit:\(word.id)"
            case .entry(let word): return "entry:\(word.id)"
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
            .accessibilityLabel("a11y.box.own.word.addAction")
        }
    }

    private var card: some View {
        // The blocks in the order the section is read: what the learner keeps, then
        // what the catalog owes them, then the two ways to tell it so.
        var blocks: [Block] = []
        if model.hasBriefing { blocks.append(.briefing) }
        if !model.ownWordPairs.isEmpty { blocks.append(.pairs) }
        if !model.suggestions.isEmpty { blocks.append(.suggestions) }
        if !model.remarks.isEmpty { blocks.append(.notes) }
        if !reports.isEmpty { blocks.append(.reports) }
        // why: an empty complaints box is furniture — the actions appear once there is
        // something for them to carry, and the catalog check once there is a word to check.
        if model.hasFeedback(onlyNew: false) || !checkableWords.isEmpty {
            blocks.append(.actions)
        }

        return VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            ForEach(Array(blocks.enumerated()), id: \.element) { index, block in
                if index > 0 { separator }
                blockBody(block)
            }
        }
        .panelSurface()
        .cardShadow()
    }

    /// What the section's card can be made of, top to bottom. Only the ones with
    /// something in them are drawn, and a separator sits between whichever remain.
    private enum Block: Hashable {
        case briefing, pairs, suggestions, notes, reports, actions
    }

    @ViewBuilder
    private func blockBody(_ block: Block) -> some View {
        switch block {
        case .briefing: briefingRow
        case .pairs: pairList
        case .suggestions: suggestionList
        case .notes: noteList
        case .reports: reportList
        case .actions:
            FeedbackExportActions(model: model) { sheet = .matching }
        }
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

    /// The words the catalog could have caught up with. A note names none, so it is not
    /// one of them.
    private var checkableWords: [OwnWord] { model.ownWordPairs + model.suggestions }

    func blockTitle(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
    }

    /// The filed problems that have a word to name, catalog-side only
    /// (`AppModel.catalogReports`).
    // why: internal, not private — the report list in +Lists.swift reads it.
    var reports: [(issue: ReportedIssue, card: Card)] {
        model.catalogReports.compactMap { issue in
            model.card(issue.cardId).map { (issue, $0) }
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
        case .entry(let word):
            OwnEntrySheet(model: model, entry: word)
        case .talking:
            BriefingSheet(model: model)
        case .matching:
            CatalogMatchSheet(model: model)
        case .reporting(let card):
            ReportIssueSheet(model: model, card: card, learnerInput: "")
        }
    }
}
