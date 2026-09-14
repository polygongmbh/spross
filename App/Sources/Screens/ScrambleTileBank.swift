import SwiftUI
import SprossKern

/// The two halves a sentence is arranged on: the order taken shape above, the
/// words still to be spent below.
///
/// A tap carries a chip up and another carries it back down — tapping is the
/// whole gesture, because a drag buys an arrangement nothing and costs assistive
/// technology a great deal (`docs/drills.md`). A spent chip stays in the bank,
/// dimmed and disabled, rather than vanishing: a bank that empties as it is used
/// moves every chip under the thumb that is aiming at one.
///
/// What is parametrized is how a word is SET and what a screen reader hears of
/// it — `DrillChoiceGrid`'s split, for the same reason: the verdict skin and the
/// interaction are shared, the typesetting of one option is not.
struct ScrambleTileBank: View {

    /// How the arrangement stands. Anything but `owed` locks every chip: the
    /// question has been answered, and an order that could still be permuted
    /// afterwards would let a learner brute-force one.
    enum Verdict {
        case owed, correct, wrong
    }

    /// The atoms in kern's own dealt order — both platforms render the same
    /// deal, so a seeded run is reproducible.
    let bank: [ScrambleAtom]
    /// The arrangement so far, in the order it was committed.
    let placed: [ScrambleAtom]
    /// Whether the dealt atom at that index has already been carried up.
    let isTaken: (Int) -> Bool
    /// What the arrangement READS as — kern's `arranged`, so the row is one
    /// VoiceOver stop saying a sentence rather than a pile of chips.
    let arranged: String
    /// How one word is set on its chip.
    var font: Font = Theme.typography.headline
    /// What a screen reader hears in place of the bare word, where the bare word
    /// is not one. nil ⇒ the word reads as itself.
    var label: ((String) -> Text)?
    var verdict: Verdict = .owed
    /// A bank slot tapped — an index into `bank`.
    let place: (Int) -> Void
    /// An answer-row slot tapped — an index into `placed`.
    let take: (Int) -> Void

    var body: some View {
        VStack(spacing: Theme.spacing.lg) {
            answerRow
            bankRow
        }
        .animation(.easeOut(duration: 0.2), value: placed.count)
        .animation(.easeOut(duration: 0.2), value: verdict)
    }

    private var locked: Bool { verdict != .owed }

    // MARK: - The arrangement

    private var answerRow: some View {
        ChipFlow(spacing: Theme.spacing.sm) {
            ForEach(placed.indices, id: \.self) { slot in
                Button { take(slot) } label: {
                    chip(placed[slot].text, dimmed: false)
                }
                .buttonStyle(TrainerChipButtonStyle())
                .disabled(locked)
                .accessibilityLabel(label?(placed[slot].text) ?? Text(verbatim: placed[slot].text))
                .accessibilityHint(Text("a11y.action.takeBack"))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Theme.spacing.md)
        // why: the row is reserved whether or not anything stands in it, so the
        // bank below never walks up the screen as the sentence is built.
        .frame(minHeight: Theme.reserve.tile, alignment: .center)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .strokeBorder(rowBorder, style: StrokeStyle(lineWidth: locked ? 2 : 1,
                                                            dash: locked ? [] : [5, 4]))
        )
        .overlay {
            if placed.isEmpty {
                Text("scramble.sentence.hint")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Theme.spacing.lg)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        // why: correctness is never color alone — the mark carries it for anyone
        // who cannot tell the two tints apart (WCAG 1.4.1).
        .overlay(alignment: .topTrailing) { mark }
        // why: `contain`, not `combine` — combining would swallow the chips, and
        // a placed word that cannot be tapped back is the thing the row exists
        // to allow. The label and the sentence so far are the container's own.
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("a11y.scramble.arrangement"))
        .accessibilityValue(Text(verbatim: arranged))
    }

    private var rowBorder: Color {
        switch verdict {
        case .owed: return Theme.colors.borderStrong
        case .correct: return Theme.colors.success
        case .wrong: return Theme.colors.wrong
        }
    }

    @ViewBuilder
    private var mark: some View {
        switch verdict {
        case .owed:
            EmptyView()
        case .correct:
            markImage("checkmark.circle.fill", tint: Theme.colors.success,
                      label: "a11y.verdict.correct")
        case .wrong:
            markImage("xmark.circle.fill", tint: Theme.colors.wrong, label: "a11y.verdict.wrong")
        }
    }

    /// The mark keeps its own VoiceOver stop rather than hiding: the tint it
    /// sits on is the only other thing saying how the arrangement was graded.
    private func markImage(_ symbol: String, tint: Color,
                           label: LocalizedStringKey) -> some View {
        Image(systemName: symbol)
            .font(.title3)
            .foregroundStyle(tint)
            .padding(Theme.spacing.sm)
            .accessibilityLabel(Text(label))
    }

    // MARK: - The words still to be spent

    private var bankRow: some View {
        ChipFlow(spacing: Theme.spacing.sm) {
            ForEach(bank.indices, id: \.self) { slot in
                Button { place(slot) } label: {
                    chip(bank[slot].text, dimmed: isTaken(slot))
                }
                .buttonStyle(TrainerChipButtonStyle())
                .disabled(locked || isTaken(slot))
                .accessibilityLabel(label?(bank[slot].text) ?? Text(verbatim: bank[slot].text))
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("a11y.scramble.bank"))
    }

    /// One word's face, the same in the bank and in the row above it — a chip
    /// that changed shape on the way up would read as a different thing.
    private func chip(_ word: String, dimmed: Bool) -> some View {
        Text(verbatim: word)
            .font(font)
            .foregroundStyle(Theme.colors.textPrimary)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .padding(.horizontal, Theme.spacing.md)
            // why: a chip is a thumb target before it is a word — 44 pt is the
            // floor a tap may be aimed at.
            .frame(minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surfaceTint)
            )
            .opacity(dimmed ? 0.35 : 1)
    }
}

// MARK: - Wrapping by content width

/// Chips left to right, wrapping where the line runs out, each row centered.
///
/// The one place in the app that wraps by CONTENT width rather than into equal
/// columns, and the tile bank is what earns it: the words are the lengths they
/// are, and a grid that gave "und" the same width as "Krankenhaus" would say the
/// two are the same size of thing. Deliberately local to this file — a shared
/// flow layout is a primitive nothing else has asked for.
private struct ChipFlow: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(subviews, width: proposal.width ?? .infinity)
        let height = rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? (rows.map(\.width).max() ?? 0), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(subviews, width: bounds.width) {
            var x = bounds.minX + max(bounds.width - row.width, 0) / 2
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2),
                                      anchor: .topLeading,
                                      proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var row = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if !row.indices.isEmpty, row.width + spacing + size.width > width {
                rows.append(row)
                row = Row()
            }
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
        }
        if !row.indices.isEmpty { rows.append(row) }
        return rows
    }
}
