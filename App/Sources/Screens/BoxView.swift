import SwiftUI
import DuoKern

/// Browse the box: areas with their stats, per-area "Pack in die Box",
/// card lists with phase badges, and the settings block.
struct BoxView: View {
    let model: AppModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                ForEach(model.areaNames, id: \.self) { area in
                    BoxAreaSection(model: model, area: area)
                }
                BoxSettingsSection(model: model)
            }
            .padding(DL.Space.xl)
        }
        .background(Color.dlBackground.ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("Die Box")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text(subtitle)
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var subtitle: String {
        let active = model.stats?.activeCount ?? 0
        let total = model.box?.cards.count ?? 0
        return "\(active) von \(total) Karten in Arbeit"
    }
}

// MARK: - Area section

private struct BoxAreaSection: View {
    let model: AppModel
    let area: String

    @State private var expanded = false

    var body: some View {
        let info = AreaInfo.info(for: area)
        let stats = model.areaStats(area)
        let sitting = stats?.sitting ?? 0
        let learning = max(0, (stats?.active ?? 0) - sitting)

        VStack(alignment: .leading, spacing: DL.Space.m) {
            AreaChip(emoji: info.emoji, name: info.name, sitting: sitting, learning: learning)
            phraseRow(stats)
            packButton
            cardList(info: info)
        }
    }

    @ViewBuilder
    private func phraseRow(_ stats: AreaStatistics?) -> some View {
        if let stats, stats.phrasesLocked + stats.phrasesUnlocked > 0 {
            HStack(spacing: DL.Space.l) {
                Label("\(stats.phrasesUnlocked) Sätze frei", systemImage: "lock.open.fill")
                Label("\(stats.phrasesLocked) warten", systemImage: "lock.fill")
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

    private func cardList(info: AreaInfo) -> some View {
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
                Text(card.germanWithArticle)
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                Text(card.translation)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: DL.Space.s)
            if sched?.suspended == true {
                Text("💤")
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
        case nil, .new?: return .new
        case .learning?: return .learning
        case .review?: return .review
        case .relearning?: return .relearning
        }
    }
}
