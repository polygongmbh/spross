import CoreGraphics
import Foundation
import SprossKern

// MARK: - Tree skeleton
//
// Kern's grown tree (`AreaTreeLayout.grow`) as Swift values, placed in points for drawing.

/// One length of branch: a bowed center line that tapers along its length.
struct TreeSegment {
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint
    let startWidth: CGFloat
    let endWidth: CGFloat
    let depth: Int
    /// The segment this one grows from; nil for the trunk.
    let parent: Int?
}

/// Somewhere a mark hangs, and the way the mark faces.
struct LeafSlot {
    let point: CGPoint
    /// Outward from the twig it hangs off.
    let angle: Double
    /// The index of that twig in `segments`.
    let segment: Int
}

struct TreeSkeleton {
    let segments: [TreeSegment]
    /// One slot per mark, in rank order: the render hangs fruit first, then blossom, leaf, bud.
    let slots: [LeafSlot]
    /// The typical gap between neighboring marks — what a mark is sized against.
    let pitch: CGFloat

    /// The tree `area` grows to carry `marks` marks, fitted with its foot on `foot`, `height` tall.
    static func grown(area: String, marks: Int, foot: CGPoint, height: CGFloat) -> TreeSkeleton {
        guard marks > 0 else { return TreeSkeleton(segments: [], slots: [], pitch: 1) }
        let unit = UnitTrees.shared.tree(area: area, marks: marks)
        return unit.skeleton.placed(unit.grown.fit(footX: foot.x, footY: foot.y, height: height))
    }

    private func placed(_ fit: TreeFit) -> TreeSkeleton {
        let scale = CGFloat(fit.scale), x = CGFloat(fit.x), y = CGFloat(fit.y)
        func at(_ point: CGPoint) -> CGPoint { CGPoint(x: x + point.x * scale, y: y + point.y * scale) }
        return TreeSkeleton(
            segments: segments.map {
                TreeSegment(start: at($0.start), control: at($0.control), end: at($0.end),
                            startWidth: $0.startWidth * scale, endWidth: $0.endWidth * scale,
                            depth: $0.depth, parent: $0.parent)
            },
            slots: slots.map { LeafSlot(point: at($0.point), angle: $0.angle, segment: $0.segment) },
            pitch: pitch * scale)
    }

    fileprivate init(segments: [TreeSegment], slots: [LeafSlot], pitch: CGFloat) {
        self.segments = segments
        self.slots = slots
        self.pitch = pitch
    }

    /// Kern's unit-space tree as Swift arrays.
    fileprivate init(_ grown: GrownTree) {
        segments = grown.limbs.map {
            TreeSegment(start: CGPoint(x: $0.startX, y: $0.startY),
                        control: CGPoint(x: $0.controlX, y: $0.controlY),
                        end: CGPoint(x: $0.endX, y: $0.endY),
                        startWidth: $0.startWidth, endWidth: $0.endWidth,
                        depth: Int($0.depth), parent: $0.parent < 0 ? nil : Int($0.parent))
        }
        slots = grown.slots.map {
            LeafSlot(point: CGPoint(x: $0.x, y: $0.y), angle: $0.angle, segment: Int($0.limb))
        }
        pitch = grown.pitch
    }
}

/// Unit-space trees by (area, marks), each converted once:
/// a Kotlin list crosses the bridge as a copy on every read, and a rising tree is placed every frame.
private final class UnitTrees: @unchecked Sendable {
    static let shared = UnitTrees()

    struct Entry {
        let grown: GrownTree
        let skeleton: TreeSkeleton
    }

    private let lock = NSLock()
    private var entries: [String: Entry] = [:]

    func tree(area: String, marks: Int) -> Entry {
        let key = "\(area)|\(marks)"
        lock.lock()
        defer { lock.unlock() }
        if let entry = entries[key] { return entry }
        // why: a box's trees and the counts they pass through stay well under this; a cleared cache only regrows.
        if entries.count >= 512 { entries.removeAll() }
        let grown = AreaTreeLayout.shared.grow(area: area, marks: Int32(marks))
        let entry = Entry(grown: grown, skeleton: TreeSkeleton(grown))
        entries[key] = entry
        return entry
    }
}
