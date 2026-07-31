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

/// Per-area chip: emoji + name + settled/learning split bar with counts.
struct AreaChip: View {
    let emoji: String
    let name: String
    /// Cards in review phase ("gefestigt").
    let settled: Int
    /// Cards still in learning/relearning ("frisch").
    let learning: Int

    private var total: Int { max(settled + learning, 1) }

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            HStack(spacing: DL.Space.s) {
                Text(emoji).accessibilityHidden(true)
                Text(name)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                Spacer(minLength: DL.Space.s)
                Text("progress.consolidatedFresh \(settled.formatted()) \(learning.formatted())")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            GeometryReader { geo in
                if settled + learning == 0 {
                    // why: a fresh area has nothing settled or learning — a full
                    // amber bar would read as "everything learning"; show neutral.
                    Capsule().fill(Color.dlSeparator)
                } else {
                    HStack(spacing: 2) {
                        Capsule()
                            .fill(Color.dlSuccess)
                            .frame(width: geo.size.width * CGFloat(settled) / CGFloat(total))
                        Capsule()
                            .fill(Color.dlAmber)
                    }
                }
            }
            .frame(height: 6)
            .clipShape(Capsule())
            .accessibilityHidden(true) // why: counts above already carry the split
        }
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
        .accessibilityElement(children: .combine)
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

#Preview("Progress pieces") {
    ScrollView {
        VStack(alignment: .leading, spacing: DL.Space.xl) {
            StreakFlameView(days: 12)
            HStack(spacing: DL.Space.m) {
                BoxStatTile(emoji: "🌳", value: "84", label: "progress.consolidated")
                BoxStatTile(emoji: "🌱", value: "48", label: "progress.fresh")
            }
            AreaChip(emoji: "🍳", name: "Küche", settled: 18, learning: 6)
            AreaChip(emoji: "🛁", name: "Bad", settled: 4, learning: 9)
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
        AreaChip(emoji: "🍳", name: "Küche", settled: 18, learning: 6)
        HStack(spacing: DL.Space.s) {
            ForEach(PhaseBadge.Phase.allCases, id: \.self) { PhaseBadge(phase: $0) }
        }
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
