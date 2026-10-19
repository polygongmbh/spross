import SprossKern
import SwiftUI

// MARK: - Progress components (Home progress section / Box screen)
//
// Small reusable stat pieces. All color-coded elements also carry their
// meaning in text or icons (colorblind-safe).

// MARK: AreaChip

/// Per-area chip: emoji + name + settled/growing counts over a bar that
/// measures both against the area's FULL card count, so the untouched rest
/// of an area stays visible instead of a bar that always reads as full.
///
/// Plain content, no card chrome: it sits inside its area's card on the Box
/// screen, and a second background there would read as a card in a card.
struct AreaChip: View {
    let emoji: String
    let name: String
    /// The area's flavor clause, where the catalog authors one — never louder
    /// than the name it sits under, and simply absent otherwise.
    var subtitle: String?
    /// The area's split into the bar's stretches and what they are measured
    /// against — the box's rulings (`AreaStatistics`); nil draws a bare track.
    /// Its locked phrases are no count the bar can place (they aren't scheduled
    /// yet), so they only show in the counts row, and never at zero.
    let stats: AreaStatistics?
    /// An area fully queued AND settled swaps its header mark for a jade
    /// one (the screen's own `queueControl`) and has nothing left for the
    /// counts/bar to say — so they step aside, leaving just the emoji/name.
    var hideProgress: Bool = false

    /// The bar's stretches, fades and fill are kern's (`AreaBar`). No amber stretch —
    /// amber stays a badge-only color, distinguishing Fresh/Shaky from
    /// Growing at the per-card level without the bar needing that fine a grain.
    private var bar: AreaBar {
        AreaBar(settled: Double(stats?.allSettled ?? 0), growing: Double(stats?.allGrowing ?? 0),
                queued: Double(stats?.queued ?? 0), progressTotal: stats?.progressTotal ?? 1)
    }

    private func stops(_ bar: AreaBar) -> [Gradient.Stop] {
        bar.stops.map { stop in
            let color: Color
            switch stop.stretch {
            case .settled: color = Theme.colors.settled
            case .growing: color = Theme.colors.success
            default: color = Theme.colors.accent
            }
            return Gradient.Stop(color: color, location: stop.location)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            HStack(spacing: Theme.spacing.sm) {
                Text(emoji).accessibilityHidden(true)
                Text(name)
                    .font(Theme.typography.title)
                    .foregroundStyle(Theme.colors.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: Theme.spacing.sm)
            }
            if let subtitle {
                Text(subtitle)
                    .font(Theme.typography.subheadline)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !hideProgress {
                counts.accessibilityHidden(true)
                GeometryReader { geo in
                    // why: the neutral track is the area's untouched rest — cards
                    // never queued draw no segment, so without it the bar would end
                    // in the card's own background and read as full.
                    let filled = self.bar
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.colors.separator)
                        if filled.fill > 0 {
                            Capsule()
                                .fill(LinearGradient(stops: stops(filled), startPoint: .leading, endPoint: .trailing))
                                .frame(width: geo.size.width * filled.fill)
                        }
                    }
                }
                .frame(height: 6)
                .clipShape(Capsule())
                .accessibilityHidden(true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// Settled, learning, and — only when it says something — locked phrases,
    /// as one row of icon-led caption labels instead of two disjoint rows.
    /// Three German words rarely fit this card's width at full size, so they
    /// shrink together instead of wrapping mid-word or truncating to "gefes…".
    ///
    /// Two counts, not the bar's three: three German words do not fit this width,
    /// so the text keeps the coarse split — cleared the bar, or still short of it —
    /// and the bar alone draws the stage between them.
    private var counts: some View {
        let lockedPhrases = stats?.lockedPhrases ?? 0
        return HStack(spacing: Theme.spacing.md) {
            Label("progress.allSettledCount \(stats?.allSettledCards ?? 0)",
                  systemImage: "checkmark.seal.fill")
                .foregroundStyle(Theme.colors.settled)
            Label("progress.allGrowingCount \(Int(stats?.allGrowing ?? 0))", systemImage: "leaf.fill")
                .foregroundStyle(Theme.colors.success)
            if lockedPhrases > 0 {
                // why: the padlock carries "locked", so the text only has to
                // name what is locked — three full labels do not fit the card.
                Label("box.area.phrasesLockedShort \(lockedPhrases)", systemImage: "lock.fill")
            }
            Spacer(minLength: 0)
        }
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
        .lineLimit(1)
        .minimumScaleFactor(0.75)
    }
}

// MARK: StageBadge

/// Where one card stands on the ladder, as one word in the stage's own color.
///
/// Four labeled stages: fresh, shaky (lapsed), growing and settled.
/// Fresh and shaky share amber and a glyph; only the word tells them apart.
/// The stage comes whole from kern (`CardRowState.Standing`), so a row never
/// reads settled before the shelf's count does.
///
/// The stage's [growth] color is handed in, never re-derived here: kern resolves it
/// once (`CardRowState.Standing.swatch`) so a row's badge and the shelf's own bar,
/// which reads the same three tokens, cannot paint one stage two ways.
struct StageBadge: View {
    /// Picks the WORD and the glyph; the color arrives with [growth] instead.
    let stage: ActiveStage
    /// The stage's color as the box resolved it.
    let growth: Color

    private var label: LocalizedStringKey {
        switch stage {
        case .fresh: return "box.stage.fresh"
        case .growing: return "box.stage.growing"
        case .lapsed: return "box.stage.lapsed"
        case .settled: return "a11y.box.stage.settled"
        }
    }

    /// The area row's own icon at the settled end; Growing gets one, and the two
    /// amber stages share the leaf their shared color already pairs them by.
    private var icon: String {
        switch stage {
        case .settled: return "checkmark.seal.fill"
        case .growing: return "checkmark.circle.fill"
        case .fresh, .lapsed: return "leaf.fill"
        }
    }

    var body: some View {
        Group {
            // Settled is the one stage that needs no word: a seal already reads as
            // "done" on its own, where Fresh/Shaky/Growing would be ambiguous
            // glyphs without one.
            if stage == .settled {
                Image(systemName: icon)
                    .accessibilityLabel(Text(label))
            } else {
                Label(label, systemImage: icon)
                    // why: a one-word badge in a crowded row gets compressed until
                    // it wraps ("Ne/u"); it keeps its width and the word beside it gives.
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    // why: the stages on the way up are the eye's; only Settled is read.
                    .accessibilityHidden(true)
            }
        }
        .pill(growth)
    }
}

// MARK: - Previews

/// An area as the box would count it — every card either settled, growing or queued.
private func previewArea(settled: Int32, growing: Int32, queued: Int32, locked: Int32 = 0) -> AreaStatistics {
    AreaStatistics(name: "preview", total: settled + growing + queued,
                   stages: StageCounts(fresh: 0, growing: growing, lapsed: 0, settled: settled, matured: 0),
                   queued: queued, phrasesLocked: locked)
}

/// Every stage a badge can wear, in climbing order, with the colors kern hands the
/// real row (`CardRowState.Standing.swatch`) written out — a preview has no box to
/// ask, and seeing the four badges side by side is the point of it.
private var ladder: some View {
    HStack(spacing: Theme.spacing.sm) {
        StageBadge(stage: .fresh, growth: Theme.colors.amber)
        StageBadge(stage: .lapsed, growth: Theme.colors.amber)
        StageBadge(stage: .growing, growth: Theme.colors.success)
        StageBadge(stage: .settled, growth: Theme.colors.settled)
    }
}

#Preview("Progress pieces") {
    ScrollView {
        VStack(alignment: .leading, spacing: Theme.spacing.xl) {
            StreakFlameView(days: 12)
            StreakFlameView(days: 12, health: .bridgeable)
            StreakFlameView(days: 12, health: .ending)
            StreakFlameView(days: 12, emoji: "🎉")
            AreaChip(emoji: "🍳", name: "Küche",
                     subtitle: "Hier duftet es nach Essen.",
                     stats: previewArea(settled: 18, growing: 6, queued: 0))
                .panelSurface()
                .cardShadow()
            AreaChip(emoji: "🛁", name: "Bad",
                     stats: previewArea(settled: 4, growing: 9, queued: 28, locked: 3))
                .panelSurface()
                .cardShadow()
            AreaChip(emoji: "🧰", name: "Werkstatt",
                     stats: previewArea(settled: 0, growing: 0, queued: 17))
                .panelSurface()
                .cardShadow()
            // The whole ladder, in the order a card climbs it.
            ladder
        }
        .padding(Theme.spacing.xl)
    }
    .background(Theme.colors.background)
}

#Preview("Progress pieces · dark") {
    VStack(alignment: .leading, spacing: Theme.spacing.xl) {
        StreakFlameView(days: 3)
        StreakFlameView(days: 3, health: .ending)
        AreaChip(emoji: "🍳", name: "Küche",
                 stats: previewArea(settled: 18, growing: 6, queued: 28, locked: 2))
            .panelSurface()
            .cardShadow()
        ladder
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
    .preferredColorScheme(.dark)
}
