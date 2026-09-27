import Foundation

// MARK: - A fabricated orchard
//
// A box at any age, without months of reviews behind it —
// what the previews draw, and what a DEBUG launch with
// `-uitest-orchard <age>` stands on Home and on a round's summary.

enum SampleOrchard {
    private static let areas: [(String, String, String, Int)] = [
        ("basics", "👋", "Die ersten Wörter", 27), ("essentials", "⭐", "Alltag", 62),
        ("connectors", "🔗", "Verbindungswörter", 15), ("questions", "❓", "Fragewörter", 10),
        ("kitchen", "🍳", "Die Küche", 41), ("living", "🛋️", "Wohnzimmer", 36),
        ("bath", "🛁", "Bad", 39), ("bedroom", "🛏️", "Schlafzimmer", 37),
        ("desk", "✏️", "Schreibtisch", 39), ("hall", "🚪", "Flur", 40),
        ("outside", "🌳", "Draußen", 41), ("school", "🎒", "Schule", 33),
        ("organization", "🗒️", "Termine", 21), ("admin", "🗂️", "Amt", 38),
        ("health", "🩺", "Gesundheit", 36), ("work", "💼", "Arbeit", 38),
        ("own", "📦", "Eigene Wörter", 4),
    ]

    /// Every area at `age`, 0…1 — areas fill in catalog order,
    /// so an age walks the box the way growth does.
    static func trees(age: Double) -> [AreaTree] {
        areas.enumerated().map { index, area in
            let reached = max(0.0, min(1.0, age * Double(areas.count) - Double(index)))
            return tree(area, reached: reached, index: index)
        }
    }

    /// The kitchen as a round at `age` leaves it —
    /// a large area at the same age the orchard stands at.
    static func round(age: Double) -> TreeTransition {
        let kitchen = areas[4]
        return TreeTransition(before: tree(kitchen, reached: max(0, age - 0.08), index: 4),
                              after: tree(kitchen, reached: age, index: 4, tended: true))
    }

    private static func tree(_ area: (String, String, String, Int), reached: Double,
                             index: Int, tended: Bool? = nil) -> AreaTree {
        let (id, emoji, title, total) = area
        let started = Int(Double(total) * min(1, reached * 1.3))
        let settled = Int(Double(started) * max(0, reached - 0.25))
        let blossoms = Int(Double(settled) * max(0, reached - 0.55))
        let fruit = Int(Double(blossoms) * max(0, reached - 0.8))
        let reaches = (0..<started).map { rank in
            max(0, reached - Double(rank) / Double(max(started, 1)) * 0.6)
        }
        return AreaTree(
            id: id, emoji: emoji, title: title,
            leaves: settled - blossoms, blossoms: blossoms - fruit, fruit: fruit,
            buds: started - settled, growing: 0,
            fallen: reached > 0.3 && index % 3 == 0 ? 2 : 0,
            mass: Double(settled) * 0.35 + Double(blossoms) * 0.6 + Double(fruit),
            tendedToday: tended ?? (index % 5 == 2 && reached > 0),
            reaches: reaches
        )
    }
}
