import SwiftUI

// MARK: - GrowingTreeView
//
// One area's tree, rising out of the ground. The one place in the app where a
// tree is allowed to move: the forest on Heute holds still because a box grows
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
    /// 0 = the tree as it stood before, 1 = as it stands now.
    var progress: Double

    // why: `View` is main-actor isolated but SwiftUI interpolates this off it,
    // so the conformance has to step outside the actor (Swift 6 strict).
    nonisolated var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        Canvas { context, size in
            // why: the frame is the FINISHED tree's, so the drawing never
            // outgrows the space it was given mid-animation; within it the tree
            // rises from the height it had before the round.
            let mark = ForestLayout.solitary(transition.after, in: size)
            TreeShapes.draw(&context, crouched(mark),
                            arriving: TreeArrival(transition, at: progress))
        }
        .accessibilityHidden(true)
    }

    /// The tree at this moment's height, keeping the finished tree's identity so
    /// the skeleton — and therefore every slot — stays put.
    private func crouched(_ mark: TreeMark) -> TreeMark {
        TreeMark(tree: mark.tree, foot: mark.foot, height: mark.height * risen,
                 cell: mark.cell, baseline: mark.baseline)
    }

    /// A tree never starts taller than this fraction of where it ends, however
    /// little the round changed — the rise is the part of the motion that is
    /// owed to the learner rather than to the counts.
    private static let crouch: CGFloat = 0.78

    /// How tall the tree stands now, as a fraction of its finished height. An
    /// area worked from nothing rises from nothing; everything else rises from
    /// where it stood, or from the crouch, whichever is lower.
    private var risen: CGFloat {
        let full = ForestLayout.treeHeight(transition.after)
        let was = ForestLayout.treeHeight(transition.before)
        let from = full > 0 ? min(Self.crouch, was / full) : Self.crouch
        // Clamped at the top: the spring settles from above, and a tree that
        // overshot its own height would be overshooting into the screen edge.
        return max(0.05, from + (1 - from) * CGFloat(min(1, max(0, progress))))
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

    /// Nothing arriving — the forest, and any tree drawn outside a summary.
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
        let ranks = transition.changedRanks
        let hanging = transition.settledCount
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

    /// Already there, so it swells and settles back: a word that matured opens
    /// where it hangs.
    private static func swell(_ t: Double) -> CGFloat {
        1 + 0.25 * CGFloat(sin(.pi * t))
    }
}

// MARK: - Previews

#Preview("A round's growth") {
    let before = AreaTree(id: "kitchen", emoji: "🍳", title: "Die Küche",
                          leaves: 18, blossoms: 2, fruit: 1, buds: 9, growing: 0, fallen: 1,
                          mass: 14, tendedToday: false)
    let after = AreaTree(id: "kitchen", emoji: "🍳", title: "Die Küche",
                         leaves: 22, blossoms: 4, fruit: 2, buds: 6, growing: 0, fallen: 1,
                         mass: 18, tendedToday: true)
    let move = TreeTransition(before: before, after: after)
    return HStack(spacing: DL.Space.l) {
        GrowingTreeView(transition: move, progress: 0)
        GrowingTreeView(transition: move, progress: 0.5)
        GrowingTreeView(transition: move, progress: 1)
    }
    .frame(height: 200)
    .padding(DL.Space.xl)
    .background(Color.dlBackground)
}

#Preview("A round that moved no count") {
    let same = AreaTree(id: "kitchen", emoji: "🍳", title: "Die Küche",
                        leaves: 26, blossoms: 5, fruit: 2, buds: 4, growing: 0, fallen: 0,
                        mass: 19, tendedToday: true)
    let move = TreeTransition(before: same, after: same)
    return HStack(spacing: DL.Space.l) {
        GrowingTreeView(transition: move, progress: 0)
        GrowingTreeView(transition: move, progress: 0.5)
        GrowingTreeView(transition: move, progress: 1)
    }
    .frame(height: 200)
    .padding(DL.Space.xl)
    .background(Color.dlBackground)
}

// Seven words met and nothing settled — the shape of a first round, and the one
// the tree used to answer with a bare stem the height of the whole screen.
#Preview("A first round in a new area") {
    let after = AreaTree(id: "bath", emoji: "🛁", title: "Das Bad",
                         leaves: 0, blossoms: 0, fruit: 0, buds: 7, growing: 0, fallen: 0,
                         mass: 1.3, tendedToday: true)
    return GrowingTreeView(
        transition: TreeTransition(
            before: AreaTree(id: "bath", emoji: "🛁", title: "Das Bad",
                             leaves: 0, blossoms: 0, fruit: 0, growing: 0, fallen: 0,
                             mass: 0, tendedToday: false),
            after: after),
        progress: 1
    )
    .frame(height: ForestLayout.heroHeight(after))
    .padding(DL.Space.xl)
    .background(Color.dlBackground)
}

/// An area packed and not yet opened: still a seedling, and nothing hangs.
#Preview("An area only packed") {
    let packed = AreaTree(id: "bath", emoji: "🛁", title: "Das Bad",
                          leaves: 0, blossoms: 0, fruit: 0, growing: 12, fallen: 0,
                          mass: 0, tendedToday: true)
    return GrowingTreeView(transition: TreeTransition(before: packed, after: packed),
                           progress: 1)
        .frame(height: ForestLayout.heroHeight(packed))
        .padding(DL.Space.xl)
        .background(Color.dlBackground)
}
