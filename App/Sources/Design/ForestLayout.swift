import Foundation

// MARK: - Forest layout
//
// Where every plant stands, as plain values: no SwiftUI, no kern, no drawing.
// The canvas draws these frames and the accessibility overlay places its
// buttons on the very same rects, so the picture and its tap targets cannot
// drift apart.
//
// Nothing is stored per plant. Position, tilt and species variant all come
// out of a hash of the card id, so a forest is identical on every redraw,
// survives a relaunch unchanged, and needs no layout state to persist.

/// What a plant is grown from — one card's standing, in Design's own words.
/// The Design-local twin of the box's `GrowthStage`, so components stay
/// kern-free. Two rungs of the box's ladder may well map onto one of these.
enum PlantStage {
    /// A word the box holds and has not started — bare ground, not a plant.
    case soil
    case seed
    case sprout
    /// Up but bare: in review, not yet settled.
    case stem
    /// Leafed out: settled, on its way to landing.
    case leafed
    /// In flower: the word has landed.
    case bloom
    /// Full grown: a month or more between sights of it.
    case tree
    /// Lapsed, drooping until it earns its stability back.
    case wilting
    /// Out of rotation.
    case dormant

    /// How much room the plant needs, before its own growth scale.
    /// Ordered exactly as the ladder runs, so a grove's tallest plants are its
    /// furthest-grown ones and the depth sort below reads as depth.
    var height: CGFloat {
        switch self {
        case .soil: return 0.10
        case .seed: return 0.18
        case .sprout: return 0.34
        case .stem: return 0.48
        case .leafed: return 0.62
        case .bloom: return 0.78
        case .tree: return 1.00
        case .wilting: return 0.42
        case .dormant: return 0.22
        }
    }
}

/// Which species a plant takes — the card's kind, in Design's own words.
enum PlantKind {
    case noun, verb, modifier, phrase
}

/// One card as the forest sees it.
struct Plant: Identifiable {
    let id: String
    let stage: PlantStage
    let kind: PlantKind
    /// 0…1 within the stage — how far the word has come beyond clearing its bar.
    var growth: Double = 0
    /// Answered today; the day's growth, marked where it happened.
    var touchedToday: Bool = false
}

/// One area's patch of ground.
struct Grove: Identifiable {
    let id: String
    let emoji: String
    let title: String
    let plants: [Plant]
}

/// One plant placed: where it stands, how big, and how it leans.
struct PlantMark {
    let plant: Plant
    /// The plant's FOOT — where it meets the ground, not its centre.
    let foot: CGPoint
    let size: CGFloat
    let tilt: Double
    /// 0…1 across the grove's depth, 1 nearest the viewer. Fades the back rows.
    let depth: Double
}

/// One grove placed, with its plants already in draw order.
struct GroveFrame: Identifiable {
    let id: String
    let grove: Grove
    let rect: CGRect
    let marks: [PlantMark]
}

enum ForestLayout {

    /// Smallest patch a grove gets, so an untouched area is still a place.
    static let minGroveWidth: CGFloat = 84
    static let groveHeight: CGFloat = 76
    static let gap: CGFloat = DL.Space.s
    /// The label under each patch.
    static let labelHeight: CGFloat = 16

    /// Lays the groves out in rows across `width`, in the order given.
    ///
    /// A grove's width grows with the square root of its plant count, not with the
    /// count: an area holding forty words is bigger than one holding ten, but not
    /// four times bigger, or the first area the learner works would take the screen
    /// and the rest would be slivers.
    static func frames(_ groves: [Grove], width: CGFloat) -> [GroveFrame] {
        guard width > 0 else { return [] }
        let widths = groves.map { grove -> CGFloat in
            let spread = CGFloat(sqrt(Double(max(grove.plants.count, 1)) / 12))
            return min(width, max(minGroveWidth, minGroveWidth * spread))
        }
        var frames: [GroveFrame] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        for (grove, groveWidth) in zip(groves, widths) {
            if x > 0 && x + groveWidth > width {
                x = 0
                y += groveHeight + labelHeight + gap
            }
            let rect = CGRect(x: x, y: y, width: groveWidth, height: groveHeight)
            frames.append(GroveFrame(id: grove.id, grove: grove, rect: rect, marks: marks(grove, in: rect)))
            x += groveWidth + gap
        }
        return frames
    }

    /// The height the whole forest needs at this width.
    static func height(_ groves: [Grove], width: CGFloat) -> CGFloat {
        guard let last = frames(groves, width: width).last else { return 0 }
        return last.rect.maxY + labelHeight
    }

    /// Scatters a grove's plants over its patch, back band first.
    ///
    /// Where a plant stands comes from its id, NEVER from its place in the list.
    /// Seed order would otherwise put every word the learner has actually reached
    /// in the same corner and march the growth rightward as a wedge — the box
    /// grows in seed order, and a patch that showed it would read as a bug.
    ///
    /// Drawing order is the sort: a plant in a nearer band is drawn later and so
    /// stands in front, which is what makes a patch read as ground rather than as
    /// a sticker sheet.
    static func marks(_ grove: Grove, in rect: CGRect) -> [PlantMark] {
        guard !grove.plants.isEmpty else { return [] }
        let bands = max(2, min(5, Int(sqrt(Double(grove.plants.count)))))

        return grove.plants.map { plant -> PlantMark in
            let band = min(bands - 1, Int(noise(plant.id, 4) * Double(bands)))
            let alongBand = noise(plant.id, 1)
            let depth = bands == 1 ? 1 : Double(band) / Double(bands - 1)

            // Nearer bands sit lower and draw bigger — the whole of the perspective.
            let ground = rect.minY + rect.height * CGFloat(0.42 + 0.58 * depth)
            let spread = (0.62 + 0.38 * depth)
                * (0.86 + 0.28 * noise(plant.id, 2))
                * (0.72 + 0.28 * Double(plant.stage.height) + 0.24 * plant.growth)

            return PlantMark(
                plant: plant,
                foot: CGPoint(x: rect.minX + rect.width * CGFloat(alongBand.clamped(0.05, 0.95)), y: ground),
                size: rect.height * 0.38 * CGFloat(spread),
                tilt: (noise(plant.id, 3) - 0.5) * 0.22,
                depth: depth
            )
        }
        // why: back bands must be painted before the ones in front of them, and
        // the hash that placed the plants left them in no particular order.
        .sorted { $0.depth < $1.depth }
    }

    /// Stable 0..<1 noise for one (id, property) — the same SplitMix64 finish
    /// `ConfettiView` uses, over an FNV-1a fold of the id so neighbouring card
    /// ids ("w41", "w42") land nowhere near each other.
    static func noise(_ id: String, _ salt: Int) -> Double {
        var hash: UInt64 = 0xCBF2_9CE4_8422_2325
        for byte in id.utf8 {
            hash = (hash ^ UInt64(byte)) &* 0x1000_0000_01B3
        }
        var x = hash &+ UInt64(bitPattern: Int64(salt)) &* 0x9E37_79B9_7F4A_7C15
        x = (x ^ (x >> 33)) &* 0xFF51_AFD7_ED55_8CCD
        x = (x ^ (x >> 33)) &* 0xC4CE_B9FE_1A85_EC53
        x ^= x >> 33
        return Double(x >> 11) / Double(1 << 53)
    }
}

private extension Double {
    func clamped(_ low: Double, _ high: Double) -> Double { min(max(self, low), high) }
}
