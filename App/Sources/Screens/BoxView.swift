import SwiftUI
import SprossKern

/// Browse the box: areas with their stats, per-area "Pack in die Box",
/// card lists with phase badges, and the settings block.
struct BoxView: View {
    let model: AppModel

    @State private var expandedGroups: Set<String>

    init(model: AppModel) {
        self.model = model
        // why: the opening fold reads the box once, at construction — a group
        // that folds itself shut again as the learner works would be worse.
        _expandedGroups = State(initialValue: Set([model.defaultExpandedGroupID].compactMap { $0 }))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                // Areas grouped under their areas.json groups, manifest order.
                ForEach(model.areaGroupSections) { group in
                    VStack(alignment: .leading, spacing: DL.Space.l) {
                        groupHeader(group)
                        if expandedGroups.contains(group.id) {
                            ForEach(group.areas, id: \.self) { area in
                                BoxAreaSection(model: model, area: area)
                            }
                        }
                    }
                }
                BoxSettingsSection(model: model)
            }
            .padding(DL.Space.xl)
        }
        .background(Color.dlBackground.ignoresSafeArea())
    }

    /// Foldable group row — a hairline rule and no card of its own, so the
    /// area cards below it stay the heaviest thing on the screen.
    private func groupHeader(_ group: AppModel.AreaGroupSection) -> some View {
        let open = expandedGroups.contains(group.id)
        return VStack(alignment: .leading, spacing: DL.Space.xs) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    if open { expandedGroups.remove(group.id) } else { expandedGroups.insert(group.id) }
                }
            } label: {
                HStack(spacing: DL.Space.s) {
                    FoldChevron(open: open)
                    Text(group.title)
                        .font(DL.Fonts.headline)
                        .lineLimit(1)
                    Spacer(minLength: DL.Space.s)
                    // why: folded shut, these emojis are all that says what is inside.
                    Text(group.areas.map(model.areaEmoji).joined())
                        .font(DL.Fonts.subheadline)
                        .lineLimit(1)
                        .accessibilityHidden(true)
                }
                .foregroundStyle(Color.dlTextSecondary)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Divider().overlay(Color.dlSeparator)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("box.title")
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
        return Text("box.cardsInProgress \(active.formatted()) \(total.formatted())")
    }
}

// MARK: - Fold chevron

/// The one fold affordance on this screen: groups and area cards use it,
/// so both read as the same gesture.
private struct FoldChevron: View {
    let open: Bool

    var body: some View {
        Image(systemName: "chevron.right")
            .font(.caption2)
            .rotationEffect(.degrees(open ? 90 : 0))
    }
}

// MARK: - Area section

/// One area as a single foldable card: name, progress bar and phrase counts
/// in the header, its words underneath once opened. Packing sits beside the
/// header as its own tap target — it must never cost the fold.
private struct BoxAreaSection: View {
    let model: AppModel
    let area: String

    @State private var expanded = false

    var body: some View {
        let stats = model.areaStats(area)

        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: DL.Space.m) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
                } label: {
                    header(stats)
                }
                .buttonStyle(.plain)
                packControl
            }
            if expanded {
                cardList
                    .padding(.top, DL.Space.m)
            }
        }
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }

    private func header(_ stats: AreaStatistics?) -> some View {
        let settled = stats?.settledCards ?? 0
        let learning = max(0, (stats?.activeCards ?? 0) - settled)

        return HStack(alignment: .top, spacing: DL.Space.s) {
            VStack(alignment: .leading, spacing: DL.Space.s) {
                AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                         settled: settled, learning: learning,
                         total: stats?.totalCards ?? 0)
                phraseRow(stats)
            }
            FoldChevron(open: expanded)
                .foregroundStyle(Color.dlTextSecondary)
                .padding(.top, DL.Space.s)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func phraseRow(_ stats: AreaStatistics?) -> some View {
        if let stats, stats.lockedPhrases + stats.unlockedPhrases > 0 {
            HStack(spacing: DL.Space.l) {
                Label("box.phrasesUnlocked \(stats.unlockedPhrases)", systemImage: "lock.open.fill")
                Label("box.phrasesLocked \(stats.lockedPhrases.formatted())", systemImage: "lock.fill")
                Spacer(minLength: 0)
            }
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// The count moved from the button's face into its label: an icon-only
    /// control keeps the header one line tall, and the bar already shows
    /// how much of the area is still untouched.
    @ViewBuilder
    private var packControl: some View {
        let count = model.enqueueableCount(area: area)
        if count > 0 {
            Button {
                model.enqueueArea(area)
            } label: {
                Image(systemName: "plus")
            }
            .buttonStyle(DLIconButtonStyle())
            .accessibilityLabel(Text("box.enqueue \(count.formatted())"))
        } else {
            Image(systemName: "checkmark.circle.fill")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlSuccess)
                .frame(width: 40, height: 40)
                .accessibilityLabel(Text("box.enqueueDone"))
        }
    }

    private var cardList: some View {
        VStack(spacing: DL.Space.xs) {
            ForEach(model.cards(inArea: area)) { card in
                BoxCardRow(model: model, card: card)
            }
        }
    }
}

// MARK: - Card row

private struct BoxCardRow: View {
    let model: AppModel
    let card: Card

    var body: some View {
        let sched = model.scheduling(for: card.id)
        let pronounce = model.pronounceAction(for: card.target.text, lang: card.target.lang)

        HStack(spacing: DL.Space.m) {
            Text(card.displayEmoji)
                .font(.title3)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                // Exposure surfaces render the TARGET side first (contract §6).
                HStack(spacing: DL.Space.xs) {
                    Text(CardDisplay.citation(of: card.target))
                        .font(DL.Fonts.body)
                        .foregroundStyle(Color.dlTextPrimary)
                        .lineLimit(1)
                    if let pronounce {
                        SpeakerIcon(size: .small,
                                   isPlaying: model.isPronouncing(card.target.text, lang: card.target.lang),
                                   pronounce: pronounce)
                            .accessibilityLabel("a11y.pronounce")
                    }
                }
                Text(card.source.text)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: DL.Space.s)
            if sched?.suspended == true {
                Text(verbatim: "💤")
                    .accessibilityLabel("box.suspended")
                Button("box.wake") {
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
            // why: the row sits INSIDE the area card now — surface on surface
            // would leave the rows without an edge of their own.
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurfaceTint)
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
