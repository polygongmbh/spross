import SwiftUI
import SprossKern

/// Browse the box: areas with their stats, per-area "Pack in die Box", card lists
/// with phase badges, then what the learner wrote themselves
/// (`BoxOwnContentSection`) and the settings block. The magnifier in the bar opens
/// the same box by typing (`BoxSearchView`), which hands an area back here to be
/// revealed.
struct BoxView: View {
    let model: AppModel
    /// The area to open on, when the box was reached by naming one — a tree in
    /// Home's forest. Revealed once, on appear, exactly as a search hit is.
    var revealArea: String?

    @State private var expandedGroups: Set<String>
    /// Which areas stand open — lifted out of the sections themselves, because a
    /// search hit has to be able to open the one it landed in.
    @State private var expandedAreas: Set<String> = []
    @State private var searchPresented = false
    /// The area the box should bring into view; cleared the moment it has.
    @State private var scrollTarget: String?

    init(model: AppModel, revealArea: String? = nil) {
        self.model = model
        self.revealArea = revealArea
        // why: the opening fold reads the box once, at construction — a group
        // that folds itself shut again as the learner works would be worse.
        // An area named on the way in opens INSTEAD of the default group: the
        // learner already said which one they meant.
        let opening = revealArea.flatMap { area in
            model.areaGroupSections.first { $0.areas.contains(area) }?.id
        } ?? model.defaultExpandedGroupID
        _expandedGroups = State(initialValue: Set([opening].compactMap { $0 }))
        _expandedAreas = State(initialValue: Set([revealArea].compactMap { $0 }))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Theme.spacing.xl) {
                    header
                    // Areas grouped under their areas.json groups, manifest order.
                    ForEach(model.areaGroupSections, id: \.id) { group in
                        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
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
                    // why: no manifest group owns what the learner wrote, and none
                    // should — it stands on its own, after everything the catalog
                    // brought, and unlike a shelf it is always there.
                    BoxOwnContentSection(model: model)
                        .id(model.ownArea)
                    BoxSettingsSection(model: model)
                }
                .padding(Theme.spacing.xl)
            }
            // why: revealing an area is two moves — open it, then bring it up to
            // the thumb; the second one needs the proxy the scroll view owns.
            // The scroll waits a turn: the row it names is a lazy one, and on
            // the way in it is not laid out yet for the proxy to find.
            .onChange(of: scrollTarget) { _, area in
                guard let area else { return }
                scrollTarget = nil
                Task { @MainActor in
                    withAnimation(.easeInOut(duration: 0.25)) {
                        proxy.scrollTo(area, anchor: .top)
                    }
                }
            }
            // why: the fold is already set by init — this only brings the named
            // area up to the thumb, and only on the first appearance.
            .onAppear {
                if let revealArea, scrollTarget == nil { scrollTarget = revealArea }
            }
        }
        .background(Theme.colors.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    searchPresented = true
                } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel("box.search.button")
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
    /// unfolds, and the box scrolls it into reach. Own words have no shelf to
    /// unfold — they list in the own-content section, which stands open always,
    /// so naming their area is only ever a scroll.
    private func reveal(area: String) {
        if area != model.ownArea {
            let group = model.areaGroupSections.first { $0.areas.contains(area) }
            withAnimation(.easeInOut(duration: 0.2)) {
                if let group { expandedGroups.insert(group.id) }
                expandedAreas.insert(area)
            }
        }
        scrollTarget = area
    }

    /// Foldable group row — a hairline rule and no card of its own, so the
    /// area cards below it stay the heaviest thing on the screen.
    private func groupHeader(_ group: AreaGroupSection) -> some View {
        let open = expandedGroups.contains(group.id)
        return VStack(alignment: .leading, spacing: Theme.spacing.xs) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    if open { expandedGroups.remove(group.id) } else { expandedGroups.insert(group.id) }
                }
            } label: {
                HStack(spacing: Theme.spacing.sm) {
                    FoldChevron(open: open)
                    Text(group.title)
                        .font(Theme.typography.headline)
                        .lineLimit(1)
                    Spacer(minLength: Theme.spacing.sm)
                    // why: folded shut, these emojis are all that says what is inside.
                    Text(group.areas.map(model.areaEmoji).joined())
                        .font(Theme.typography.subheadline)
                        .lineLimit(1)
                        .accessibilityHidden(true)
                }
                .foregroundStyle(Theme.colors.textSecondary)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Divider().overlay(Theme.colors.separator)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.xs) {
            Text("box.title")
                .font(Theme.typography.hero)
                .foregroundStyle(Theme.colors.textPrimary)
            subtitle
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
            // why: same disclosure as the number/country reference tables — said
            // once for the page rather than as a glyph competing with every row.
            if anyWordCanBeHeard {
                ReferenceTapHint(textKey: "box.tapToHear")
            }
        }
    }

    private var subtitle: Text {
        let active = model.stats?.activeCards ?? 0
        let total = model.cardTotal
        return Text("box.subtitle \(active.formatted()) \(total.formatted())")
    }

    /// Whether the hint is worth showing at all — a box whose language has
    /// neither a recording nor a device voice for a single word must not
    /// promise a tap that would do nothing everywhere.
    private var anyWordCanBeHeard: Bool { model.anyWordAudible }
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
            HStack(alignment: .top, spacing: Theme.spacing.md) {
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
                    .padding(.top, Theme.spacing.md)
            }
        }
        .padding(Theme.spacing.lg)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
    }

    private func header(_ stats: AreaStatistics?) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.sm) {
            AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                     subtitle: model.areaSubtitle(area),
                     progress: stats?.progress ?? .empty,
                     lockedPhrases: stats?.lockedPhrases ?? 0,
                     hideProgress: fullyPackedAndMature(stats))
            FoldChevron(open: expanded)
                .foregroundStyle(Theme.colors.textSecondary)
                .padding(.top, Theme.spacing.sm)
        }
        .contentShape(Rectangle())
    }

    /// Whether nothing is left to pack or unpack AND every active card in the
    /// area has matured — the one condition that swaps the green "All
    /// packed" mark for a jade one and hides the chip's bar/counts, leaving
    /// just the emoji/name/jade mark in the header (Part D).
    private func fullyPackedAndMature(_ stats: AreaStatistics?) -> Bool {
        model.enqueueableCount(area: area) == 0
            && model.dequeueableCount(area: area) == 0
            && (stats?.mature ?? false)
    }

    /// The count moved from the button's face into its label: an icon-only
    /// control keeps the header one line tall, and the bar already shows
    /// how much of the area is still untouched.
    ///
    /// Once packing is done, a shelf still holding words queued for a round offers to
    /// take the whole batch back out (`AppModel.dequeueArea`) — the area is the unit
    /// this control acts on, same as packing itself. Below three queued words the
    /// bulk control steps aside for the per-word one instead (`BoxCardRow.standing`):
    /// a blank slot here, not a misleading "All packed" mark, since the shelf still
    /// holds queued words.
    @ViewBuilder
    private var packControl: some View {
        let count = model.enqueueableCount(area: area)
        let queued = model.dequeueableCount(area: area)
        if count > 0 {
            Button {
                model.enqueueArea(area)
            } label: {
                Image(systemName: "tray.and.arrow.down.fill")
            }
            // Ochre, where unpacking is clay: the pair reads as two directions rather
            // than one control, and neither wears a growth-ladder color.
            .buttonStyle(IconButtonStyle(color: Theme.colors.amber))
            .accessibilityLabel(Text("box.shelf.pack \(count.formatted())"))
        } else if queued > 2 {
            Button {
                model.dequeueArea(area)
            } label: {
                Image(systemName: "tray.and.arrow.up.fill")
            }
            // Clay, matching the queued pill it takes back out.
            .buttonStyle(IconButtonStyle(color: Theme.colors.accent))
            .accessibilityLabel(Text("box.shelf.unpack \(queued.formatted())"))
        } else if queued == 0 {
            let mature = model.areaStats(area)?.mature ?? false
            Image(systemName: "checkmark.circle.fill")
                .font(Theme.typography.headline)
                .foregroundStyle(mature ? Theme.colors.grown : Theme.colors.success)
                .frame(width: 40, height: 40)
                .accessibilityLabel(Text("box.shelf.packed"))
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
