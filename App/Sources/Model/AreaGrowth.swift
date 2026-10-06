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

extension AreaGrowth {
    /// A tree built by hand — the previews and the fabricated DEBUG box (`SampleTrees`).
    static func sample(_ area: String, leaves: Int = 0, blossoms: Int = 0, fruit: Int = 0,
                       buds: Int = 0, queued: Int = 0, fallen: Int = 0,
                       tendedToday: Bool = false, strengths: [Double] = []) -> AreaGrowth {
        AreaGrowth(area: area,
                   stages: StageCounts(fresh: Int32(buds), growing: Int32(leaves), lapsed: Int32(fallen),
                                       settled: Int32(blossoms), matured: Int32(fruit)),
                   queued: Int32(queued), answeredToday: tendedToday,
                   strengths: strengths.map { KotlinDouble(value: $0) })
    }
}
