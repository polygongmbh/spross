import SwiftUI
import SprossKern

/// The bottom of Heute: the 14-day strip, then the box as a forest — one tree
/// per area, in catalog order with the learner's own words last.
///
/// It is a picture of the box, not a way around it: tapping a tree opens the
/// Box screen at that area, which is still where browsing, packing and reviving
/// live. What the forest adds is the thing a count cannot — how the whole box
/// is shaped, and which corners of the language have never been opened.
struct ForestSection: View {
    let model: AppModel
    let open: (String) -> Void

    /// Measured once and reused: the forest has to know its width before it can
    /// say how tall it is (see `ForestCanvas`).
    @State private var width: CGFloat = 0

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            widthProbe
            Text("progress.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            ActivityStripView(days: model.activityWindow().map(ActivityColumn.init),
                              streakDays: model.stats?.streakDays ?? 0)
            ForestCanvas(trees: trees, open: open, describe: describe)
                .environment(\.dlContentWidth, width)
            caption
        }
    }

    private var widthProbe: some View {
        Color.clear
            .frame(height: 0)
            .background {
                GeometryReader { proxy in
                    Color.clear
                        .onAppear { width = proxy.size.width }
                        .onChange(of: proxy.size.width) { _, new in width = new }
                }
            }
    }

    /// The standing split in words, under the picture: a forest says how the box
    /// is shaped, never how many words are in it.
    private var caption: some View {
        Text.joined(
            Text("progress.consolidatedCount \((model.stats?.consolidatedCards ?? 0).formatted())"),
            Text("progress.freshCount \((model.stats?.learningCards ?? 0).formatted())")
        )
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
    }

    // MARK: - Areas as trees

    /// One tree per area the box holds, in the Box screen's own order
    /// (`AppModel.areaNames` — catalog groups top to bottom, own words last).
    private var trees: [AreaTree] {
        guard let box = model.box else { return [] }
        let maximumInterval = Double(box.config.maximumIntervalDays)
        var byArea: [String: AreaGrowth] = [:]
        for entry in model.growth {
            guard let card = box.cards[entry.cardId] else { continue }
            byArea[card.area, default: AreaGrowth()].add(entry, maximumInterval: maximumInterval)
        }
        return model.areaNames.compactMap { area in
            byArea[area]?.tree(id: area, emoji: model.areaEmoji(area), title: model.areaTitle(area))
        }
    }

    /// What VoiceOver reads: the picture says nothing aloud, so the label
    /// carries the area and the same split the caption spells out.
    private func describe(_ tree: AreaTree) -> Text {
        let stats = model.areaStats(tree.id)
        return Text.joined(
            Text(tree.title),
            Text("progress.consolidatedCount \((stats?.consolidated ?? 0).formatted())"),
            Text("progress.freshCount \((stats?.learning ?? 0).formatted())")
        )
    }
}

// MARK: - Kern → Design

/// One area's cards, tallied into the marks its tree is made of.
///
/// Which rung becomes which mark is decided HERE and nowhere else: kern names
/// the rule (`GrowthStage`), and the picture is free to give several rungs one
/// mark — as it does for everything still on its way in.
private struct AreaGrowth {
    var leaves = 0
    var blossoms = 0
    var fruit = 0
    var growing = 0
    var fallen = 0
    var mass = 0.0
    var tendedToday = false

    mutating func add(_ entry: CardGrowth, maximumInterval: Double) {
        if entry.touchedToday { tendedToday = true }
        // why: mass is what the trunk is made of, so every word that has come
        // anywhere counts toward it — a settled word carries more of the tree
        // than one met yesterday, and a word never opened carries none of it.
        mass += entry.reach(maximumIntervalDays: maximumInterval)
        switch entry.stage {
        case .unscheduled: break
        case .queued, .learning, .fresh: growing += 1
        case .settled: leaves += 1
        case .consolidated: blossoms += 1
        case .matured: fruit += 1
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
