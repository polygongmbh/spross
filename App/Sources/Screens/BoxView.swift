import SwiftUI
import SprossKern

/// Browse the box: areas with their stats, per-area "Pack in die Box",
/// card lists with phase badges, and the settings block.
struct BoxView: View {
    let model: AppModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                // Areas grouped under their areas.json groups, manifest order.
                ForEach(model.areaGroupSections) { group in
                    groupHeader(group.title)
                    ForEach(group.areas, id: \.self) { area in
                        BoxAreaSection(model: model, area: area)
                    }
                }
                BoxSettingsSection(model: model)
            }
            .padding(DL.Space.xl)
        }
        .background(Color.dlBackground.ignoresSafeArea())
    }

    /// Plain section header (kicker idiom: uppercased caption, secondary).
    private func groupHeader(_ title: String) -> some View {
        Text(title)
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            .padding(.top, DL.Space.s)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("Die Box")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            subtitle
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var subtitle: Text {
        let active = model.stats?.activeCards ?? 0
        let total = model.box?.cards.count ?? 0
        return Text("\(active) von \(total) Karten in Arbeit")
    }
}

// MARK: - Area section

private struct BoxAreaSection: View {
    let model: AppModel
    let area: String

    @State private var expanded = false

    var body: some View {
        let stats = model.areaStats(area)
        let sitting = stats?.sittingCards ?? 0
        let learning = max(0, (stats?.activeCards ?? 0) - sitting)

        VStack(alignment: .leading, spacing: DL.Space.m) {
            AreaChip(emoji: AreaInfo.emoji(for: area), name: model.areaTitle(area),
                     sitting: sitting, learning: learning)
            phraseRow(stats)
            packButton
            cardList
        }
    }

    @ViewBuilder
    private func phraseRow(_ stats: AreaStatistics?) -> some View {
        if let stats, stats.lockedPhrases + stats.unlockedPhrases > 0 {
            HStack(spacing: DL.Space.l) {
                Label("\(stats.unlockedPhrases) Sätze freigeschaltet", systemImage: "lock.open.fill")
                Label("\(stats.lockedPhrases) gesperrt", systemImage: "lock.fill")
                Spacer(minLength: 0)
            }
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .padding(.horizontal, DL.Space.xs)
        }
    }

    @ViewBuilder
    private var packButton: some View {
        let count = model.enqueueableCount(area: area)
        if count > 0 {
            Button {
                model.enqueueArea(area)
            } label: {
                Label("Pack in die Box (\(count))", systemImage: "plus.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLSoftButtonStyle())
        } else {
            Label("Alles eingepackt", systemImage: "checkmark.circle.fill")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlSuccess)
                .padding(.horizontal, DL.Space.xs)
        }
    }

    private var cardList: some View {
        let cards = model.cards(inArea: area)
        return DisclosureGroup(isExpanded: $expanded) {
            VStack(spacing: DL.Space.xs) {
                ForEach(cards) { card in
                    BoxCardRow(model: model, card: card)
                }
            }
            .padding(.top, DL.Space.s)
        } label: {
            Text(expanded ? "Karten ausblenden" : "\(cards.count) Karten anzeigen")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTeal)
        }
        .tint(.dlTeal)
        .padding(.horizontal, DL.Space.xs)
    }
}

// MARK: - Card row

private struct BoxCardRow: View {
    let model: AppModel
    let card: Card

    var body: some View {
        let sched = model.scheduling(for: card.id)

        HStack(spacing: DL.Space.m) {
            Text(card.displayEmoji)
                .font(.title3)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                // Exposure surfaces render the TARGET side first (contract §6).
                Text(CardDisplay.citation(of: card.target))
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                Text(card.source.text)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: DL.Space.s)
            if sched?.suspended == true {
                Text(verbatim: "💤")
                    .accessibilityLabel("Pausiert")
                Button("Wecken") {
                    model.setSuspended(cardID: card.id, suspended: false)
                }
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlAccent)
                .padding(.horizontal, DL.Space.m)
                .padding(.vertical, DL.Space.xs + 1)
                .background(Color.dlAccent.opacity(0.14), in: Capsule())
            } else {
                PhaseBadge(phase: badgePhase(sched))
            }
        }
        .padding(.horizontal, DL.Space.l)
        .padding(.vertical, DL.Space.s + 2)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurface)
        )
    }

    private func badgePhase(_ sched: CardScheduling?) -> PhaseBadge.Phase {
        switch sched?.phase {
        case nil, .theNew?: return .new
        case .learning?: return .learning
        case .review?: return .review
        case .relearning?: return .relearning
        }
    }
}
