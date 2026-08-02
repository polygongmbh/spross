import SwiftUI

// MARK: - Progress components (Heute progress section / Box screen)
//
// Small reusable stat pieces. All color-coded elements also carry their
// meaning in text or icons (colorblind-safe).

// MARK: StreakFlameView

struct StreakFlameView: View {
    let days: Int

    var body: some View {
        HStack(spacing: DL.Space.s) {
            Text(verbatim: "🔥")
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

// MARK: BoxStatTile

struct BoxStatTile: View {
    let emoji: String
    let value: String
    let label: LocalizedStringKey
    /// Today's movement on this figure — the part that turns a standing total
    /// into a sense of progress. Omitted when the day has not moved it.
    var delta: LocalizedStringKey?

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text(emoji)
                .font(.title3)
                .accessibilityHidden(true)
            Text(value)
                .font(DL.Fonts.statValue)
                .foregroundStyle(Color.dlTextPrimary)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            if let delta {
                Text(delta)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlAccent)
            }
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
        .accessibilityElement(children: .combine)
    }
}

// MARK: AreaChip

/// One stretch of the area bar; empty stretches are dropped before layout.
private struct AreaBarSegment: Identifiable {
    let id: Int
    let count: Int
    let color: Color
}

/// Per-area chip: emoji + name + settled/learning counts over a bar that
/// measures both against the area's FULL card count, so the untouched rest
/// of an area stays visible instead of a bar that always reads as full.
///
/// Plain content, no card chrome: it sits inside its area's card on the Box
/// screen, and a second background there would read as a card in a card.
struct AreaChip: View {
    let emoji: String
    let name: String
    /// Cards in review phase ("gefestigt").
    let settled: Int
    /// Cards still in learning/relearning ("frisch").
    let learning: Int
    /// Every card the area holds, introduced or not — the bar's denominator.
    let total: Int

    /// Settled → learning → not yet introduced, measured against `total`.
    private var segments: [AreaBarSegment] {
        [(settled, Color.dlSuccess),
         (learning, Color.dlAmber),
         (max(total - settled - learning, 0), Color.dlSeparator)]
            .enumerated()
            .filter { $0.element.0 > 0 }
            .map { AreaBarSegment(id: $0.offset, count: $0.element.0, color: $0.element.1) }
    }

    /// Never below the introduced count: a stale `total` must not overflow the bar.
    private var denominator: CGFloat { CGFloat(max(total, settled + learning, 1)) }

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

    /// Same idiom as the Box screen's phrase counts: icon-led caption labels.
    private var counts: some View {
        HStack(spacing: DL.Space.l) {
            Label("progress.consolidatedCount \(settled.formatted())", systemImage: "checkmark.seal.fill")
            Label("progress.freshCount \(learning.formatted())", systemImage: "leaf.fill")
            Spacer(minLength: 0)
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
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

    var body: some View {
        Text(phase.label)
            .font(DL.Fonts.caption)
            .foregroundStyle(phase.color)
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
            HStack(spacing: DL.Space.m) {
                BoxStatTile(emoji: "🌳", value: "84", label: "progress.consolidated")
                BoxStatTile(emoji: "🌱", value: "48", label: "progress.fresh")
            }
            AreaChip(emoji: "🍳", name: "Küche", settled: 18, learning: 6, total: 24)
                .previewCard()
            AreaChip(emoji: "🛁", name: "Bad", settled: 4, learning: 9, total: 41)
                .previewCard()
            AreaChip(emoji: "🧰", name: "Werkstatt", settled: 0, learning: 0, total: 17)
                .previewCard()
            HStack(spacing: DL.Space.s) {
                ForEach(PhaseBadge.Phase.allCases, id: \.self) { PhaseBadge(phase: $0) }
            }
        }
        .padding(DL.Space.xl)
    }
    .background(Color.dlBackground)
}

#Preview("Progress pieces · dark") {
    VStack(alignment: .leading, spacing: DL.Space.xl) {
        StreakFlameView(days: 3)
        AreaChip(emoji: "🍳", name: "Küche", settled: 18, learning: 6, total: 52)
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
