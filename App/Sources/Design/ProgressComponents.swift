import SwiftUI

// MARK: - Progress components (Heute progress section / Box screen)
//
// Small reusable stat pieces. All color-coded elements also carry their
// meaning in text or icons (colorblind-safe).

// MARK: StreakFlameView

struct StreakFlameView: View {
    let days: Int
    /// The mark the run wears. The flame is the streak's identity everywhere it is
    /// merely reported; a screen that IS the celebration hands its own emoji in and
    /// carries one badge instead of a badge under a hero saying the same thing twice.
    var emoji: String = "🔥"

    var body: some View {
        HStack(spacing: DL.Space.s) {
            Text(verbatim: emoji)
                .font(.title2)
                .accessibilityHidden(true)
            Text(days.formatted())
                .font(DL.Fonts.statValue)
                .foregroundStyle(Color.dlTextPrimary)
            // why: the number carries the big type, so the unit stands alone —
            // and a string that does not name its count cannot be plural-varied
            // (the compiler refuses it), which is what these two keys are for.
            Text(days == 1 ? "common.dayOne" : "common.dayMany")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
        .padding(.horizontal, DL.Space.l)
        .padding(.vertical, DL.Space.m)
        .background(Color.dlSurfaceTint, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("a11y.streakDays \(days)"))
    }
}

// MARK: AreaChip

/// One stretch of the area bar; empty stretches are dropped before layout.
private struct AreaBarSegment: Identifiable {
    let id: Int
    let count: Int
    let color: Color
}

/// An area's cards split into the three stretches the bar draws, with what they
/// are measured against. The split and the denominator are the box's rulings
/// (`AreaStatistics`); the screen hands them over so Design stays kern-free.
struct AreaProgress {
    /// Cards past the consolidated bar ("gefestigt") — not merely in Review.
    let consolidated: Int
    /// Cards still in learning/relearning ("frisch").
    let learning: Int
    /// Cards the area holds that have never been introduced.
    let notIntroduced: Int
    /// The bar's denominator — never below the introduced count.
    let progressTotal: Int

    /// What an area with no statistics yet draws: a bar with nothing on it.
    static let empty = AreaProgress(consolidated: 0, learning: 0, notIntroduced: 0, progressTotal: 1)
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
    let progress: AreaProgress
    /// Phrases still waiting on their component words to stabilize — not a
    /// count the bar can place (they aren't scheduled yet), so it only ever
    /// shows up here, and only when it says something (never at zero).
    let lockedPhrases: Int

    /// Consolidated → learning → not yet introduced.
    private var segments: [AreaBarSegment] {
        [(progress.consolidated, Color.dlSuccess),
         (progress.learning, Color.dlAmber),
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

    /// Consolidated, fresh, and — only when it says something — locked phrases,
    /// as one row of icon-led caption labels instead of two disjoint rows.
    /// Three German words rarely fit this card's width at full size, so they
    /// shrink together instead of wrapping mid-word or truncating to "gefes…".
    private var counts: some View {
        HStack(spacing: DL.Space.m) {
            Label("progress.consolidatedCount \(progress.consolidated.formatted())",
                  systemImage: "checkmark.seal.fill")
            Label("progress.freshCount \(progress.learning.formatted())", systemImage: "leaf.fill")
            if lockedPhrases > 0 {
                // why: the padlock carries "locked", so the text only has to
                // name what is locked — three full labels do not fit the card.
                Label("box.phrasesLockedShort \(lockedPhrases)", systemImage: "lock.fill")
                    .accessibilityLabel(Text("box.phrasesLocked \(lockedPhrases)"))
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

struct PhaseBadge: View {
    enum Phase: CaseIterable {
        case new, learning, review, relearning

        var label: LocalizedStringKey {
            switch self {
            case .new: return "phase.new"
            case .learning: return "phase.learning"
            case .review: return "phase.review"
            case .relearning: return "phase.relearning"
            }
        }

        var color: Color {
            switch self {
            case .new: return .dlTextSecondary
            case .learning: return .dlDer
            case .review: return .dlSuccess
            case .relearning: return .dlAmber
            }
        }
    }

    let phase: Phase
    /// Whether this card counts as consolidated in its area's tally. The icon
    /// follows THIS, not the phase: a card reaches Review well below the
    /// consolidated threshold, so a seal keyed to the phase would mark cards
    /// the "gefestigt" count above it does not include.
    var consolidated: Bool = false

    /// The area row's own two icons, so a badge and that row agree on sight.
    private var icon: String {
        if phase == .new { return "circle.dashed" }
        return consolidated ? "checkmark.seal.fill" : "leaf.fill"
    }

    var body: some View {
        Label(phase.label, systemImage: icon)
            .font(DL.Fonts.caption)
            .foregroundStyle(phase.color)
            // why: a one-word badge in a crowded row gets compressed until it
            // wraps ("Ne/u"); it keeps its width and the word beside it gives.
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.xs + 1)
            .background(phase.color.opacity(0.14), in: Capsule())
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

#Preview("Progress pieces") {
    ScrollView {
        VStack(alignment: .leading, spacing: DL.Space.xl) {
            StreakFlameView(days: 12)
            AreaChip(emoji: "🍳", name: "Küche",
                     progress: .init(consolidated: 18, learning: 6, notIntroduced: 0, progressTotal: 24),
                     lockedPhrases: 0)
                .previewCard()
            AreaChip(emoji: "🛁", name: "Bad",
                     progress: .init(consolidated: 4, learning: 9, notIntroduced: 28, progressTotal: 41),
                     lockedPhrases: 3)
                .previewCard()
            AreaChip(emoji: "🧰", name: "Werkstatt",
                     progress: .init(consolidated: 0, learning: 0, notIntroduced: 17, progressTotal: 17),
                     lockedPhrases: 0)
                .previewCard()
            HStack(spacing: DL.Space.s) {
                ForEach(PhaseBadge.Phase.allCases, id: \.self) { PhaseBadge(phase: $0) }
            }
            // The same Review card once it has passed the consolidated bar.
            PhaseBadge(phase: .review, consolidated: true)
        }
        .padding(DL.Space.xl)
    }
    .background(Color.dlBackground)
}

#Preview("Progress pieces · dark") {
    VStack(alignment: .leading, spacing: DL.Space.xl) {
        StreakFlameView(days: 3)
        AreaChip(emoji: "🍳", name: "Küche",
                 progress: .init(consolidated: 18, learning: 6, notIntroduced: 28, progressTotal: 52),
                 lockedPhrases: 2)
            .previewCard()
        HStack(spacing: DL.Space.s) {
            ForEach(PhaseBadge.Phase.allCases, id: \.self) { PhaseBadge(phase: $0) }
        }
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
