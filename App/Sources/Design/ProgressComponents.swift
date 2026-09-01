import SwiftUI

// MARK: - Progress components (Home progress section / Box screen)
//
// Small reusable stat pieces. All color-coded elements also carry their
// meaning in text or icons (colorblind-safe).

// MARK: StreakFlameView

/// How the streak's flame burns. The Design-local twin of the box's
/// `StreakHealth`, so components stay kern-free — WHICH day arithmetic puts a run
/// in which grade is the engine's ruling (`kern/docs/reports.md`); here it is only
/// how bright and how colorful the mark burns.
enum FlameState {
    /// Today has reviews — the run is safe until tomorrow.
    case lit
    /// Nothing today yet, but a miss would only spend the run's one bridge.
    case dwindling
    /// Nothing today, and the bridge is already spent — a miss ends the run.
    case atRisk
    /// No run to protect.
    case unlit

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
        HStack(spacing: DL.Space.s) {
            mark
                .font(.title2)
                .accessibilityHidden(true)
            Text(days.formatted())
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            // why: the number carries the big type, so the unit stands alone —
            // and a string that does not name its count cannot be plural-varied
            // (the compiler refuses it), which is what these two keys are for.
            Text(days == 1 ? "common.day.one" : "common.day.other")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
        .padding(.horizontal, DL.Space.l)
        .padding(.vertical, DL.Space.m)
        .background(Color.dlSurfaceTint, in: Capsule())
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
    /// Cards past the consolidated bar ("gefestigt") — not merely in Review.
    let consolidated: Int
    /// Everything active short of that bar — [settling] included, since the counts
    /// row draws the coarse two-way split this number is cut for.
    let learning: Int
    /// The part of [learning] that has reached Review without clearing the bar.
    /// Only the BAR separates it out; the counts beside the bar do not.
    let settling: Int
    /// Cards the area holds that have never been introduced.
    let notIntroduced: Int
    /// The bar's denominator — never below the introduced count.
    let progressTotal: Int

    /// What an area with no statistics yet draws: a bar with nothing on it.
    static let empty = AreaProgress(consolidated: 0, learning: 0, settling: 0,
                                    notIntroduced: 0, progressTotal: 1)
}

/// Per-area chip: emoji + name + consolidated/learning counts over a bar that
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

    /// The ladder laid flat, grown end first: grown → growing → fresh → not yet
    /// introduced, in the same three colors a row's own badge wears, so the shelf
    /// and the rows under it never tell two stories. The fresh stretch subtracts
    /// the settling cards the box counts inside `learning` — the two-way number
    /// the counts row is cut for, which the bar splits one level finer.
    private var segments: [AreaBarSegment] {
        [(progress.consolidated, Color.dlGrown),
         (progress.settling, Color.dlSuccess),
         (progress.learning - progress.settling, Color.dlAmber),
         (progress.notIntroduced, Color.dlSeparator)]
            .enumerated()
            .filter { $0.element.0 > 0 }
            .map { AreaBarSegment(id: $0.offset, count: $0.element.0, color: $0.element.1) }
    }

    private var denominator: CGFloat { CGFloat(max(progress.progressTotal, 1)) }

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            HStack(spacing: DL.Space.s) {
                Text(emoji).accessibilityHidden(true)
                Text(name)
                    .font(DL.Fonts.title)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                Spacer(minLength: DL.Space.s)
            }
            if let subtitle {
                Text(subtitle)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextSecondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            counts
            GeometryReader { geo in
                if segments.isEmpty {
                    // why: an empty area still needs a bar in its slot — a full
                    // amber one would read as "everything learning"; show neutral.
                    Capsule().fill(Color.dlSeparator)
                } else {
                    let unit = max(geo.size.width - CGFloat(segments.count - 1) * 2, 0) / denominator
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
        .accessibilityElement(children: .combine)
    }

    /// Consolidated, learning, and — only when it says something — locked phrases,
    /// as one row of icon-led caption labels instead of two disjoint rows.
    /// Three German words rarely fit this card's width at full size, so they
    /// shrink together instead of wrapping mid-word or truncating to "gefes…".
    ///
    /// Two counts, not the bar's three: three German words do not fit this width,
    /// so the text keeps the coarse split — cleared the bar, or still short of it —
    /// and the bar alone draws the rung between them.
    private var counts: some View {
        HStack(spacing: DL.Space.m) {
            Label("progress.consolidatedCount \(progress.consolidated.formatted())",
                  systemImage: "checkmark.seal.fill")
                .foregroundStyle(Color.dlGrown)
            Label("progress.learningCount \(progress.learning.formatted())", systemImage: "leaf.fill")
                .foregroundStyle(Color.dlSuccess)
            if lockedPhrases > 0 {
                // why: the padlock carries "locked", so the text only has to
                // name what is locked — three full labels do not fit the card.
                Label("box.area.phrasesLockedShort \(lockedPhrases)", systemImage: "lock.fill")
                    .accessibilityLabel(Text("box.area.phrasesLocked \(lockedPhrases)"))
            }
            Spacer(minLength: 0)
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
        .lineLimit(1)
        .minimumScaleFactor(0.75)
    }
}

// MARK: PhaseBadge

/// Where one card stands on the ladder, as one word in the rung's own color.
///
/// Four rungs, growing: a card just planted is fresh, one that lapsed back to the
/// learning steps is shaky, one in Review is growing, and one past [consolidated] —
/// kern's stricter bar, the same one the shelf's tally counts against — has grown.
/// Fresh and shaky share amber and a glyph deliberately: both are still walking the
/// learning steps, and only the WORD says which way the card got there. Growing and
/// grown are the pair that must never blur, because a row that read "grown" before the
/// shelf counted it claimed a word the shelf above it did not — so the bar it cleared
/// is handed over beside the phase rather than read out of it (kern
/// `CardRowState.Standing` states why).
///
/// The rung's [growth] color is handed in, never re-derived here: kern resolves it
/// once (`CardRowState.Standing.swatch`) so a row's badge and the shelf's own bar,
/// which reads the same three tokens, cannot paint one rung two ways.
struct PhaseBadge: View {
    /// Kept for the exhaustive mapping callers build from `CardPhase` — see
    /// `BoxCardRow.badgePhase`. It picks the WORD and the glyph; the color arrives
    /// with [growth] instead.
    enum Phase: CaseIterable {
        case new, learning, review, relearning
    }

    let phase: Phase
    /// Whether this card counts as consolidated in its area's tally — the bar it
    /// has cleared, handed over beside the phase rather than read out of it
    /// (kern `CardRowState.Standing` states why).
    var consolidated: Bool = false
    /// The rung's color as the box resolved it. Absent where there is no rung to
    /// color — a card with nothing behind it, which kern's ladder does not cover.
    var growth: Color?

    private var label: LocalizedStringKey {
        if phase == .new { return "box.phase.new" }
        if consolidated { return "box.phase.consolidated" }
        switch phase {
        case .review: return "box.phase.settled"
        case .relearning: return "box.phase.relearning"
        case .learning, .new: return "box.phase.learning"
        }
    }

    private var color: Color { growth ?? .dlTextSecondary }

    /// The area row's own icon at the consolidated end; Growing gets one, and the two
    /// amber rungs share the leaf their shared color already pairs them by.
    private var icon: String {
        if phase == .new { return "circle.dashed" }
        if consolidated { return "checkmark.seal.fill" }
        return phase == .review ? "checkmark.circle.fill" : "leaf.fill"
    }

    var body: some View {
        Group {
            // Grown is the one rung that needs no word: a seal already reads as
            // "done" on its own, where Fresh/Shaky/Growing would be ambiguous
            // glyphs without one.
            if consolidated {
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
        .font(DL.Fonts.caption)
        .foregroundStyle(color)
        .padding(.horizontal, DL.Space.m)
        .padding(.vertical, DL.Space.xs + 1)
        .background(color.opacity(0.14), in: Capsule())
    }
}

// MARK: - Previews

/// The card its real callers wrap it in, so the preview shows it in place.
private extension View {
    func previewCard() -> some View {
        padding(DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .dlCardShadow()
    }
}

/// Every rung a badge can wear, in climbing order, with the colors kern hands the
/// real row (`CardRowState.Standing.swatch`) written out — a preview has no box to
/// ask, and seeing the four words side by side is the point of it.
private var ladder: some View {
    HStack(spacing: DL.Space.s) {
        PhaseBadge(phase: .new)
        PhaseBadge(phase: .learning, growth: .dlAmber)
        PhaseBadge(phase: .relearning, growth: .dlAmber)
        PhaseBadge(phase: .review, growth: .dlSuccess)
        PhaseBadge(phase: .review, consolidated: true, growth: .dlGrown)
    }
}

#Preview("Progress pieces") {
    ScrollView {
        VStack(alignment: .leading, spacing: DL.Space.xl) {
            StreakFlameView(days: 12)
            StreakFlameView(days: 12, flame: .dwindling)
            StreakFlameView(days: 12, flame: .atRisk)
            StreakFlameView(days: 12, emoji: "🎉")
            AreaChip(emoji: "🍳", name: "Küche",
                     subtitle: "Hier duftet es nach Abendessen.",
                     progress: .init(consolidated: 18, learning: 6, settling: 4,
                                     notIntroduced: 0, progressTotal: 24),
                     lockedPhrases: 0)
                .previewCard()
            AreaChip(emoji: "🛁", name: "Bad",
                     progress: .init(consolidated: 4, learning: 9, settling: 3,
                                     notIntroduced: 28, progressTotal: 41),
                     lockedPhrases: 3)
                .previewCard()
            AreaChip(emoji: "🧰", name: "Werkstatt",
                     progress: .init(consolidated: 0, learning: 0, settling: 0,
                                     notIntroduced: 17, progressTotal: 17),
                     lockedPhrases: 0)
                .previewCard()
            // The whole ladder, in the order a card climbs it.
            ladder
        }
        .padding(DL.Space.xl)
    }
    .background(Color.dlBackground)
}

#Preview("Progress pieces · dark") {
    VStack(alignment: .leading, spacing: DL.Space.xl) {
        StreakFlameView(days: 3)
        StreakFlameView(days: 3, flame: .atRisk)
        AreaChip(emoji: "🍳", name: "Küche",
                 progress: .init(consolidated: 18, learning: 6, settling: 2,
                                 notIntroduced: 28, progressTotal: 52),
                 lockedPhrases: 2)
            .previewCard()
        ladder
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
