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
            Text("\(days)")
                .font(DL.Fonts.statValue)
                .foregroundStyle(Color.dlTextPrimary)
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

// MARK: DueCountRing

struct DueCountRing: View {
    /// Cards still due right now.
    let remaining: Int
    /// Total cards in today's session (done + remaining).
    let total: Int
    var size: CGFloat = 96

    private var fraction: Double {
        guard total > 0 else { return 1 }
        return Double(total - remaining) / Double(total)
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.dlSeparator, lineWidth: 8)
            Circle()
                .trim(from: 0, to: fraction)
                .stroke(Color.dlAccent, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text("\(remaining)")
                    .font(DL.Fonts.statValue)
                    .foregroundStyle(Color.dlTextPrimary)
                    .minimumScaleFactor(0.6)
                Text("progress.due")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            .padding(DL.Space.s)
        }
        .frame(width: size, height: size)
        .animation(.easeOut(duration: 0.4), value: fraction)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("a11y.dueOfTotal \(remaining) \(total)"))
    }
}

// MARK: AreaChip

/// Per-area chip: emoji + name + sitting/learning split bar with counts.
struct AreaChip: View {
    let emoji: String
    let name: String
    /// Cards in review phase ("gefestigt").
    let sitting: Int
    /// Cards still in learning/relearning ("frisch").
    let learning: Int

    private var total: Int { max(sitting + learning, 1) }

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            HStack(spacing: DL.Space.s) {
                Text(emoji).accessibilityHidden(true)
                Text(name)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                Spacer(minLength: DL.Space.s)
                Text("progress.consolidatedFresh \(sitting) \(learning)")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            GeometryReader { geo in
                if sitting + learning == 0 {
                    // why: a fresh area has nothing sitting or learning — a full
                    // amber bar would read as "everything learning"; show neutral.
                    Capsule().fill(Color.dlSeparator)
                } else {
                    HStack(spacing: 2) {
                        Capsule()
                            .fill(Color.dlSuccess)
                            .frame(width: geo.size.width * CGFloat(sitting) / CGFloat(total))
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
            HStack(spacing: DL.Space.l) {
                StreakFlameView(days: 12)
                DueCountRing(remaining: 7, total: 20)
            }
            HStack(spacing: DL.Space.m) {
                BoxStatTile(emoji: "📦", value: "132", label: "progress.cardsInBox")
                BoxStatTile(emoji: "🎯", value: "91 %", label: "progress.retention")
            }
            AreaChip(emoji: "🍳", name: "Küche", sitting: 18, learning: 6)
            AreaChip(emoji: "🛁", name: "Bad", sitting: 4, learning: 9)
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
        HStack(spacing: DL.Space.l) {
            StreakFlameView(days: 3)
            DueCountRing(remaining: 0, total: 15)
        }
        AreaChip(emoji: "🍳", name: "Küche", sitting: 18, learning: 6)
        HStack(spacing: DL.Space.s) {
            ForEach(PhaseBadge.Phase.allCases, id: \.self) { PhaseBadge(phase: $0) }
        }
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
    .preferredColorScheme(.dark)
}
