import SwiftUI
import SprossKern

// MARK: - GrowingTreeView
//
// One area's tree, rising out of the ground. The one place in the app where a
// tree is allowed to move: the Trees picture on Home holds still because a box grows
// over weeks and motion there would claim a change the picture is not showing —
// here a round has just finished, so something did in fact just happen.
//
// `Animatable` is what makes it work: a Canvas draws once per body evaluation,
// and only an animatable property gets the body re-evaluated per frame.
//
// Two motions, and they say different things:
//
//   · the whole tree RISES, from a crouch to its full height. It rises even
//     when the round moved no count at all — a learner who spent ten minutes
//     holding a hard area steady earned the tree standing up, and a summary
//     that shows a photograph on those days is a summary of nothing.
//   · what the round CHANGED arrives after it, mark by mark. Those are the only
//     marks that move on their own, so the eye is taken to the new leaf rather
//     than spread over a crown that all wobbles alike.

struct GrowingTreeView: View, Animatable {
    let transition: TreeTransition
    /// The garden the tree grows in (`AreaTree.garden`).
    let garden: String
    /// 0 = the tree as it stood before, 1 = as it stands now.
    var progress: Double
    /// The finished tree's marks, read once rather than per frame.
    private let canopy: Canopy
    /// The share of its finished height the tree rises from.
    private let from: CGFloat

    init(transition: TreeTransition, garden: String, progress: Double) {
        self.transition = transition
        self.garden = garden
        self.progress = progress
        canopy = Canopy(transition.after)
        let full = AreaTree.shared.height(tree: transition.after)
        let was = AreaTree.shared.height(tree: transition.before)
        // An area worked from nothing rises from nothing; everything else rises from where it
        // stood, or from the crouch, whichever is lower.
        from = full > 0 ? min(Self.crouch, CGFloat(was / full)) : Self.crouch
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
            // why: the frame is the FINISHED tree's, so the drawing never
            // outgrows the space it was given mid-animation; within it the tree
            // rises from the height it had before the round.
            let mark = TreeMark.solitary(transition.after, garden: garden, canopy: canopy, in: size, risen: risen)
            TreeShapes.draw(&context, mark, arriving: TreeArrival(transition, at: progress))
        }
        .accessibilityHidden(true)
    }

    /// A tree never starts taller than this fraction of where it ends, however
    /// little the round changed — the rise is the part of the motion that is
    /// owed to the learner rather than to the counts.
    private static let crouch: CGFloat = 0.78

    /// How tall the tree stands now, as a fraction of its finished height.
    private var risen: CGFloat {
        // Clamped at the top: the spring settles from above, and a tree that
        // overshot its own height would be overshooting into the screen edge.
        max(0.05, from + (1 - from) * CGFloat(min(1, max(0, progress))))
    }
}

// MARK: - Arrival

/// How far each of the round's own marks has come, at one moment of the rise.
///
/// Every other mark is drawn settled and full size — it was already on the tree,
/// and a crown where everything moves says nothing about what just happened.
struct TreeArrival {
    /// Keyed by canopy rank; anything not in here is settled.
    private let scales: [Int: CGFloat]

    /// Nothing arriving — the Trees picture on Home, and any tree drawn outside a summary.
    static let settled = TreeArrival(scales: [:])

    private init(scales: [Int: CGFloat]) { self.scales = scales }

    /// The first marks wait until the tree is most of the way up, so an arrival
    /// reads as landing ON the tree rather than as part of its rising.
    private static let opens = 0.42
    /// A mark takes this much of the rise to arrive, and the last one starts
    /// this far after the first. Both inside the rise: a spring approaches its
    /// end slowly, and motion timed to the last of it drags.
    private static let takes = 0.24
    private static let stagger = 0.30

    init(_ transition: TreeTransition, at progress: Double) {
        let ranks = transition.changedRanks.map(\.intValue)
        let hanging = Int(transition.standingCount)
        var scales: [Int: CGFloat] = [:]
        for (order, rank) in ranks.enumerated() {
            let share = ranks.count > 1 ? Double(order) / Double(ranks.count - 1) : 0
            let begins = Self.opens + Self.stagger * share
            let t = min(1, max(0, (progress - begins) / Self.takes))
            // A mark the round HUNG has to arrive out of nothing; a mark it only
            // moved a tier was already hanging there, and popping it in from
            // zero would read as the word having been taken off the tree first.
            scales[rank] = rank >= hanging ? Self.pop(t) : Self.swell(t)
        }
        self.scales = scales
    }

    /// How big the mark at this rank is drawn, against its settled size.
    func scale(_ rank: Int) -> CGFloat { scales[rank] ?? 1 }

    /// Out of nothing, past full size, back to it — the overshoot is what makes
    /// a leaf appearing read as an event rather than as a redraw.
    private static func pop(_ t: Double) -> CGFloat {
        let over = 1.9, past = t - 1
        return CGFloat(1 + (over + 1) * past * past * past + over * past * past)
    }

    /// Already there, so it swells and settles back: a word that settled opens
    /// where it hangs.
    private static func swell(_ t: Double) -> CGFloat {
        1 + 0.25 * CGFloat(sin(.pi * t))
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

/// An area packed and not yet opened: still a seedling, and nothing hangs.
#Preview("An area only packed") {
    let packed = AreaGrowth.sample("bath", queued: 12, tendedToday: true)
    return GrowingTreeView(transition: TreeTransition(before: packed, after: packed),
                           garden: "", progress: 1)
        .frame(height: AreaTree.shared.heroHeight(tree: packed, ceiling: AreaTree.shared.HERO_MAX))
        .padding(Theme.spacing.xl)
        .background(Theme.colors.background)
}
