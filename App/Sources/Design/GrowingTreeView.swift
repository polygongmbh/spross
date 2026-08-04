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
    let tree: AreaTree
    /// 0 = level ground, 1 = the tree at its full size.
    var grown: Double

    // why: `View` is main-actor isolated but SwiftUI interpolates this off it,
    // so the conformance has to step outside the actor (Swift 6 strict).
    nonisolated var animatableData: Double {
        get { grown }
        set { grown = newValue }
    }

    var body: some View {
        Canvas { context, size in
            let mark = ForestLayout.solitary(tree, in: size)
            TreeShapes.draw(&context, ForestLayout.revealed(mark, grown))
        }
        .accessibilityHidden(true)
    }
}

// MARK: - Previews

#Preview("Growing tree") {
    let tree = AreaTree(id: "kitchen", emoji: "🍳", title: "Die Küche",
                        leaves: 22, blossoms: 4, fruit: 2, growing: 6, fallen: 1,
                        mass: 18, tendedToday: true)
    return VStack(spacing: DL.Space.xl) {
        GrowingTreeView(tree: tree, grown: 0.35)
        GrowingTreeView(tree: tree, grown: 1)
    }
    .frame(height: 420)
    .padding(DL.Space.xl)
    .background(Color.dlBackground)
}
