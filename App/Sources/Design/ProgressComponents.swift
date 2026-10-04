import SwiftUI

// MARK: - Progress components (Home progress section / Box screen)
//
// Small reusable stat pieces. All color-coded elements also carry their
// meaning in text or icons (colorblind-safe).

// MARK: StreakFlameView

/// How bright and how colorful the mark burns in each `FlameState`.
extension FlameState {
    /// Full strength where the day is answered, only a whisper of fade where it is
    /// still owed, and faint where there is no run behind the mark at all.
    var opacity: Double {
        switch self {
        case .lit: return 1
        case .dwindling: return 0.9
        case .atRisk: return 0.9
        case .unlit: return 0.4
        }
    }

    /// How much color is drained out of the emoji: none while the run is whole,
    /// half of it while today still owes the run — a flame cooling, which asks for
    /// renewal without being faded out — and all of it once a missed today would
    /// end the run, a flame gone cold, which is louder than any amount of fading.
    var grayscale: Double {
        switch self {
        case .lit: return 0
        case .dwindling: return 0.5
        case .atRisk, .unlit: return 1
        }
    }
}

struct StreakFlameView: View {
    let days: Int
    /// What today still owes the run, worn by the flame itself — the mark says
    /// the run is exposed on exactly the day it is, without a word for it.
    var flame: FlameState = .lit
    /// The mark the run wears. The flame is the streak's identity everywhere it is
    /// merely reported; a screen that IS the celebration hands its own emoji in and
    /// carries one badge instead of a badge under a hero saying the same thing twice.
    var emoji: String?

    var body: some View {
        HStack(spacing: Theme.spacing.sm) {
            mark
                .font(.title2)
                .accessibilityHidden(true)
            Text(days.formatted())
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
            // why: the number carries the big type, so the unit stands alone —
            // and a string that does not name its count cannot be plural-varied
            // (the compiler refuses it), which is what these two keys are for.
            Text(days == 1 ? "common.day.one" : "common.day.other")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
        }
        .padding(.horizontal, Theme.spacing.lg)
        .padding(.vertical, Theme.spacing.md)
        .background(Theme.colors.surfaceTint, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("a11y.count.streakDays \(days)"))
    }

    /// The celebrating screen's own emoji where one is handed in, else the flame
    /// in the grade the day has earned it.
    @ViewBuilder
    private var mark: some View {
        if let emoji {
            Text(verbatim: emoji)
        } else {
            Text(verbatim: "🔥")
                .grayscale(flame.grayscale)
                .opacity(flame.opacity)
        }
    }
}

// MARK: AreaChip

/// One stretch of the area bar; empty stretches are dropped before layout.
private struct AreaBarSegment: Identifiable {
    let id: Int
    let count: Int
    let color: Color
}

/// An area's cards split into the stretches the bar draws, with what they are
/// measured against. The split and the denominator are the box's rulings
/// (`AreaStatistics`); the screen hands them over so Design stays kern-free.
struct AreaProgress {
    /// Settled or matured cards — the bar's jade segment.
    let allSettled: Int
    /// Every other active card, fresh, growing or relearning — the counts row's split.
    let allGrowing: Int
    /// Cards packed but not yet introduced — the bar's clay segment. A card
    /// never packed at all gets no segment: it leaves the bar's neutral track
    /// showing rather than widening a fourth bucket.
    let queued: Int
    /// The bar's denominator — never below the introduced count.
    let progressTotal: Int

    /// What an area with no statistics yet draws: a bare track, no segment on it.
    static let empty = AreaProgress(allSettled: 0, allGrowing: 0, queued: 0, progressTotal: 1)
}

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
    let progress: AreaProgress
    /// Phrases still waiting on their component words to stabilize — not a
    /// count the bar can place (they aren't scheduled yet), so it only ever
    /// shows up here, and only when it says something (never at zero).
    let lockedPhrases: Int
    /// An area fully packed AND settled swaps its header mark for a jade
    /// one (the screen's own `packControl`) and has nothing left for the
    /// counts/bar to say — so they step aside, leaving just the emoji/name.
    var hideProgress: Bool = false

    /// A two-way split (matches the counts row) plus queued: settled, then
    /// everything else active, then packed-but-unintroduced. No amber segment —
    /// amber stays a badge-only color, distinguishing Fresh/Shaky from
    /// Growing at the per-card level without the bar needing that fine a grain.
    /// A card never packed at all gets no segment: the neutral track under them
    /// is what the untouched rest of the area reads as.
    private var segments: [AreaBarSegment] {
        [(progress.allSettled, Theme.colors.settled),
         (progress.allGrowing, Theme.colors.success),
         (progress.queued, Theme.colors.accent)]
            .enumerated()
            .filter { $0.element.0 > 0 }
            .map { AreaBarSegment(id: $0.offset, count: $0.element.0, color: $0.element.1) }
    }

    private var denominator: CGFloat { CGFloat(max(progress.progressTotal, 1)) }

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
                counts
                GeometryReader { geo in
                    let gaps = CGFloat(max(segments.count - 1, 0)) * 2
                    let unit = max(geo.size.width - gaps, 0) / denominator
                    // why: the neutral track is the area's untouched rest — cards
                    // never packed draw no segment, so without it the bar would end
                    // in the card's own background and read as full.
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.colors.separator)
                        HStack(spacing: 2) {
                            ForEach(segments) { segment in
                                Capsule()
                                    .fill(segment.color)
                                    .frame(width: unit * CGFloat(segment.count))
                            }
                        }
                    }
                }
                .frame(height: 6)
                .clipShape(Capsule())
                .accessibilityHidden(true) // why: counts above already carry the split
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
    /// and the bar alone draws the Sprosse between them.
    private var counts: some View {
        HStack(spacing: Theme.spacing.md) {
            Label("progress.allSettledCount \(Int(progress.allSettled))",
                  systemImage: "checkmark.seal.fill")
                .foregroundStyle(Theme.colors.settled)
            Label("progress.allGrowingCount \(Int(progress.allGrowing))", systemImage: "leaf.fill")
                .foregroundStyle(Theme.colors.success)
            if lockedPhrases > 0 {
                // why: the padlock carries "locked", so the text only has to
                // name what is locked — three full labels do not fit the card.
                Label("box.area.phrasesLockedShort \(lockedPhrases)", systemImage: "lock.fill")
                    .accessibilityLabel(Text("box.area.phrasesLocked \(lockedPhrases)"))
            }
            Spacer(minLength: 0)
        }
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
        .lineLimit(1)
        .minimumScaleFactor(0.75)
    }
}

// MARK: PhaseBadge

/// Where one card stands on the ladder, as one word in the Sprosse's own color.
///
/// Four labeled Sprossen: fresh, shaky (lapsed), growing and settled.
/// Fresh and shaky share amber and a glyph; only the word tells them apart.
/// The stage comes whole from kern (`CardRowState.Standing`), so a row never
/// reads settled before the shelf's count does.
///
/// The Sprosse's [growth] color is handed in, never re-derived here: kern resolves it
/// once (`CardRowState.Standing.swatch`) so a row's badge and the shelf's own bar,
/// which reads the same three tokens, cannot paint one Sprosse two ways.
struct PhaseBadge: View {
    /// Kept for the exhaustive mapping callers build from kern's `GrowthStage` —
    /// see `BoxCardRow.badgePhase`. It picks the WORD and the glyph; the color
    /// arrives with [growth] instead.
    enum Phase: CaseIterable {
        case new, fresh, growing, relearning, settled
    }

    let phase: Phase
    /// The Sprosse's color as the box resolved it. Absent where there is no Sprosse to
    /// color — a card with nothing behind it, which kern's ladder does not cover.
    var growth: Color?

    private var label: LocalizedStringKey {
        switch phase {
        case .new: return "box.phase.new"
        case .fresh: return "box.phase.fresh"
        case .growing: return "box.phase.growing"
        case .relearning: return "box.phase.relearning"
        case .settled: return "a11y.box.phase.settled"
        }
    }

    private var color: Color { growth ?? Theme.colors.textSecondary }

    /// The area row's own icon at the settled end; Growing gets one, and the two
    /// amber Sprossen share the leaf their shared color already pairs them by.
    private var icon: String {
        switch phase {
        case .new: return "circle.dashed"
        case .settled: return "checkmark.seal.fill"
        case .growing: return "checkmark.circle.fill"
        case .fresh, .relearning: return "leaf.fill"
        }
    }

    var body: some View {
        Group {
            // Grown is the one Sprosse that needs no word: a seal already reads as
            // "done" on its own, where Fresh/Shaky/Growing would be ambiguous
            // glyphs without one.
            if phase == .settled {
                Image(systemName: icon)
                    .accessibilityLabel(Text(label))
            } else {
                Label(label, systemImage: icon)
                    // why: a one-word badge in a crowded row gets compressed until
                    // it wraps ("Ne/u"); it keeps its width and the word beside it gives.
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
        }
        .pill(color)
    }
}

// MARK: - Previews

/// The card its real callers wrap it in, so the preview shows it in place.
private extension View {
    func previewCard() -> some View {
        padding(Theme.spacing.lg)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surface)
            )
            .cardShadow()
    }
}

/// Every Sprosse a badge can wear, in climbing order, with the colors kern hands the
/// real row (`CardRowState.Standing.swatch`) written out — a preview has no box to
/// ask, and seeing the five words side by side is the point of it.
private var ladder: some View {
    HStack(spacing: Theme.spacing.sm) {
        PhaseBadge(phase: .new)
        PhaseBadge(phase: .fresh, growth: Theme.colors.amber)
        PhaseBadge(phase: .relearning, growth: Theme.colors.amber)
        PhaseBadge(phase: .growing, growth: Theme.colors.success)
        PhaseBadge(phase: .settled, growth: Theme.colors.settled)
    }
}

#Preview("Progress pieces") {
    ScrollView {
        VStack(alignment: .leading, spacing: Theme.spacing.xl) {
            StreakFlameView(days: 12)
            StreakFlameView(days: 12, flame: .dwindling)
            StreakFlameView(days: 12, flame: .atRisk)
            StreakFlameView(days: 12, emoji: "🎉")
            AreaChip(emoji: "🍳", name: "Küche",
                     subtitle: "Hier duftet es nach Abendessen.",
                     progress: .init(allSettled: 18, allGrowing: 6, queued: 0, progressTotal: 24),
                     lockedPhrases: 0)
                .previewCard()
            AreaChip(emoji: "🛁", name: "Bad",
                     progress: .init(allSettled: 4, allGrowing: 9, queued: 28, progressTotal: 41),
                     lockedPhrases: 3)
                .previewCard()
            AreaChip(emoji: "🧰", name: "Werkstatt",
                     progress: .init(allSettled: 0, allGrowing: 0, queued: 17, progressTotal: 17),
                     lockedPhrases: 0)
                .previewCard()
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
        StreakFlameView(days: 3, flame: .atRisk)
        AreaChip(emoji: "🍳", name: "Küche",
                 progress: .init(allSettled: 18, allGrowing: 6, queued: 28, progressTotal: 52),
                 lockedPhrases: 2)
            .previewCard()
        ladder
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
    .preferredColorScheme(.dark)
}
