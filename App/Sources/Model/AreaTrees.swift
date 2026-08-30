import Foundation
import SprossKern

// The box as trees: kern names how far each word has come (`GrowthStage`), and
// this is the one place that decides which rung becomes which mark. Both the
// forest on Heute and the single tree a session summary draws read it, so they
// can never disagree about what an area looks like.

extension AppModel {

    /// One tree per area the box holds, in the Box screen's own order
    /// (`areaNames` — catalog groups top to bottom, own words last).
    /// Held on the model as `trees`: it walks every card in the join and sorts
    /// each area's reaches, and the forest asks for it on every redraw.
    func composedAreaTrees() -> [AreaTree] {
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
    var buds = 0
    var growing = 0
    var fallen = 0
    var mass = 0.0
    var tendedToday = false
    /// One entry per canopy word — what each has come to, for the mark that
    /// stands for it.
    var reaches: [Double] = []

    /// A word is fruit only once it is well past the matured bar. Blossom is
    /// meant to be the rare thing on a tree — mapping it to `consolidated`, the
    /// state most of a worked area sits in, turned every grown tree pink.
    static let fruitStability = 120.0

    mutating func add(_ entry: CardGrowth, maximumInterval: Double) {
        if entry.touchedToday { tendedToday = true }
        // why: mass is what the trunk is made of, so every word that has come
        // anywhere counts toward it — a settled word carries more of the tree
        // than one met yesterday, and a word never opened carries none of it.
        let reach = entry.reach(maximumIntervalDays: maximumInterval)
        mass += reach
        switch entry.stage {
        case .unscheduled: break
        // Packed and not yet opened: the tree is why it is growing, and nothing
        // hangs on it — a word the learner has not met cannot be on the tree.
        case .queued: growing += 1
        // Met, still on its way in. A bud, because the round that introduces a
        // word has to be able to point at what it put there: with these drawn as
        // nothing, a first round in an area moved the picture not at all.
        case .learning, .fresh:
            buds += 1
            reaches.append(reach)
        // The canopy is green because most of a worked area IS green: words that
        // have landed are the bulk of any box that is being used.
        case .consolidated:
            leaves += 1
            reaches.append(reach)
        case .matured:
            if entry.stability >= Self.fruitStability { fruit += 1 } else { blossoms += 1 }
            reaches.append(reach)
        case .relearning: fallen += 1
        // A word the box has taken out of rotation is owed no space in the
        // picture; waking it lives on its row in the Box screen.
        case .suspended: break
        }
    }

    func tree(id: String, emoji: String, title: String) -> AreaTree {
        AreaTree(id: id, emoji: emoji, title: title,
                 leaves: leaves, blossoms: blossoms, fruit: fruit, buds: buds,
                 growing: growing, fallen: fallen, mass: mass, tendedToday: tendedToday,
                 // why: most-grown first, which is the order the canopy draws
                 // its marks in — fruit at the rim, then blossom, leaves, buds
                 // — so entry n belongs to mark n. The tiers ARE stability
                 // bands, so sorting by reach reproduces them.
                 reaches: reaches.sorted(by: >))
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
