import SwiftUI
import SprossKern

/// The day's card once the day has answered more than it still owes and kern
/// leads with a way in other than the round (`DayLead`, `docs/drills.md`
/// § The suggestion): what the day has done on top, the drill, listening or the
/// conversation as the card's body and first action, and the round still one
/// button away.
extension HomeView {

    func leadCard(emoji: String, title: Text, reason: Text, open: @escaping () -> Void) -> some View {
        VStack(spacing: Theme.spacing.lg) {
            dayHeader
            Divider()
            Text(verbatim: emoji)
                .font(.system(size: 56)) // card-parity: the card's own glyph, not a card prompt
                .accessibilityHidden(true)
            title
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
            reason
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
            Button(action: open) {
                ActionLabel(key: "home.offer.start", targetLocale: model.targetChromeLocale)
            }
            .buttonStyle(PrimaryButtonStyle())
            // The round with reviews still due; an extra one once none are (kern's `roundInstead`).
            if let round = model.home?.roundInstead {
                Button("home.suggestion.wordsInstead") {
                    if round == .due { model.startSession() } else { model.startExtraSession() }
                }
                .buttonStyle(SoftButtonStyle())
            }
        }
        .homeCard()
    }

    /// What the day has done, compact: the check and its title, the run, the tally.
    private var dayHeader: some View {
        HStack(spacing: Theme.spacing.md) {
            VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                Label("home.done.title", systemImage: "checkmark.circle.fill")
                    .font(Theme.typography.headline)
                    .foregroundStyle(Theme.colors.textPrimary)
                if let today = model.today, today.worked {
                    todayTally(today)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
            }
            Spacer(minLength: 0)
            dayMark(.companion.lead(streak: Int32(model.stats?.streakDays ?? 0)))
        }
    }
}
