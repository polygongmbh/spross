import Foundation
import SprossKern

// MARK: - A fabricated box of trees
//
// A box at any age, without months of reviews behind it —
// what a DEBUG launch with `-uitest-trees <age>` stands on Home and on a round's summary.

enum SampleTrees {
    /// Catalog areas, so a DEBUG launch labels them with the catalog's own emoji.
    private static let areas: [(String, Int)] = [
        ("greetings", 27), ("people", 62), ("connectors", 15), ("questions", 10),
        ("kitchen", 41), ("living", 36), ("bath", 39), ("bedroom", 37), ("desk", 39),
        ("hall", 40), ("nature", 41), ("school", 33), ("organization", 21),
        ("admin", 38), ("doctor", 36), ("work", 38), ("food", 4),
    ]

    /// Every area at `age`, 0…1 — areas fill in catalog order,
    /// so an age walks the box the way growth does.
    static func trees(age: Double) -> [AreaGrowth] {
        areas.enumerated().map { index, area in
            let reached = max(0.0, min(1.0, age * Double(areas.count) - Double(index)))
            return tree(area, reached: reached, index: index)
        }
    }

    /// The kitchen as a round at `age` leaves it —
    /// a large area at the same age the other trees stand at.
    static func round(age: Double) -> TreeTransition {
        let kitchen = areas[4]
        return TreeTransition(before: tree(kitchen, reached: max(0, age - 0.08), index: 4),
                              after: tree(kitchen, reached: age, index: 4, tended: true))
    }

    private static func tree(_ area: (String, Int), reached: Double,
                             index: Int, tended: Bool? = nil) -> AreaGrowth {
        let (id, total) = area
        let started = Int(Double(total) * min(1, reached * 1.3))
        let settled = Int(Double(started) * max(0, reached - 0.25))
        let blossoms = Int(Double(settled) * max(0, reached - 0.55))
        let fruit = Int(Double(blossoms) * max(0, reached - 0.8))
        let strengths = (0..<started).map { rank in
            max(0, reached - Double(rank) / Double(max(started, 1)) * 0.6)
        }
        return AreaGrowth.sample(
            id, leaves: settled - blossoms, blossoms: blossoms - fruit, fruit: fruit,
            buds: started - settled, fallen: reached > 0.3 && index % 3 == 0 ? 2 : 0,
            tendedToday: tended ?? (index % 5 == 2 && reached > 0),
            strengths: strengths
        )
    }
}
