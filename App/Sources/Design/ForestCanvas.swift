import SwiftUI

// MARK: - ForestCanvas
//
// The whole box as one picture: every card a plant, every area a grove.
//
// Drawn as ONE Canvas, never a view per plant — the same call ConfettiView
// makes and for the same reason: a box of five hundred words would otherwise
// be five hundred layers. Nothing is stored per plant; ForestLayout derives
// position, size and tilt from the card id.
//
// The forest never animates. A box grows over weeks, and motion would be
// claiming a change the picture cannot actually be showing — which also means
// there is nothing here for Reduce Motion to switch off.
//
// The canvas itself is hidden from accessibility. Each grove carries a real
// button on the very same rect the layout gave it, so what a sighted learner
// taps and what VoiceOver reads are one element, and every stage is told by
// silhouette and size rather than by colour alone.

/// Which of the two plant styles the forest draws in.
enum PlantStyle: String, CaseIterable, Identifiable {
    case drawn, emoji

    var id: String { rawValue }

    var label: LocalizedStringKey {
        switch self {
        case .drawn: return "settings.plantStyle.drawn"
        case .emoji: return "settings.plantStyle.emoji"
        }
    }
}

/// Where the chosen style is kept, so the forest and the settings that change
/// it cannot disagree about the key.
enum PlantStyleSetting {
    static let key = "plantStyle"
    static let `default` = PlantStyle.drawn
}

struct ForestCanvas: View {
    let groves: [Grove]
    var style: PlantStyle = .drawn
    /// What tapping a grove does. Nil leaves the forest a picture.
    var open: ((String) -> Void)?
    /// The spoken description of one grove — the screen's to write, since it
    /// alone knows what the counts are called.
    var describe: ((Grove) -> Text)?

    /// The width to lay out in. Taken from the environment rather than measured:
    /// a Canvas has to be given a height, the height falls out of the layout, and
    /// the layout needs the width first — so the screen states it once and both
    /// the picture and its buttons are placed against the same number.
    @Environment(\.dlContentWidth) private var width

    var body: some View {
        let frames = ForestLayout.frames(groves, width: width)
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                for frame in frames {
                    ground(&context, frame)
                    for mark in frame.marks {
                        switch style {
                        case .drawn: PlantShapes.draw(&context, mark)
                        case .emoji: PlantEmoji.draw(&context, mark)
                        }
                    }
                }
            }
            .accessibilityHidden(true)
            ForEach(frames) { frame in
                groveControl(frame)
            }
        }
        .frame(width: width, height: ForestLayout.height(groves, width: width), alignment: .topLeading)
    }

    /// The patch a grove stands on, and its name underneath.
    private func ground(_ context: inout GraphicsContext, _ frame: GroveFrame) {
        let patch = RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
            .path(in: frame.rect)
        context.fill(patch, with: .color(.dlSurfaceTint.opacity(0.55)))
    }

    private func groveControl(_ frame: GroveFrame) -> some View {
        VStack(spacing: 0) {
            // The patch itself is the Canvas's; this half is only the tap target.
            Spacer(minLength: 0)
            HStack(spacing: 3) {
                Text(verbatim: frame.grove.emoji)
                    .font(.system(size: 9))
                    .accessibilityHidden(true)
                Text(frame.grove.title)
                    .font(.system(size: 9, design: .rounded))
                    .foregroundStyle(Color.dlTextSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(height: ForestLayout.labelHeight)
        }
        .frame(width: frame.rect.width, height: frame.rect.height + ForestLayout.labelHeight)
        .contentShape(Rectangle())
        .offset(x: frame.rect.minX, y: frame.rect.minY)
        .onTapGesture { open?(frame.grove.id) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(describe?(frame.grove) ?? Text(frame.grove.title))
        .accessibilityAddTraits(open == nil ? [] : .isButton)
    }
}

// MARK: - Content width

private struct DLContentWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat = 320
}

extension EnvironmentValues {
    /// The width a section may actually draw in — the screen's width less its
    /// own padding. Set by the screen; read by anything that has to know its
    /// height before it is laid out.
    var dlContentWidth: CGFloat {
        get { self[DLContentWidthKey.self] }
        set { self[DLContentWidthKey.self] = newValue }
    }
}

// MARK: - Previews

/// A fabricated box at a given age — the only way to see a full forest without
/// months of reviews behind it.
private func sampleGroves(cards: Int) -> [Grove] {
    let areas = [("kitchen", "🍳", "Küche"), ("bath", "🛁", "Bad"), ("desk", "✏️", "Schreibtisch"),
                 ("living", "🛋️", "Wohnen"), ("hall", "🚪", "Flur"), ("outside", "🌳", "Draußen"),
                 ("school", "🎒", "Schule"), ("work", "💼", "Arbeit")]
    let stages: [PlantStage] = [.soil, .soil, .seed, .sprout, .stem, .leafed, .bloom, .bloom, .tree, .wilting, .dormant]
    let kinds: [PlantKind] = [.noun, .verb, .modifier, .phrase]
    var index = 0
    return areas.map { area, emoji, title in
        let count = max(3, cards / areas.count + (area.count * 7) % 11)
        let plants = (0..<count).map { position -> Plant in
            index += 1
            let noise = ForestLayout.noise("\(area)-\(position)", 9)
            return Plant(
                id: "\(area)-\(position)",
                stage: stages[Int(noise * Double(stages.count)) % stages.count],
                kind: kinds[index % kinds.count],
                growth: ForestLayout.noise("\(area)-\(position)", 10),
                touchedToday: noise > 0.9
            )
        }
        return Grove(id: area, emoji: emoji, title: title, plants: plants)
    }
}

private struct ForestPreview: View {
    let cards: Int
    @State private var style: PlantStyle = .drawn

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Picker("Stil", selection: $style) {
                ForEach(PlantStyle.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            ForestCanvas(groves: sampleGroves(cards: cards), style: style, open: { _ in })
        }
        .padding(DL.Space.xl)
        .environment(\.dlContentWidth, 393 - DL.Space.xl * 2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.dlBackground)
    }
}

#Preview("Forest · a young box") { ForestPreview(cards: 20) }

#Preview("Forest · a working box") { ForestPreview(cards: 200) }

#Preview("Forest · a full box") { ForestPreview(cards: 550) }

#Preview("Forest · dark") {
    ForestPreview(cards: 200).preferredColorScheme(.dark)
}
