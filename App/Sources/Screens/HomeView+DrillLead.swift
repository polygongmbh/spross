import SwiftUI
import SprossKern

/// The day's card once the day has answered more than it still owes and kern
/// names a drill (`DayLead.drill`, `docs/drills.md` § The suggestion): what the
/// day has done on top, the drill as the card's body and first action, and the
/// round still one button away.
extension HomeView {

    func drillLeadCard(_ offer: SessionOffer, _ pick: DrillSuggestion.Pick,
                       open: @escaping () -> Void) -> some View {
        VStack(spacing: Theme.spacing.lg) {
            dayHeader
            Divider()
            Text(verbatim: pick.drill.emoji)
                .font(.system(size: 56)) // card-parity: the card's own glyph, not a card prompt
                .accessibilityHidden(true)
            Text("home.suggestion.title \(ChromeStrings.string(pick.drill.titleKeyName, locale: locale))")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
            pick.reasonText
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
            Button(action: open) {
                ActionLabel(key: "home.offer.start", targetLocale: model.targetChromeLocale)
            }
            .buttonStyle(PrimaryButtonStyle())
            // The round with reviews still due; an extra one once none are.
            if offer.hasRound || model.canPracticeMore {
                Button("home.suggestion.wordsInstead") {
                    if offer.hasRound { model.startSession() } else { model.startExtraSession() }
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
            if let streak = model.stats?.streakDays, streak > 0 {
                StreakFlameView(days: streak, health: model.stats?.streakHealth ?? .noRun)
            }
        }
    }
}
