import SwiftUI
import SprossKern

// MARK: - OrchardCanvas
//
// The box as one picture:
// a tree per area, standing in rows on shared ground.
//
// Drawn as ONE Canvas, never a view per tree —
// the same call ConfettiView makes.
// Nothing is stored:
// every measure comes from the area's own counts,
// and what little jitter there is comes from a hash of the area name,
// so the orchard is identical on every redraw
// and survives a relaunch unchanged.
//
// The orchard never animates.
// A box grows over weeks,
// and motion would claim a change the picture is not showing —
// which also means there is nothing here for Reduce Motion to switch off.
//
// The canvas is hidden from accessibility.
// Each tree carries a real button on the very cell the layout gave it,
// so what a sighted learner taps and what VoiceOver reads are one element,
// and the counts are spoken rather than left to color.

struct OrchardCanvas: View {
    let trees: [AreaTree]
    /// The emoji an area wears under its tree.
    let emoji: (String) -> String
    /// What tapping a tree does.
    /// Nil leaves the orchard a picture.
    var open: ((String) -> Void)?
    /// The spoken description of one area —
    /// the screen's to write,
    /// since it alone knows what the counts are called.
    var describe: ((AreaTree) -> Text)?

    /// The width to lay out in.
    /// Taken from the environment rather than measured:
    /// a Canvas has to be given a height,
    /// the height falls out of the layout,
    /// and the layout needs the width first —
    /// so the screen states it once
    /// and both the picture and its buttons are placed against one number.
    @Environment(\.contentWidth) private var width

    var body: some View {
        let marks = OrchardLayout.marks(trees, width: width)
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                // why: the emoji is drawn WITH its own tree, in the one back-to-front
                // order the marks carry — drawn afterwards it sat on top of the whole
                // orchard, and a tree standing in front of an area was labeled through.
                for mark in marks {
                    TreeShapes.draw(&context, mark)
                    label(&context, mark)
                }
            }
            .accessibilityHidden(true)
            ForEach(marks, id: \.tree.area) { mark in
                tapTarget(mark)
            }
        }
        // why: from the marks already laid out —
        // asking `OrchardLayout.height` would lay the whole orchard out
        // a second time for the same number.
        .frame(width: width, height: OrchardLayout.height(of: marks), alignment: .topLeading)
    }

    /// The area's emoji, on the strip of ground under its own tree —
    /// the identity the catalog already owns,
    /// and the only text small enough to sit under a 58pt cell.
    /// The name itself is in the accessibility label
    /// and on the screen the tree opens.
    private func label(_ context: inout GraphicsContext, _ mark: TreeMark) {
        let text = Text(verbatim: emoji(mark.tree.area))
            .font(.system(size: 13)) // card-parity: a mark under a 58pt cell, below every type role
        let at = CGPoint(x: mark.foot.x, y: mark.baseline + OrchardLayout.labelHeight / 2)
        guard mark.canopy.isBare else { return context.draw(text, at: at, anchor: .center) }
        context.drawLayer { faded in
            faded.opacity = 0.4
            faded.draw(text, at: at, anchor: .center)
        }
    }

    /// What a learner taps, and what VoiceOver reads: one invisible element
    /// on the cell the layout gave the tree, carrying everything the picture
    /// itself cannot say.
    private func tapTarget(_ mark: TreeMark) -> some View {
        Color.clear
            .frame(width: mark.cell.width, height: mark.cell.height)
            .contentShape(Rectangle())
            .offset(x: mark.cell.minX, y: mark.cell.minY)
            .onTapGesture { open?(mark.tree.area) }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(describe?(mark.tree) ?? Text(verbatim: mark.tree.area))
            .accessibilityAddTraits(open == nil ? [] : .isButton)
    }
}

// MARK: - Content width

private struct ContentWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat = 320
}

extension EnvironmentValues {
    /// The width a section may actually draw in —
    /// the screen's width less its own padding.
    /// Set by the screen;
    /// read by anything that has to know its height before it is laid out.
    var contentWidth: CGFloat {
        get { self[ContentWidthKey.self] }
        set { self[ContentWidthKey.self] = newValue }
    }
}

// MARK: - Previews

private struct OrchardPreview: View {
    let age: Double

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            OrchardCanvas(trees: SampleOrchard.trees(age: age), emoji: SampleOrchard.emoji, open: { _ in })
        }
        .padding(Theme.spacing.xl)
        .environment(\.contentWidth, 402 - Theme.spacing.xl * 2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.colors.background)
    }
}

#Preview("Orchard · untouched") { OrchardPreview(age: 0) }

#Preview("Orchard · first weeks") { OrchardPreview(age: 0.18) }

#Preview("Orchard · a working box") { OrchardPreview(age: 0.55) }

#Preview("Orchard · a grown box") { OrchardPreview(age: 1.0) }

#Preview("Orchard · dark") { OrchardPreview(age: 0.55).preferredColorScheme(.dark) }
