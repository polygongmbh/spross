import SwiftUI
import SprossKern

/// Which sheet a row's menu asks the row to raise. The menu itself lives inside a
/// `contextMenu` builder, which is no part of the view hierarchy a sheet can be
/// presented from — so the choice travels back out and the row presents it.
enum BoxRowSheet: String, Identifiable {
    case report, editReport, ownFrom, editOwnWord

    var id: String { rawValue }
}

/// Everything a learner might want to do to one word, as a long press on its Box row.
///
/// Only what applies, and in one fixed order: where it stands in the box first
/// (packing, sleep, forgetting), then what can be MADE of it, then what is wrong
/// with it, and deleting last because it is the one entry that cannot be taken back.
/// WHERE the word stands is the box's own ruling (`BoxBrowser.cardRowState`), asked
/// with packing offered — this menu can always pack a single word, whatever the row
/// behind it draws.
///
/// The session card's menu is deliberately NOT this one (`SessionView.cardMenu`):
/// a round is no place to reorganize the box.
struct BoxRowMenu: View {
    let model: AppModel
    let card: Card
    /// Raise a sheet the menu cannot present itself.
    let open: (BoxRowSheet) -> Void

    var body: some View {
        standing
        Button("box.card.ownFrom", systemImage: "doc.on.doc") { open(.ownFrom) }
        if model.isOwnWord(card.id) {
            Button("box.own.word.edit", systemImage: "pencil") { open(.editOwnWord) }
        }
        reporting
        if model.isOwnWord(card.id) {
            Button("box.own.word.remove", systemImage: "trash", role: .destructive) {
                model.removeOwnWord(card.id)
            }
        }
    }

    /// Packing, sleep and forgetting — the three things that move a word's standing
    /// rather than its content. A card the join does not hold offers none of them.
    @ViewBuilder
    private var standing: some View {
        let state = model.cardRowState(card.id, packOffered: true)
        switch onEnum(of: state) {
        case .packOffered:
            Button("box.card.pack", systemImage: "tray.and.arrow.down") {
                model.enqueueCard(card.id)
            }
        case .packed:
            Button("box.card.unpack", systemImage: "tray.and.arrow.up") {
                model.dequeue(cardID: card.id)
            }
        case .standing:
            Button("box.card.sleep", systemImage: "moon.zzz") {
                model.setSuspended(cardID: card.id, suspended: true)
            }
        case .sleeping:
            Button("box.card.wake", systemImage: "sun.max") {
                model.setSuspended(cardID: card.id, suspended: false)
            }
        case .plain:
            EmptyView()
        }
        if isScheduled(state) {
            Button("box.card.forget", systemImage: "arrow.counterclockwise") {
                model.forget(cardID: card.id)
            }
        }
    }

    /// Whether the box has ever asked this word — the one state where there is
    /// progress to drop.
    private func isScheduled(_ state: CardRowState) -> Bool {
        switch onEnum(of: state) {
        case .standing, .sleeping: return true
        case .packOffered, .packed, .plain: return false
        }
    }

    @ViewBuilder
    private var reporting: some View {
        if model.reportedIssue(for: card.id) == nil {
            Button("report.action", systemImage: "exclamationmark.bubble") { open(.report) }
        } else {
            Button("report.edit", systemImage: "text.bubble") { open(.editReport) }
            Button("report.dismiss", systemImage: "checkmark.bubble") {
                model.dismissReportedIssue(cardID: card.id)
            }
        }
    }
}
