import SwiftUI

// MARK: - ForestCanvas
//
// The box as one picture: a tree per area, standing in rows on shared ground.
//
// Drawn as ONE Canvas, never a view per tree — the same call ConfettiView makes.
// Nothing is stored: every measure comes from the area's own counts, and what
// little jitter there is comes from a hash of the area name, so the forest is
// identical on every redraw and survives a relaunch unchanged.
//
// The forest never animates. A box grows over weeks, and motion would claim a
// change the picture is not showing — which also means there is nothing here
// for Reduce Motion to switch off.
//
// The canvas is hidden from accessibility. Each tree carries a real button on
// the very cell the layout gave it, so what a sighted learner taps and what
// VoiceOver reads are one element, and the counts are spoken rather than left
// to color.

struct ForestCanvas: View {
    let trees: [AreaTree]
    /// What tapping a tree does. Nil leaves the forest a picture.
    var open: ((String) -> Void)?
    /// The spoken description of one area — the screen's to write, since it
    /// alone knows what the counts are called.
    var describe: ((AreaTree) -> Text)?

    /// The width to lay out in. Taken from the environment rather than
    /// measured: a Canvas has to be given a height, the height falls out of the
    /// layout, and the layout needs the width first — so the screen states it
    /// once and both the picture and its buttons are placed against one number.
    @Environment(\.dlContentWidth) private var width

    var body: some View {
        let marks = ForestLayout.marks(trees, width: width)
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                for mark in marks { TreeShapes.draw(&context, mark) }
            }
            .accessibilityHidden(true)
            ForEach(marks, id: \.tree.id) { mark in
                label(mark)
            }
        }
        // why: from the marks already laid out — asking `ForestLayout.height`
        // would lay the whole forest out a second time for the same number.
        .frame(width: width, height: ForestLayout.height(of: marks), alignment: .topLeading)
    }

    /// The area's emoji under its tree — the identity the catalog already owns,
    /// and the only text small enough to sit under a 58pt cell. The name itself
    /// is in the accessibility label and on the screen the tree opens.
    private func label(_ mark: TreeMark) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            Text(verbatim: mark.tree.emoji)
                .font(.system(size: 13))
                .opacity(mark.tree.isBare ? 0.4 : 1)
                .accessibilityHidden(true)
                .frame(height: ForestLayout.labelHeight)
        }
        .frame(width: mark.cell.width, height: mark.cell.height)
        .contentShape(Rectangle())
        .offset(x: mark.cell.minX, y: mark.cell.minY)
        .onTapGesture { open?(mark.tree.id) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(describe?(mark.tree) ?? Text(mark.tree.title))
        .accessibilityAddTraits(open == nil ? [] : .isButton)
    }
}

// MARK: - Content width

private struct DLContentWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat = 320
}

extension EnvironmentValues {
    /// The width a section may actually draw in — the screen's width less its
    /// own padding. Set by the screen; read by anything that has to know its
    /// height before it is laid out.
    var dlContentWidth: CGFloat {
        get { self[DLContentWidthKey.self] }
        set { self[DLContentWidthKey.self] = newValue }
    }
}

// MARK: - Previews

/// A fabricated box at a given age — the only way to see a grown forest
/// without months of reviews behind it.
private func sampleTrees(age: Double) -> [AreaTree] {
    let areas = [("basics", "👋", "Die ersten Wörter", 27), ("essentials", "⭐", "Alltag", 62),
                 ("connectors", "🔗", "Verbindungswörter", 15), ("questions", "❓", "Fragewörter", 10),
                 ("kitchen", "🍳", "Die Küche", 41), ("living", "🛋️", "Wohnzimmer", 36),
                 ("bath", "🛁", "Bad", 39), ("bedroom", "🛏️", "Schlafzimmer", 37),
                 ("desk", "✏️", "Schreibtisch", 39), ("hall", "🚪", "Flur", 40),
                 ("outside", "🌳", "Draußen", 41), ("school", "🎒", "Schule", 33),
                 ("organization", "🗒️", "Termine", 21), ("admin", "🗂️", "Amt", 38),
                 ("health", "🩺", "Gesundheit", 36), ("work", "💼", "Arbeit", 38),
                 ("own", "📦", "Eigene Wörter", 4)]
    return areas.enumerated().map { index, area in
        let (id, emoji, title, total) = area
        // Areas fill in catalog order, so an "age" walks the box the way growth does.
        let reached = max(0.0, min(1.0, age * Double(areas.count) - Double(index)))
        let started = Int(Double(total) * min(1, reached * 1.3))
        let settled = Int(Double(started) * max(0, reached - 0.25))
        let blossoms = Int(Double(settled) * max(0, reached - 0.55))
        let fruit = Int(Double(blossoms) * max(0, reached - 0.8))
        return AreaTree(
            id: id, emoji: emoji, title: title,
            leaves: settled - blossoms, blossoms: blossoms - fruit, fruit: fruit,
            buds: started - settled, growing: 0,
            fallen: reached > 0.3 && index % 3 == 0 ? 2 : 0,
            mass: Double(settled) * 0.35 + Double(blossoms) * 0.6 + Double(fruit),
            tendedToday: index % 5 == 2 && reached > 0
        )
    }
}

private struct ForestPreview: View {
    let age: Double

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            ForestCanvas(trees: sampleTrees(age: age), open: { _ in })
        }
        .padding(Theme.spacing.xl)
        .environment(\.dlContentWidth, 402 - Theme.spacing.xl * 2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.colors.background)
    }
}

#Preview("Forest · untouched") { ForestPreview(age: 0) }

#Preview("Forest · first weeks") { ForestPreview(age: 0.18) }

#Preview("Forest · a working box") { ForestPreview(age: 0.55) }

#Preview("Forest · a grown box") { ForestPreview(age: 1.0) }

#Preview("Forest · dark") { ForestPreview(age: 0.55).preferredColorScheme(.dark) }
