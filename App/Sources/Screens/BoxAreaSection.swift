import SwiftUI
import SprossKern

// MARK: - Fold chevron

/// The one fold affordance on this screen: groups and area cards use it,
/// so both read as the same gesture.
struct FoldChevron: View {
    let open: Bool

    var body: some View {
        Image(systemName: "chevron.right")
            .font(.caption2)
            .rotationEffect(.degrees(open ? 90 : 0))
    }
}

// MARK: - Area section

/// One area as a single foldable card: name, progress bar and phrase counts
/// in the header, its words underneath once opened. Queuing sits beside the
/// header as its own tap target — it must never cost the fold.
struct BoxAreaSection: View {
    let model: AppModel
    let area: String
    @Binding var expanded: Bool

    var body: some View {
        let stats = model.areaStats(area)

        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: Theme.spacing.md) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
                } label: {
                    header(stats)
                }
                .buttonStyle(.plain)
                queueControl
            }
            if expanded {
                cardList
                    .padding(.top, Theme.spacing.md)
            }
        }
        .panelSurface()
        .cardShadow()
    }

    private func header(_ stats: AreaStatistics?) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.sm) {
            AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                     subtitle: model.areaSubtitle(area),
                     progress: stats?.progress ?? .empty,
                     lockedPhrases: stats?.lockedPhrases ?? 0,
                     hideProgress: model.shelfControl(area: area).hidesProgress)
            FoldChevron(open: expanded)
                .foregroundStyle(Theme.colors.textSecondary)
                .padding(.top, Theme.spacing.sm)
        }
        .contentShape(Rectangle())
    }

    /// The shelf's own control as kern names it (`ShelfControl`): queue, take the batch
    /// back out, or a mark — jade once settled, green otherwise.
    /// Icon-only, so the header stays one line tall; the spoken label names the area.
    @ViewBuilder
    private var queueControl: some View {
        let control = model.shelfControl(area: area)
        if control == .queue {
            QueueButton(direction: .in, label: "a11y.box.shelf.queue \(model.areaTitle(area))") {
                model.queueArea(area)
            }
        } else if control == .unqueue {
            QueueButton(direction: .out, label: "a11y.box.shelf.unqueue \(model.areaTitle(area))") {
                model.unqueueArea(area)
            }
        } else {
            Image(systemName: "checkmark.circle.fill")
                .font(Theme.typography.headline)
                .foregroundStyle(control == .settled ? Theme.colors.settled : Theme.colors.success)
                .frame(width: 40, height: 40)
                .accessibilityHidden(true) // why: a minor status mark; the bar already says the area is done
        }
    }

    private var cardList: some View {
        VStack(spacing: Theme.spacing.sm) {
            ForEach(model.cards(inArea: area)) { card in
                BoxCardRow(model: model, card: card)
            }
        }
    }
}
