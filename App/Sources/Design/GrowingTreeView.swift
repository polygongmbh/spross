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
            let shown = transition.at(progress)
            // why: the tree is SIZED for its finished state and only its marks
            // move — a tree that grew its own frame would shift under the eye
            // while the thing being watched is what landed in the canopy.
            let mark = ForestLayout.solitary(transition.after, in: size)
            TreeShapes.draw(&context, geometry(mark, shown), showing: shown)
        }
        .accessibilityHidden(true)
    }

    /// The mark at this moment's height, keeping the finished tree's identity
    /// so the skeleton — and therefore every slot — stays put.
    private func geometry(_ mark: TreeMark, _ shown: AreaTree) -> TreeMark {
        let full = ForestLayout.treeHeight(transition.after)
        let now = ForestLayout.treeHeight(shown)
        return TreeMark(tree: mark.tree, foot: mark.foot,
                        height: mark.height * (full > 0 ? now / full : 0),
                        cell: mark.cell, baseline: mark.baseline)
    }
}

// MARK: - Previews

#Preview("A round's growth") {
    let before = AreaTree(id: "kitchen", emoji: "🍳", title: "Die Küche",
                          leaves: 18, blossoms: 2, fruit: 1, growing: 9, fallen: 1,
                          mass: 14, tendedToday: false)
    let after = AreaTree(id: "kitchen", emoji: "🍳", title: "Die Küche",
                         leaves: 22, blossoms: 4, fruit: 2, growing: 6, fallen: 1,
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

#Preview("A first round in a new area") {
    let after = AreaTree(id: "bath", emoji: "🛁", title: "Das Bad",
                         leaves: 3, blossoms: 0, fruit: 0, growing: 5, fallen: 0,
                         mass: 1.6, tendedToday: true)
    return GrowingTreeView(
        transition: TreeTransition(
            before: AreaTree(id: "bath", emoji: "🛁", title: "Das Bad",
                             leaves: 0, blossoms: 0, fruit: 0, growing: 0, fallen: 0,
                             mass: 0, tendedToday: false),
            after: after),
        progress: 1
    )
    .frame(height: 200)
    .padding(DL.Space.xl)
    .background(Color.dlBackground)
}
