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
                     hideProgress: fullyQueuedAndSettled(stats))
            FoldChevron(open: expanded)
                .foregroundStyle(Theme.colors.textSecondary)
                .padding(.top, Theme.spacing.sm)
        }
        .contentShape(Rectangle())
    }

    /// Whether nothing is left to queue or unqueue AND every active card in the
    /// area has settled — the one condition that swaps the green "All
    /// queued" mark for a jade one and hides the chip's bar/counts, leaving
    /// just the emoji/name/jade mark in the header (Part D).
    private func fullyQueuedAndSettled(_ stats: AreaStatistics?) -> Bool {
        model.queueableCount(area: area) == 0
            && model.unqueueableCount(area: area) == 0
            && (stats?.fullySettled ?? false)
    }

    /// Icon-only, so the header stays one line tall; the spoken label names the area.
    ///
    /// Once queuing is done, a shelf still holding words queued for a round offers to
    /// take the whole batch back out (`AppModel.unqueueArea`) — the area is the unit
    /// this control acts on, same as queuing itself. Below three queued words the
    /// bulk control steps aside for the per-word one instead (`BoxCardRow.standing`),
    /// and the shelf wears the green "queued" mark; the jade mark is reserved for
    /// nothing queued at all (`fullyQueuedAndSettled`).
    @ViewBuilder
    private var queueControl: some View {
        let count = model.queueableCount(area: area)
        let queued = model.unqueueableCount(area: area)
        if count > 0 {
            QueueButton(direction: .in, label: "a11y.box.shelf.queue \(model.areaTitle(area))") {
                model.queueArea(area)
            }
        } else if queued > 2 {
            QueueButton(direction: .out, label: "a11y.box.shelf.unqueue \(model.areaTitle(area))") {
                model.unqueueArea(area)
            }
        } else {
            let fullySettled = queued == 0 && (model.areaStats(area)?.fullySettled ?? false)
            Image(systemName: "checkmark.circle.fill")
                .font(Theme.typography.headline)
                .foregroundStyle(fullySettled ? Theme.colors.settled : Theme.colors.success)
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
