import SwiftUI

/// A wrapping headword that breaks only between its words.
///
/// While its widest word is wider than the line, the text steps down one Dynamic Type
/// size at a time until that word fits — "Guten Tag!" at the largest accessibility
/// size stays "Guten" / "Tag!" instead of "Gute" / "n" / "Tag!". A word that fits at
/// the reader's own size renders exactly as a plain `Text` would; past the smallest
/// size the line wraps inside the word after all.
struct WholeWords: View {
    let text: Text
    /// Everything `text` says, so its widest word can be measured on its own.
    let content: String
    let font: Font

    @Environment(\.dynamicTypeSize) private var size

    var body: some View {
        let sizes = DynamicTypeSize.allCases.filter { $0 <= size }.reversed()
        ViewThatFits(in: .horizontal) {
            ForEach(Array(sizes), id: \.self) { candidate in
                IdealWidthLayout {
                    widestWordProbe
                    text
                }
                .dynamicTypeSize(candidate)
            }
        }
    }

    private var widestWordProbe: some View {
        VStack(spacing: 0) {
            ForEach(Array(content.split(whereSeparator: \.isWhitespace).enumerated()),
                    id: \.offset) { _, word in
                Text(verbatim: String(word)).font(font).fixedSize()
            }
        }
        .hidden()
        .accessibilityHidden(true)
    }
}

/// Offers its first subview's width as the ideal width, and lays out the second — the
/// one that draws — at whatever width it is given. `ViewThatFits` measures ideal widths,
/// so a candidate fits exactly when its widest word does.
private struct IdealWidthLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard proposal.width == nil else { return subviews[1].sizeThatFits(proposal) }
        let width = subviews[0].sizeThatFits(.unspecified).width
        return subviews[1].sizeThatFits(ProposedViewSize(width: width, height: proposal.height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        subviews[0].place(at: bounds.origin, proposal: .zero)
        subviews[1].place(at: bounds.origin, proposal: ProposedViewSize(bounds.size))
    }
}
