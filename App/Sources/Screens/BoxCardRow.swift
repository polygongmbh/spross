import SwiftUI
import SprossKern

/// One word as the box lists it: its picture, the target citation over the word
/// the learner already knows, and its standing. The row itself is the audio
/// control — no speaker icon competing with the wake/pack controls and the
/// phrase text for width; tapping anywhere plain speaks it (`pronounceOnTap`,
/// shared with the produce-narration lines in `SessionView+Audio`).
///
/// `pack` is the row's one variation. Where a word can be packed on its own —
/// a search hit, which the learner went looking for by name — the "new" badge
/// gives its place to that offer, and a packed word answers with a check. In the
/// area list no such offer is made: the area's own control packs the shelf.
struct BoxCardRow: View {
    let model: AppModel
    let card: Card
    var pack: (() -> Void)?

    var body: some View {
        // why: only a word the learner wrote is theirs to delete — a catalog word
        // can be put to sleep, never removed, so it carries no menu at all.
        if model.isOwnWord(card.id) {
            row.contextMenu {
                Button("box.ownWords.remove", systemImage: "trash", role: .destructive) {
                    model.removeOwnWord(card.id)
                }
            }
        } else {
            row
        }
    }

    @ViewBuilder
    private var row: some View {
        let pronounce = model.pronounceAction(
            for: card.target.text, lang: card.target.lang,
            article: CardDisplay.spokenArticle(of: card.target, shown: card.target.text)
        )

        HStack(spacing: DL.Space.m) {
            Text(card.displayEmoji)
                .font(.title3)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                // Exposure surfaces render the TARGET side first (contract §6).
                Text(CardDisplay.citation(of: card.target))
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                Text(card.source.text)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: DL.Space.s)
            standing
        }
        .padding(.horizontal, DL.Space.m)
        .padding(.vertical, DL.Space.xs + 2)
        .background(
            // why: the row sits INSIDE the area card now — surface on surface
            // would leave the rows without an edge of their own.
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurfaceTint)
        )
        .pronounceOnTap(pronounce)
    }

    /// The row's right edge, drawn. WHICH of the five things a row has to state
    /// is the box's ruling (`BoxBrowser.cardRowState`) — including that an
    /// unexposed card states nothing at all, since in a shelf of unstarted words
    /// a "Neu" badge would be most of the rows and its capsule was what pushed
    /// the phrase text into truncation. The icons, the pill and the capsule are
    /// this row's own.
    @ViewBuilder
    private var standing: some View {
        switch onEnum(of: model.cardRowState(card.id, packOffered: pack != nil)) {
        case .sleeping:
            Text(verbatim: "💤")
                .accessibilityLabel("box.suspended")
            Button("box.wake") {
                model.setSuspended(cardID: card.id, suspended: false)
            }
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlAccent)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.xs + 1)
            .background(Color.dlAccent.opacity(0.14), in: Capsule())
        case .packOffered:
            if let pack {
                Button(action: pack) {
                    Image(systemName: "plus")
                }
                .buttonStyle(DLIconButtonStyle())
                .accessibilityLabel("box.packWord")
            }
        case .packed:
            Image(systemName: "checkmark.circle.fill")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlSuccess)
                .frame(width: 40, height: 40)
                .accessibilityLabel("box.packedWord")
        case .plain:
            EmptyView()
        case .standing(let standing):
            PhaseBadge(phase: Self.badgePhase(standing.phase),
                       consolidated: standing.consolidated)
        }
    }

    /// Kern's phase in the palette's own terms. `.theNew` never arrives — a card
    /// with nothing behind it reads `.plain` above — but the case is what makes
    /// the switch exhaustive.
    private static func badgePhase(_ phase: CardPhase) -> PhaseBadge.Phase {
        switch phase {
        case .theNew: return .new
        case .learning: return .learning
        case .review: return .review
        case .relearning: return .relearning
        }
    }
}
