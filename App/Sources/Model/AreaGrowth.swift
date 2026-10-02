import Foundation
import SprossKern

// MARK: - Area trees
//
// Kern tallies each area into its tiers (`growthByArea`) and names what a round moved
// (`TreeTransition`, `grownArea`); this reads them in the Box screen's order.
// Both the Trees picture on Home and the single tree a round's summary draws read these,
// so they can never disagree about what an area looks like.

extension AppModel {

    /// One tree per area the box holds, in the Box screen's own order
    /// (`areaNames` — catalog groups top to bottom, own words last).
    /// Held on the model as `trees`: it walks every card in the join,
    /// and the Trees picture asks for it on every redraw.
    func composedAreaGrowth() -> [AreaGrowth] {
        let byArea = growthByAreaName()
        return areaNames.compactMap { byArea[$0] }
    }

    private func growthByAreaName() -> [String: AreaGrowth] {
        guard let box else { return [:] }
        return growthByArea(state: box, growth: growth)
    }
}

/// Which tier becomes which mark — the drawing's reading of kern's tiers,
/// taken once per placed tree: a Kotlin list crosses the bridge as a copy
/// on every read, and the canopy is walked mark by mark.
///
/// Drawn outermost rank first:
///   fruit   — long held (`longHeld`)
///   blossom — matured
///   leaf    — growing
///   bud     — met, on its way in (`arriving`)
struct Canopy {
    let fruit: Int
    let blossoms: Int
    let leaves: Int
    let buds: Int
    let fallen: Int
    let tendedToday: Bool
    let isBare: Bool
    /// How far each mark's word has come, 0…1, one per mark in rank order.
    let reaches: [Double]

    init(_ tree: AreaGrowth) {
        fruit = Int(tree.longHeld)
        blossoms = Int(tree.matured)
        leaves = Int(tree.growing)
        buds = Int(tree.arriving)
        fallen = Int(tree.lapsed)
        tendedToday = tree.answeredToday
        isBare = tree.isBare
        reaches = tree.reaches.map(\.doubleValue)
    }

    var count: Int { fruit + blossoms + leaves + buds }

    /// The word at `rank`'s reach, or a middling one past the list.
    func reach(_ rank: Int) -> Double { rank < reaches.count ? reaches[rank] : 0.4 }
}

extension AreaGrowth {
    /// A tree built by hand — the previews and the fabricated DEBUG box (`SampleTrees`).
    static func sample(_ area: String, leaves: Int = 0, blossoms: Int = 0, fruit: Int = 0,
                       buds: Int = 0, packed: Int = 0, fallen: Int = 0, mass: Double,
                       tendedToday: Bool = false, reaches: [Double] = []) -> AreaGrowth {
        AreaGrowth(area: area, arriving: Int32(buds), growing: Int32(leaves),
                 matured: Int32(blossoms), longHeld: Int32(fruit), queued: Int32(packed),
                 lapsed: Int32(fallen), mass: mass, answeredToday: tendedToday,
                 reaches: reaches.map { KotlinDouble(value: $0) })
    }
}
