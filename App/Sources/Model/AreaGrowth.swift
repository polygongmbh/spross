import Foundation
import SprossKern

// MARK: - Area trees
//
// Kern tallies each area into its stages (`growthByArea`) and names what a round moved
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

    /// The garden every tree grows in (`AreaTree.garden`).
    var garden: String {
        AreaTree.shared.garden(learnerName: learnerName, target: targetLanguage ?? "")
    }

    private func growthByAreaName() -> [String: AreaGrowth] {
        guard let box else { return [:] }
        return growthByArea(state: box, growth: growth)
    }
}

/// Which stage becomes which mark — the drawing's reading of kern's stages,
/// taken once per placed tree: a Kotlin list crosses the bridge as a copy
/// on every read, and the canopy is walked mark by mark.
///
/// Drawn outermost rank first:
///   fruit   — matured
///   blossom — settled
///   leaf    — growing
///   bud     — met, on its way in (fresh)
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
        fruit = Int(tree.stages.matured)
        blossoms = Int(tree.stages.settled)
        leaves = Int(tree.stages.growing)
        buds = Int(tree.stages.fresh)
        fallen = Int(tree.stages.lapsed)
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
                       buds: Int = 0, queued: Int = 0, fallen: Int = 0,
                       tendedToday: Bool = false, reaches: [Double] = []) -> AreaGrowth {
        AreaGrowth(area: area,
                   stages: StageCounts(fresh: Int32(buds), growing: Int32(leaves), lapsed: Int32(fallen),
                                       settled: Int32(blossoms), matured: Int32(fruit)),
                   queued: Int32(queued), answeredToday: tendedToday,
                   reaches: reaches.map { KotlinDouble(value: $0) })
    }
}
