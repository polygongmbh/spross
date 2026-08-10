import SwiftUI

/// One row of a list you choose from: a mark, a title, an optional caption.
///
/// Onboarding picks exactly ONE language per list; the numbers overview picks
/// N of M variants — the same row with a different mark, so the two surfaces
/// cannot drift apart on padding, tint or the selected border.
///
/// A row that has not been earned keeps its place and states its price instead
/// of vanishing: a ladder you can see is a reason to climb, an absence is not.
struct DLSelectionRow: View {

    /// How many of the list may be on at once — and whether this one may be
    /// chosen at all.
    enum Mark {
        /// Exactly one of the list (a radio).
        case one
        /// Any number of the list (a checkbox).
        case many
        /// Not yet unlocked: a padlock, and no response to a tap.
        case locked
        /// The whole list, folded shut onto the choice already made:
        /// a chevron where the mark would be, and a tap that opens it again.
        case fold
    }

    let title: Text
    var caption: Text?
    let mark: Mark
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: DL.Space.m) {
                if let symbol {
                    Image(systemName: symbol)
                        .font(.title3)
                        .foregroundStyle(markColor)
                }
                VStack(alignment: .leading, spacing: 2) {
                    title
                        .font(DL.Fonts.headline)
                        .foregroundStyle(titleColor)
                    if let caption {
                        caption
                            .font(DL.Fonts.caption)
                            .foregroundStyle(Color.dlTextSecondary)
                    }
                }
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if isFold {
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(Color.dlTextSecondary)
                }
            }
            .padding(DL.Space.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background)
        }
        .buttonStyle(.plain)
        .disabled(isLocked)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var isLocked: Bool { if case .locked = mark { return true }; return false }

    private var isFold: Bool { if case .fold = mark { return true }; return false }

    private var symbol: String? {
        switch mark {
        case .one: return selected ? "checkmark.circle.fill" : "circle"
        case .many: return selected ? "checkmark.square.fill" : "square"
        case .locked: return "lock.fill"
        case .fold: return nil
        }
    }

    private var markColor: Color { selected ? .dlAccent : .dlTextSecondary }

    /// A locked row is readable but plainly out of reach — its caption says why.
    private var titleColor: Color { isLocked ? .dlTextSecondary : .dlTextPrimary }

    private var background: some View {
        RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
            .fill(selected ? Color.dlSurfaceTint : Color.dlSurface)
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .strokeBorder(selected ? Color.dlAccent : Color.dlSeparator,
                                  lineWidth: selected ? 2 : 1)
            )
    }
}
