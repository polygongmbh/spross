import SwiftUI

/// The face Home's ways in share — listening, the companion, the suggested
/// drill: the emoji leads, the title names the mode once, and the subtitle
/// carries what the name cannot. The whole card is the tap target.
struct WayInCard: View {
    let emoji: String
    let title: Text
    let subtitle: Text

    var body: some View {
        HStack(alignment: .top, spacing: Theme.spacing.md) {
            Text(verbatim: emoji)
                .font(.title2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                title
                    .font(Theme.typography.title)
                    .foregroundStyle(Theme.colors.textPrimary)
                subtitle
                    .font(Theme.typography.subheadline)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .font(.title3)
                .foregroundStyle(Theme.colors.textSecondary)
                .accessibilityHidden(true)
        }
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
    }
}
