import SwiftUI
import SprossKern

/// Browse the box: areas with their stats, per-area "Pack in die Box",
/// card lists with phase badges, and the settings block. The magnifier in the
/// bar opens the same box by typing (`BoxSearchView`), which hands an area back
/// here to be revealed.
struct BoxView: View {
    let model: AppModel
    /// The area to open on, when the box was reached by naming one — a tree in
    /// Heute's forest. Revealed once, on appear, exactly as a search hit is.
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
                LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                    header
                    // Areas grouped under their areas.json groups, manifest order.
                    ForEach(model.areaGroupSections, id: \.id) { group in
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
                    // why: no manifest group owns the learner's own words, and none
                    // should — they stand on their own, after everything the catalog
                    // brought.
                    if model.hasOwnWords {
                        BoxAreaSection(model: model, area: model.ownArea,
                                       expanded: fold(of: model.ownArea))
                            .id(model.ownArea)
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
            // why: the fold is already set by init — this only brings the named
            // area up to the thumb, and only on the first appearance.
            .onAppear {
                if let revealArea, scrollTarget == nil { scrollTarget = revealArea }
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
    /// unfolds, and the box scrolls it into reach. The own-words area sits in no
    /// group, so there is simply nothing to unfold above it.
    private func reveal(area: String) {
        let group = model.areaGroupSections.first { $0.areas.contains(area) }
        withAnimation(.easeInOut(duration: 0.2)) {
            if let group { expandedGroups.insert(group.id) }
            expandedAreas.insert(area)
        }
        scrollTarget = area
    }

    /// Foldable group row — a hairline rule and no card of its own, so the
    /// area cards below it stay the heaviest thing on the screen.
    private func groupHeader(_ group: AreaGroupSection) -> some View {
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
            // why: same disclosure as the number/country reference tables — said
            // once for the page rather than as a glyph competing with every row.
            if anyWordCanBeHeard {
                ReferenceTapHint(textKey: "box.tapToHear")
            }
        }
    }

    private var subtitle: Text {
        let active = model.stats?.activeCards ?? 0
        let total = model.box?.cards.count ?? 0
        return Text("box.cardsInProgress \(active.formatted()) \(total.formatted())")
    }

    /// Whether the hint is worth showing at all — a box whose language has
    /// neither a recording nor a device voice for a single word must not
    /// promise a tap that would do nothing everywhere.
    private var anyWordCanBeHeard: Bool {
        guard let target = model.targetLanguage else { return false }
        return Pronouncer.shared.canSpeak(language: target)
            || (model.box?.cards.values.contains { card in
                model.pronounceAction(for: card.target.text, lang: card.target.lang) != nil
            } ?? false)
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
        HStack(alignment: .top, spacing: DL.Space.s) {
            AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                     subtitle: model.areaSubtitle(area),
                     progress: stats?.progress ?? .empty,
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
    ///
    /// Once packing is done, a shelf still holding words queued for a round offers to
    /// take the whole batch back out (`AppModel.dequeueArea`) — the area is the unit
    /// this control acts on, same as packing itself.
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
            .buttonStyle(DLIconButtonStyle())
            .accessibilityLabel(Text("box.enqueue \(count.formatted())"))
        } else if queued > 0 {
            Button {
                model.dequeueArea(area)
            } label: {
                Image(systemName: "tray.and.arrow.up.fill")
            }
            .buttonStyle(DLIconButtonStyle(color: .dlSuccess))
            .accessibilityLabel(Text("box.dequeue \(queued.formatted())"))
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
