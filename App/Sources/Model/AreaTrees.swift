import Foundation
import SprossKern

// MARK: - Area tree data
//
// One tree per area, standing in rows.
// The unit is the AREA, not the card.
// A word is a leaf, not a character:
// five hundred individual plants can only ever be read as texture,
// and drawing them as objects made a freshly packed area look like a
// spilled bag rather than like sowing.
// Sixteen trees can each be looked at.
//
// Every tree in a row stands on ONE baseline,
// so their heights compare directly.
// That comparison is the picture:
// a row is a skyline, and unlike per-card patches —
// sized by how many words the CATALOG holds,
// and so identical on install day and a year in —
// a skyline changes shape as the box grows.

/// One area's tree, in the terms the drawing needs: counts of marks, not cards.
struct AreaTree: Identifiable {
    let id: String
    let emoji: String
    let title: String
    /// Words that have reached the canopy,
    /// split by how far each has come.
    /// A word is exactly one mark — leaf, then blossom, then fruit —
    /// so the canopy is made of the words themselves
    /// and nothing is counted twice.
    let leaves: Int
    let blossoms: Int
    let fruit: Int
    /// Words the learner has met that have not settled yet —
    /// the smallest mark the canopy carries.
    /// A word gets one from its first answer,
    /// so a round in a new area has something to hang.
    var buds: Int = 0
    /// Words packed and not yet met —
    /// why the tree is growing at all,
    /// and nothing that hangs on it.
    let growing: Int
    /// Words that lapsed: a couple of leaves on the ground, never a smaller tree.
    let fallen: Int
    /// The area's aggregate growth, in words-worth-of-stability.
    let mass: Double
    /// Something here was answered today.
    let tendedToday: Bool
    /// How far each canopy word has come, 0…1, most-grown first —
    /// one entry per mark, in the order the marks are drawn.
    /// The canopy's marks take their SIZE from these,
    /// so a leaf is the size of the word it stands for
    /// rather than of a hash:
    /// within one tier a word held for a week and one held for a month
    /// stop drawing identically.
    var reaches: [Double] = []

    /// Whether anything at all has happened here.
    /// A bare area draws as bare ground, NOT as empty slots:
    /// a catalog the learner never chose must not read as a list of
    /// things they have failed to do.
    var isBare: Bool { canopyCount + growing == 0 }

    /// Everything standing in the canopy.
    var canopyCount: Int { leaves + blossoms + fruit + buds }
}

/// One area's tree before and after something happened to it —
/// a finished round, most often.
/// The FINISHED tree is what gets drawn;
/// what the transition says is which of its marks the round itself put there,
/// so the animation can hand those the motion
/// and leave the rest of the crown standing still.
struct TreeTransition {
    let before: AreaTree
    let after: AreaTree

    /// Where the animation starts:
    /// `before`, but with no tier holding more than it ends with.
    ///
    /// why: a round can take a mark off the tree —
    /// a word that lapsed leaves the canopy,
    /// and a word that matures moves from one tier to the next.
    /// Played forward those read as marks being removed,
    /// and a summary that takes something away in front of the learner
    /// is the wrong screen for it.
    /// A tier that shrank simply starts where it ends;
    /// what the round ADDED still animates,
    /// and what it cost is still there in the finished picture.
    private var start: AreaTree {
        AreaTree(id: before.id, emoji: before.emoji, title: before.title,
                 leaves: min(before.leaves, after.leaves),
                 blossoms: min(before.blossoms, after.blossoms),
                 fruit: min(before.fruit, after.fruit),
                 buds: min(before.buds, after.buds),
                 growing: min(before.growing, after.growing),
                 fallen: min(before.fallen, after.fallen),
                 mass: min(before.mass, after.mass),
                 tendedToday: before.tendedToday)
    }

    /// How many marks were already hanging when the round began.
    /// From this rank on, a mark is one the round itself hung —
    /// it has to arrive out of nothing,
    /// where a mark below this rank was already there and only changed.
    var settledCount: Int { start.canopyCount }

    /// The canopy ranks this round moved, in canopy order:
    /// a mark that appeared,
    /// and a mark that changed tier where it hangs —
    /// a word maturing pushes the blossom boundary out by one,
    /// so the leaf at that rank becomes a blossom
    /// without anything else on the tree shifting.
    var changedRanks: [Int] {
        let was = start
        return (0..<after.canopyCount).filter { tier($0, was) != tier($0, after) }
    }

    /// What a rank draws as: nothing, or one of the four marks.
    private func tier(_ rank: Int, _ tree: AreaTree) -> Int {
        if rank < tree.fruit { return 1 }
        if rank < tree.fruit + tree.blossoms { return 2 }
        if rank < tree.fruit + tree.blossoms + tree.leaves { return 3 }
        if rank < tree.canopyCount { return 4 }
        return 0
    }
}

// MARK: - Composing trees from kern data
//
// Kern names how far each word has come (`GrowthStage`),
// and this is the one place that decides which Sprosse becomes which mark.
// Both the orchard on Home and the single tree a session summary draws
// read it, so they can never disagree about what an area looks like.

extension AppModel {

    /// One tree per area the box holds,
    /// in the Box screen's own order
    /// (`areaNames` — catalog groups top to bottom, own words last).
    /// Held on the model as `trees`:
    /// it walks every card in the join and sorts each area's reaches,
    /// and the orchard asks for it on every redraw.
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
    /// One entry per canopy word —
    /// what each has come to, for the mark that stands for it.
    var reaches: [Double] = []

    mutating func add(_ entry: CardGrowth, maximumInterval: Double) {
        if entry.touchedToday { tendedToday = true }
        // why: mass is what the trunk is made of,
        // so every word that has come anywhere counts toward it —
        // a settled word carries more of the tree than one met yesterday,
        // and a word never opened carries none of it.
        let reach = entry.reach(maximumIntervalDays: maximumInterval)
        mass += reach
        switch entry.stage {
        case .unscheduled: break
        // Packed and not yet opened: the tree is why it is growing,
        // and nothing hangs on it —
        // a word the learner has not met cannot be on the tree.
        case .queued: growing += 1
        // Met, still on its way in.
        // A bud, because the round that introduces a word has to be able
        // to point at what it put there:
        // with these drawn as nothing, a first round in an area
        // moved the picture not at all.
        case .learning, .fresh:
            buds += 1
            reaches.append(reach)
        // The canopy is green because most of a worked area IS green:
        // words that have landed are the bulk of any box that is being used.
        case .growing:
            leaves += 1
            reaches.append(reach)
        // A word is fruit only once it is well past the matured bar;
        // kern draws that second line (`FRUIT_STABILITY`),
        // so both phones split a canopy the same way.
        case .matured:
            if entry.stability >= FRUIT_STABILITY { fruit += 1 } else { blossoms += 1 }
            reaches.append(reach)
        case .relearning: fallen += 1
        // A word the box has taken out of rotation is owed no space
        // in the picture;
        // waking it lives on its row in the Box screen.
        case .suspended: break
        }
    }

    func tree(id: String, emoji: String, title: String) -> AreaTree {
        AreaTree(id: id, emoji: emoji, title: title,
                 leaves: leaves, blossoms: blossoms, fruit: fruit, buds: buds,
                 growing: growing, fallen: fallen, mass: mass, tendedToday: tendedToday,
                 // why: most-grown first,
                 // which is the order the canopy draws its marks in —
                 // fruit at the rim, then blossom, leaves, buds —
                 // so entry n belongs to mark n.
                 // The tiers ARE stability bands,
                 // so sorting by reach reproduces them.
                 reaches: reaches.sorted(by: >))
    }
}

extension CardGrowth {
    /// How far one word has come, 0…1 —
    /// what it contributes to its area's tree.
    ///
    /// Logarithmic, because stability grows multiplicatively:
    /// on a linear scale every word short of a year out would count the same,
    /// and the difference between a week and a month —
    /// the part the learner actually lives through — would be invisible.
    func reach(maximumIntervalDays: Double) -> Double {
        guard stability > 1, maximumIntervalDays > 1 else { return 0 }
        return min(1, log(stability) / log(maximumIntervalDays))
    }
}
