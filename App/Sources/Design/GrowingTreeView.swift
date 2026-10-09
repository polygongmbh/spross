import SwiftUI
import SprossKern

// MARK: - GrowingTreeView
//
// One area's tree, rising out of the ground — how it rises is kern's `TreeRise`;
// this runs it on SwiftUI's animation clock.
//
// `Animatable` is what makes it work: a Canvas draws once per body evaluation,
// and only an animatable property gets the body re-evaluated per frame.

struct GrowingTreeView: View, Animatable {
    let transition: TreeTransition
    /// The garden the tree grows in (`AreaTree.garden`).
    let garden: String
    /// 0 = the tree as it stood before, 1 = as it stands now.
    var progress: Double
    private let rise: TreeRise
    /// The ranks the round moved, read once rather than per frame.
    private let ranks: [Int]
    private let finished = FinishedTree()

    init(transition: TreeTransition, garden: String, progress: Double) {
        self.transition = transition
        self.garden = garden
        self.progress = progress
        rise = TreeRise(transition: transition)
        ranks = rise.ranks.map(\.intValue)
    }

    // why: `View` is main-actor isolated but SwiftUI interpolates this off it,
    // so the conformance has to step outside the actor (Swift 6 strict).
    nonisolated var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        // why: the crown's marks hang past the finished tree's box; the bleed
        // draws them over the space around it instead of clipping them.
        BleedingCanvas(bleed: 64) { context, size in
            // why: the frame is the FINISHED tree's, so the drawing never outgrows
            // the space it was given mid-animation; within it the tree rises about its foot.
            let mark = finished.mark(transition.after, garden: garden, in: size)
            let risen = CGFloat(rise.risen(progress: progress))
            context.translateBy(x: mark.foot.x, y: mark.foot.y)
            context.scaleBy(x: risen, y: risen)
            context.translateBy(x: -mark.foot.x, y: -mark.foot.y)
            mark.art.draw(&context, scales: scales)
        }
        .accessibilityHidden(true)
    }

    /// How big each of the round's own marks is drawn at this moment; every other mark is settled.
    private var scales: [Int: CGFloat] {
        guard rise.arriving(progress: progress) else { return [:] }
        return Dictionary(uniqueKeysWithValues: ranks.map {
            ($0, CGFloat(rise.scale(rank: Int32($0), progress: progress)))
        })
    }
}

/// The finished tree placed for one canvas size, kept while the size holds:
/// the canvas redraws on every frame of the rise.
private final class FinishedTree: @unchecked Sendable {
    private let lock = NSLock()
    private var size: CGSize?
    private var placed: TreeMark?

    func mark(_ tree: AreaGrowth, garden: String, in size: CGSize) -> TreeMark {
        lock.lock()
        defer { lock.unlock() }
        if size == self.size, let placed { return placed }
        let mark = TreeMark.solitary(tree, garden: garden, in: size)
        self.size = size
        placed = mark
        return mark
    }
}

// MARK: - Previews

#Preview("A round's growth") {
    let before = AreaGrowth.sample("kitchen", leaves: 18, blossoms: 2, fruit: 1, buds: 9,
                                 fallen: 1)
    let after = AreaGrowth.sample("kitchen", leaves: 22, blossoms: 4, fruit: 2, buds: 6,
                                fallen: 1, tendedToday: true)
    let move = TreeTransition(before: before, after: after)
    return HStack(spacing: Theme.spacing.lg) {
        GrowingTreeView(transition: move, garden: "", progress: 0)
        GrowingTreeView(transition: move, garden: "", progress: 0.5)
        GrowingTreeView(transition: move, garden: "", progress: 1)
    }
    .frame(height: 200)
    .padding(Theme.spacing.xl)
    .background(Theme.colors.background)
}

// Seven words met and nothing settled — the shape of a first round in an area.
#Preview("A first round in a new area") {
    let after = AreaGrowth.sample("bath", buds: 7, tendedToday: true)
    return GrowingTreeView(transition: TreeTransition(before: AreaGrowth.companion.bare(area: "bath"),
                                                      after: after),
                           garden: "", progress: 1)
        .frame(height: AreaTree.shared.heroHeight(tree: after, ceiling: AreaTree.shared.HERO_MAX))
        .padding(Theme.spacing.xl)
        .background(Theme.colors.background)
}

/// An area queued and not yet opened: still a seedling, and nothing hangs.
#Preview("An area only queued") {
    let queued = AreaGrowth.sample("bath", queued: 12, tendedToday: true)
    return GrowingTreeView(transition: TreeTransition(before: queued, after: queued),
                           garden: "", progress: 1)
        .frame(height: AreaTree.shared.heroHeight(tree: queued, ceiling: AreaTree.shared.HERO_MAX))
        .padding(Theme.spacing.xl)
        .background(Theme.colors.background)
}
