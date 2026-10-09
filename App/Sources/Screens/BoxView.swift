import SwiftUI
import SprossKern

/// How long the group's fold is given to lay its areas out before the box is asked to
/// scroll to one — just past the 0.2 s the fold itself animates for.
private let FOLD_SETTLE_MS = 220

/// How many times the box asks to be scrolled to the revealed area. See the loop.
private let SCROLL_ATTEMPTS = 6

/// Browse the box: areas with their stats, per-area queue controls, card lists
/// with phase badges, then what the learner wrote themselves
/// (`BoxOwnContentSection`). The magnifier in the bar opens
/// the same box by typing (`BoxSearchView`), which hands an area back here to be
/// revealed.
struct BoxView: View {
    let model: AppModel
    /// The area to open on, when the box was reached by naming one —
    /// a tree in Home's Trees picture.
    /// Revealed once, on appear, exactly as a search hit is.
    var revealArea: String?

    @State private var expandedGroups: Set<String>
    /// Which areas stand open — lifted out of the sections themselves, because a
    /// search hit has to be able to open the one it landed in.
    @State private var expandedAreas: Set<String> = []
    @State private var searchPresented = false
    /// The area the box should bring into view; cleared the moment it has.
    @State private var scrollTarget: String?
    /// The area a search hit named, held until its sheet is actually gone.
    @State private var revealAfterSearch: String?
    /// The area (or own-words) row nearest the top of the visible box, tracked
    /// by SwiftUI itself (`scrollTargetLayout`/`scrollPosition`). A target-language
    /// switch changes every row's height under a fixed pixel offset — this keeps
    /// the same ROW in view instead, which is the shelf the learner actually
    /// scrolled to.
    @State private var scrollPositionID: String?

    init(model: AppModel, revealArea: String? = nil) {
        self.model = model
        self.revealArea = revealArea
        // why: the opening fold reads the box once, at construction (`BoxFold.opening`).
        let opening = model.openingFold(revealArea: revealArea)
        _expandedGroups = State(initialValue: opening.groups)
        _expandedAreas = State(initialValue: opening.areas)
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
                }
                .padding(Theme.spacing.xl)
                .scrollTargetLayout()
            }
            .scrollPosition(id: $scrollPositionID)
            // why: revealing an area is two moves — open it, then bring it up to
            // the thumb; the second one needs the proxy the scroll view owns.
            // The scroll waits for the FOLD, not just a turn: the row it names is a
            // lazy one inside the group that just opened, and asked for before that
            // insertion has laid out, the proxy finds nothing and the box stays put.
            .onChange(of: scrollTarget) { _, area in
                guard let area else { return }
                scrollTarget = nil
                Task { @MainActor in
                    // why: one ask is not enough. The list is lazy, so a row three
                    // screens down is not laid out for the proxy to find; each ask
                    // realizes the rows it passes, and the next one reaches further.
                    for _ in 0..<SCROLL_ATTEMPTS {
                        try? await Task.sleep(for: .milliseconds(FOLD_SETTLE_MS))
                        withAnimation(.easeInOut(duration: 0.25)) {
                            proxy.scrollTo(area, anchor: .top)
                        }
                    }
                }
            }
            // why: the fold is already set by init — this only brings the named
            // area up to the thumb, and only on the first appearance.
            .onAppear {
                if let revealArea, scrollTarget == nil { scrollTarget = revealArea }
            }
            // why: the tab keeps this screen alive, so a tree tapped after the box has
            // once stood open reaches a view whose `init` will not run again. The area
            // it names gets the unfold a search hit gets.
            .onChange(of: revealArea) { _, area in
                if let area { reveal(area: area) }
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
        // why: the reveal waits for the sheet to be GONE. Unfolding behind it is
        // harmless, but the scroll is not: the row it names is a lazy one that the
        // covered box has not laid out, so the proxy finds nothing and the box stays
        // where it was — the area opened and the learner never saw it.
        .sheet(isPresented: $searchPresented, onDismiss: revealWhatSearchNamed) {
            BoxSearchView(model: model, reveal: { revealAfterSearch = $0 })
        }
    }

    private func revealWhatSearchNamed() {
        guard let area = revealAfterSearch else { return }
        revealAfterSearch = nil
        reveal(area: area)
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

    /// A search hit names the area it lives in: the fold moves to it (`BoxFold.revealing`),
    /// and the box scrolls it into reach.
    private func reveal(area: String) {
        let fold = BoxFold(groups: expandedGroups, areas: expandedAreas)
            .revealing(area: area, sections: model.areaGroupSections)
        withAnimation(.easeInOut(duration: 0.2)) {
            expandedGroups = fold.groups
            expandedAreas = fold.areas
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
