import SwiftUI
import SprossKern

/// The bottom of Heute: the 14-day strip, then the box itself — one plant per
/// word, one grove per area, in catalog order with the learner's own words last.
///
/// This IS the box browser. Tapping a grove opens that area, which is why the
/// box needed no screen of its own: the picture of what has grown and the way
/// into it are the same thing.
struct ForestSection: View {
    let model: AppModel
    let open: (String) -> Void

    @AppStorage(PlantStyleSetting.key) private var styleRaw = PlantStyleSetting.default.rawValue
    /// Measured once and reused: the forest has to know its width before it can
    /// say how tall it is (see `ForestCanvas`).
    @State private var width: CGFloat = 0

    private var style: PlantStyle { PlantStyle(rawValue: styleRaw) ?? PlantStyleSetting.default }

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            widthProbe
            Text("progress.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            ActivityStripView(days: model.activityWindow().map(ActivityColumn.init),
                              streakDays: model.stats?.streakDays ?? 0)
            ForestCanvas(groves: groves, style: style, open: open, describe: describe)
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

    /// The standing split, spelled out under the picture: a forest says how the
    /// box is shaped, never how many words are in it.
    private var caption: some View {
        Text.joined(
            Text("progress.consolidatedCount \((model.stats?.consolidatedCards ?? 0).formatted())"),
            Text("progress.freshCount \((model.stats?.learningCards ?? 0).formatted())")
        )
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
    }

    // MARK: - Groves

    /// One grove per area the box holds, in the Box browser's own order
    /// (`AppModel.areaNames` — catalog groups top-to-bottom, own words last).
    private var groves: [Grove] {
        guard let box = model.box else { return [] }
        let maximumInterval = Double(box.config.maximumIntervalDays)
        var byArea: [String: [Plant]] = [:]
        // why: growth already arrives in seed order, so bucketing keeps each
        // grove's plants in it — the layout leans on that for its bands.
        for entry in model.growth {
            guard let card = box.cards[entry.cardId] else { continue }
            byArea[card.area, default: []].append(
                Plant(id: card.id,
                      stage: entry.stage.plantStage,
                      kind: card.kind.plantKind,
                      growth: entry.reach(maximumIntervalDays: maximumInterval),
                      touchedToday: entry.touchedToday)
            )
        }
        return model.areaNames.compactMap { area in
            guard let plants = byArea[area] else { return nil }
            return Grove(id: area, emoji: model.areaEmoji(area),
                         title: model.areaTitle(area), plants: plants)
        }
    }

    /// What VoiceOver reads for a grove — the area's name and the same split the
    /// area's own screen shows, since the picture itself says nothing aloud.
    private func describe(_ grove: Grove) -> Text {
        let stats = model.areaStats(grove.id)
        return Text.joined(
            Text(model.areaTitle(grove.id)),
            Text("progress.consolidatedCount \((stats?.consolidated ?? 0).formatted())"),
            Text("progress.freshCount \((stats?.learning ?? 0).formatted())")
        )
    }
}

// MARK: - Kern → Design

extension GrowthStage {
    /// The box's rung as the forest draws it. Deliberately one-to-one today —
    /// which rungs share a plant is a picture decision, and the place to change
    /// it is here, never in kern.
    var plantStage: PlantStage {
        switch self {
        case .unscheduled: return .soil
        case .queued: return .seed
        case .learning: return .sprout
        case .fresh: return .stem
        case .settled: return .leafed
        case .consolidated: return .bloom
        case .matured: return .tree
        case .relearning: return .wilting
        case .suspended: return .dormant
        }
    }
}

extension CardKind {
    /// Species by word kind. Adjectives are kern's catch-all for every plain
    /// non-noun, non-verb word, so the plant they take is named for that rather
    /// than for the part of speech.
    var plantKind: PlantKind {
        switch self {
        case .noun: return .noun
        case .verb: return .verb
        case .adjective: return .modifier
        case .phrase: return .phrase
        }
    }
}

extension CardGrowth {
    /// How far the word has come, 0…1, for the plants whose SIZE says it.
    ///
    /// Logarithmic, because stability grows multiplicatively: on a linear scale
    /// every word short of a year out would draw the same, and the difference
    /// between a week and a month — the part the learner actually lives through —
    /// would be invisible.
    func reach(maximumIntervalDays: Double) -> Double {
        guard stability > 1, maximumIntervalDays > 1 else { return 0 }
        return min(1, log(stability) / log(maximumIntervalDays))
    }
}
