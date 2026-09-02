import SwiftUI
import SprossKern

/// One word as the box lists it: its picture, the target citation over the word
/// the learner already knows, and its standing. The row itself is the audio
/// control — no speaker icon competing with the wake/pack controls and the
/// phrase text for width; tapping anywhere plain speaks it (`pronounceOnTap`,
/// shared with the produce-narration lines in `SessionView+Audio`). The one
/// exception is a crossed-out speaker beside a word neither a recording nor
/// the device's voice can say — there the tap does nothing, and the row must
/// not promise otherwise.
///
/// `pack` is the row's one variation. Where a word can be packed on its own —
/// a search hit, which the learner went looking for by name — the "new" badge
/// gives its place to that offer, and a word already queued there answers
/// with a tappable tray icon of its own, taking it back out the same way it
/// went in. In the area list the shelf's own control does both instead
/// (`BoxAreaSection.packControl`): a queued row states so with the same tray
/// icon, plain rather than tappable.
///
/// A long press opens everything that can be done to the word (`BoxRowMenu`);
/// the entries that need a screen of their own hand one back here to raise.
struct BoxCardRow: View {
    let model: AppModel
    let card: Card
    var pack: (() -> Void)?

    /// The sheet the long-press menu asked for, if any (`BoxRowMenu`).
    @State private var sheet: BoxRowSheet?

    var body: some View {
        // why: the note is the one thing the row has no width to say, and the
        // long press is already where a word is asked about itself. A word
        // without one keeps the system's own preview — a copy of the row would
        // say nothing the row is not already saying.
        if let note = card.target.note ?? card.source.note {
            row.contextMenu {
                BoxRowMenu(model: model, card: card) { sheet = $0 }
            } preview: {
                notePreview(note)
            }
        } else {
            row.contextMenu {
                BoxRowMenu(model: model, card: card) { sheet = $0 }
            }
        }
    }

    /// The word as it explains itself: picture, both sides, and the catalog's
    /// note under them in the same line the card's reveal wears (`dlNoteLine`),
    /// so a gloss read here and a gloss read mid-round are the same line.
    private func notePreview(_ note: String) -> some View {
        VStack(spacing: Theme.spacing.sm) {
            Text(card.displayEmoji)
                .font(.largeTitle)
                .accessibilityHidden(true)
            citation
                .multilineTextAlignment(.center)
            Text(card.source.text)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            Text(note).dlNoteLine()
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: 300)
        .background(Theme.colors.surface)
    }

    @ViewBuilder
    private func sheetBody(_ which: BoxRowSheet) -> some View {
        switch which {
        case .report:
            // No typed answer to carry: nothing was being answered here.
            ReportIssueSheet(model: model, card: card, learnerInput: "")
        case .editReport:
            ReportIssueSheet(model: model, card: card, learnerInput: "",
                             filed: model.reportedIssue(for: card.id)?.comment ?? "")
        case .ownFrom:
            OwnWordFormView(model: model, seed: .card(card))
        case .editOwnWord:
            if let word = model.ownWord(card.id) {
                OwnWordFormView(model: model, seed: .editing(word))
            }
        }
    }

    @ViewBuilder
    private var row: some View {
        let pronounce = model.pronounceAction(
            for: card.target.text, lang: card.target.lang,
            article: CardDisplay.spokenArticle(of: card.target, shown: card.target.text)
        )

        HStack(spacing: Theme.spacing.md) {
            Text(card.displayEmoji)
                .font(.title3)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
                HStack(spacing: Theme.spacing.xs) {
                    citation
                        .lineLimit(1)
                    // why: `pronounce` is nil for exactly the words neither a
                    // recording nor the device's own voice can say — the one
                    // state where the row's tap-to-speak does nothing, so it is
                    // the one that needs to say so.
                    if pronounce == nil {
                        Image(systemName: "speaker.slash")
                            .font(.caption2)
                            .foregroundStyle(Theme.colors.textSecondary)
                            .accessibilityLabel("box.card.noAudio")
                    }
                }
                Text(card.source.text)
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: Theme.spacing.sm)
            // why: standing apart from `standing` on purpose — a report says
            // nothing about where the word stands, and a reported word keeps
            // whatever badge it had.
            if model.reportedIssue(for: card.id) != nil {
                Text(verbatim: "🚩")
                    .accessibilityLabel("report.reported")
            }
            standing
        }
        .padding(.horizontal, Theme.spacing.md)
        .padding(.vertical, Theme.spacing.xs + 2)
        .background(
            // why: the row sits INSIDE the area card now — surface on surface
            // would leave the rows without an edge of their own.
            RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                .fill(Theme.colors.surfaceTint)
        )
        .pronounceOnTap(pronounce)
        .sheet(item: $sheet) { sheetBody($0) }
    }

    /// The citation form, with its article in the gender's own color — the same
    /// mark the card face wears (`VocabCardView.headlineText`) and the same one
    /// the Android rows already draw, so a word does not lose its gender just
    /// because it is being listed instead of asked. Where the box hands over no
    /// article — a genderless target, or a rotated synonym the card's article
    /// would mislabel — the line is simply the word, uncolored.
    private var citation: Text {
        let word = Text(card.target.text)
            .font(Theme.typography.body)
            .foregroundStyle(Theme.colors.textPrimary)
        guard let article = CardDisplay.articleLabel(of: card.target, shown: card.target.text)
        else { return word }
        return Text(verbatim: "\(article.text) ")
            .font(Theme.typography.body)
            .foregroundStyle(Theme.genderColor(article.gender))
            + word
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
                .accessibilityLabel("box.card.suspended")
            Button("box.card.wake") {
                model.setSuspended(cardID: card.id, suspended: false)
            }
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.accent)
            .padding(.horizontal, Theme.spacing.md)
            .padding(.vertical, Theme.spacing.xs + 1)
            .background(Theme.colors.accent.opacity(0.14), in: Capsule())
        case .packOffered:
            if let pack {
                Button(action: pack) {
                    Image(systemName: "tray.and.arrow.down.fill")
                }
                .buttonStyle(DLIconButtonStyle())
                .accessibilityLabel("box.card.pack")
            }
        case .packed(let packed):
            if packed.removalOffered {
                // Direct tap, no confirmation: nothing has been studied yet, so taking a
                // queued word back out costs it nothing (mirrors "box.card.wake"'s own direct tap).
                Button {
                    model.dequeue(cardID: card.id)
                } label: {
                    Image(systemName: "tray.and.arrow.up.fill")
                }
                .buttonStyle(DLIconButtonStyle(color: Theme.colors.success))
                .accessibilityLabel("box.card.unpack")
            } else {
                // A pill, not an icon: a bare tray glyph reads as a control here too,
                // and this one has none — the shelf's own takes the whole queue out.
                // Clay, not green: green is the growth ladder's, and a queued word
                // is not on it yet.
                Text("box.card.queued")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.accent)
                    .padding(.horizontal, Theme.spacing.md)
                    .padding(.vertical, Theme.spacing.xs + 1)
                    .background(Theme.colors.accent.opacity(0.14), in: Capsule())
            }
        case .plain:
            EmptyView()
        case .standing(let standing):
            PhaseBadge(phase: Self.badgePhase(standing.phase),
                       consolidated: standing.consolidated,
                       growth: Color(standing.swatch))
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
