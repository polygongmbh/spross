import Foundation
import SprossKern

// The box as trees: kern names how far each word has come (`GrowthStage`), and
// this is the one place that decides which rung becomes which mark. Both the
// forest on Heute and the single tree a session summary draws read it, so they
// can never disagree about what an area looks like.

extension AppModel {

    /// One tree per area the box holds, in the Box screen's own order
    /// (`areaNames` — catalog groups top to bottom, own words last).
    var areaTrees: [AreaTree] {
        let counts = growthByArea()
        return areaNames.compactMap { area in
            counts[area]?.tree(id: area, emoji: areaEmoji(area), title: areaTitle(area))
        }
    }

    /// One area's tree — what a session summary draws for the area it worked.
    func areaTree(_ area: String) -> AreaTree? {
        growthByArea()[area]?.tree(id: area, emoji: areaEmoji(area), title: areaTitle(area))
    }

    private func growthByArea() -> [String: AreaGrowth] {
        guard let box else { return [:] }
        let maximumInterval = Double(box.config.maximumIntervalDays)
        var byArea: [String: AreaGrowth] = [:]
        for entry in growth {
            guard let card = box.cards[entry.cardId] else { continue }
            byArea[card.area, default: AreaGrowth()].add(entry, maximumInterval: maximumInterval)
        }
        return byArea
    }
}

/// One area's cards, tallied into the marks its tree is made of.
struct AreaGrowth {
    var leaves = 0
    var blossoms = 0
    var fruit = 0
    var growing = 0
    var fallen = 0
    var mass = 0.0
    var tendedToday = false

    /// A word is fruit only once it is well past the matured bar. Blossom is
    /// meant to be the rare thing on a tree — mapping it to `consolidated`, the
    /// state most of a worked area sits in, turned every grown tree pink.
    static let fruitStability = 120.0

    mutating func add(_ entry: CardGrowth, maximumInterval: Double) {
        if entry.touchedToday { tendedToday = true }
        // why: mass is what the trunk is made of, so every word that has come
        // anywhere counts toward it — a settled word carries more of the tree
        // than one met yesterday, and a word never opened carries none of it.
        mass += entry.reach(maximumIntervalDays: maximumInterval)
        switch entry.stage {
        case .unscheduled: break
        case .queued, .learning, .fresh: growing += 1
        // The canopy is green because most of a worked area IS green: settled
        // and consolidated words are the bulk of any box that is being used.
        case .settled, .consolidated: leaves += 1
        case .matured:
            if entry.stability >= Self.fruitStability { fruit += 1 } else { blossoms += 1 }
        case .relearning: fallen += 1
        // A word the box has taken out of rotation is owed no space in the
        // picture; waking it lives on its row in the Box screen.
        case .suspended: break
        }
    }

    func tree(id: String, emoji: String, title: String) -> AreaTree {
        AreaTree(id: id, emoji: emoji, title: title,
                 leaves: leaves, blossoms: blossoms, fruit: fruit,
                 growing: growing, fallen: fallen, mass: mass, tendedToday: tendedToday)
    }
}

extension CardGrowth {
    /// How far one word has come, 0…1 — what it contributes to its area's tree.
    ///
    /// Logarithmic, because stability grows multiplicatively: on a linear scale
    /// every word short of a year out would count the same, and the difference
    /// between a week and a month — the part the learner actually lives through
    /// — would be invisible.
    func reach(maximumIntervalDays: Double) -> Double {
        guard stability > 1, maximumIntervalDays > 1 else { return 0 }
        return min(1, log(stability) / log(maximumIntervalDays))
    }
}
