import SwiftUI
import SprossKern

/// Browse the box: areas with their stats, per-area "Pack in die Box",
/// card lists with phase badges, and the settings block. The magnifier in the
/// bar opens the same box by typing (`BoxSearchView`), which hands an area back
/// here to be revealed.
struct BoxView: View {
    let model: AppModel

    @State private var expandedGroups: Set<String>
    /// Which areas stand open — lifted out of the sections themselves, because a
    /// search hit has to be able to open the one it landed in.
    @State private var expandedAreas: Set<String> = []
    @State private var searchPresented = false
    /// The area the box should bring into view; cleared the moment it has.
    @State private var scrollTarget: String?

    init(model: AppModel) {
        self.model = model
        // why: the opening fold reads the box once, at construction — a group
        // that folds itself shut again as the learner works would be worse.
        _expandedGroups = State(initialValue: Set([model.defaultExpandedGroupID].compactMap { $0 }))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                    header
                    // Areas grouped under their areas.json groups, manifest order.
                    ForEach(model.areaGroupSections) { group in
                        VStack(alignment: .leading, spacing: DL.Space.l) {
                            groupHeader(group)
                            if expandedGroups.contains(group.id) {
                                ForEach(group.areas, id: \.self) { area in
                                    BoxAreaSection(model: model, area: area,
                                                   expanded: fold(of: area))
                                        .id(area)
                                }
                            }
                        }
                    }
                    BoxSettingsSection(model: model)
                }
                .padding(DL.Space.xl)
            }
            // why: revealing an area is two moves — open it, then bring it up to
            // the thumb; the second one needs the proxy the scroll view owns.
            .onChange(of: scrollTarget) { _, area in
                guard let area else { return }
                withAnimation(.easeInOut(duration: 0.25)) {
                    proxy.scrollTo(area, anchor: .top)
                }
                scrollTarget = nil
            }
        }
        .background(Color.dlBackground.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    searchPresented = true
                } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel("box.search")
            }
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .sheet(isPresented: $searchPresented) {
            BoxSearchView(model: model, reveal: reveal(area:))
        }
    }

    /// One area's fold, held by the screen so both the header and a search hit
    /// can move it.
    private func fold(of area: String) -> Binding<Bool> {
        Binding(
            get: { expandedAreas.contains(area) },
            set: { open in
                if open { expandedAreas.insert(area) } else { expandedAreas.remove(area) }
            }
        )
    }

    /// A search hit names the area it lives in: the group unfolds, the area
    /// unfolds, and the box scrolls it into reach.
    private func reveal(area: String) {
        guard let group = model.areaGroupSections.first(where: { $0.areas.contains(area) })
        else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            expandedGroups.insert(group.id)
            expandedAreas.insert(area)
        }
        scrollTarget = area
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
    @Binding var expanded: Bool

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
        let consolidated = stats?.consolidatedCards ?? 0
        let learning = max(0, (stats?.activeCards ?? 0) - consolidated)

        return HStack(alignment: .top, spacing: DL.Space.s) {
            AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                     consolidated: consolidated, learning: learning,
                     total: stats?.totalCards ?? 0,
                     lockedPhrases: stats?.lockedPhrases ?? 0)
            FoldChevron(open: expanded)
                .foregroundStyle(Color.dlTextSecondary)
                .padding(.top, DL.Space.s)
        }
        .contentShape(Rectangle())
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
        VStack(spacing: DL.Space.s) {
            ForEach(model.cards(inArea: area)) { card in
                BoxCardRow(model: model, card: card)
            }
        }
    }
}
