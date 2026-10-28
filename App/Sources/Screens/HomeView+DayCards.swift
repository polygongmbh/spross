import SwiftUI
import SprossKern

/// Home's day cards: the session offer, the done-for-today card, and the error state.
extension HomeView {

    // MARK: - Session available

    func sessionCard(_ offer: SessionOffer) -> some View {
        VStack(spacing: Theme.spacing.lg) {
            sessionStats
            Text(LocalizedStringKey(offer.headlineKey))
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
            sessionSummary(offer)
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
            if offer.dueHeldBack > 0 {
                // Names the held-back rest, so a backlog never looks like vanished cards.
                // why: a plural key varies only on an Int, not the engine's Int32.
                Text("home.offer.heldBack \(Int(offer.dueHeldBack))")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            Button {
                model.startSession()
            } label: {
                ActionLabel(key: "home.offer.start", targetLocale: model.targetChromeLocale)
            }
            .buttonStyle(PrimaryButtonStyle())
            // Kern offers the short round when it differs enough from the full one.
            if offer.shortRound > 0 {
                Button("home.offer.shortRound") {
                    model.startShortSession()
                }
                .buttonStyle(SoftButtonStyle())
            }
        }
        .homeCard()
    }

    /// The run's flame, or a sprout with no run to show; the card stands while today's work is owed.
    ///
    /// No progress ring: with no daily quota, an arc divides work done by work still queued,
    /// which reads near full from the second round on.
    var sessionStats: some View {
        dayMark(.companion.offer(streak: Int32(model.stats?.streakDays ?? 0)))
    }

    /// The day's mark as kern names it (`DayMark`): the card's own emoji or the run's flame,
    /// with the run's count beside it as one badge.
    @ViewBuilder
    func dayMark(_ mark: DayMark) -> some View {
        let streak = model.stats?.streakDays ?? 0
        if mark.counted, let emoji = mark.emoji {
            StreakFlameView(days: streak, emoji: emoji)
        } else if mark.counted {
            StreakFlameView(days: streak, health: model.stats?.streakHealth ?? .noRun)
        } else if let emoji = mark.emoji {
            Text(verbatim: emoji)
                .font(.system(size: 56)) // card-parity: the card's own glyph, not a card prompt
                .accessibilityHidden(true)
        }
    }

    /// "12 Checks · 5 Neue": the parts and their order are kern's (`SessionOffer.summaryParts`).
    /// Built as `Text` so each part localizes with the catalog's plurals.
    func sessionSummary(_ offer: SessionOffer) -> Text {
        let parts = offer.summaryParts()
        return parts.map { offerPartText($0, alone: parts.count == 1) }.joined() ?? Text("home.tally.someCards")
    }

    func offerPartText(_ part: OfferPart, alone: Bool) -> Text {
        // why: a plural key varies only on an Int, not the engine's Int32.
        let count = Int(part.count)
        switch part.kind {
        case .reviews: return Text("home.tally.reviews \(count)")
        case .ahead: return Text("home.tally.ahead \(count)")
        case .newCards:
            return alone
                ? Text("home.tally.newWordsOnly \(count.formatted())")
                : Text("home.tally.newCards \(count)")
        }
    }

    // MARK: - Nothing due (done for today / caught up)

    /// "Done" once the day has been worked, else "caught up", which claims no finish.
    ///
    /// Ordered like every celebration screen: mark, headline, the day's tally, the way on, fine print.
    /// The mark and the streak are one badge.
    var doneCard: some View {
        let today = model.today
        let worked = today?.worked ?? false
        return VStack(spacing: Theme.spacing.lg) {
            doneMark(worked: worked)
            Text(worked ? "home.done.title" : "home.done.caughtUp")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
            if let today, worked {
                todayTally(today)
                    .font(Theme.typography.body)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            // Hidden only when the box has nothing left to compose.
            if model.canPracticeMore {
                Button("home.done.extraRound") {
                    model.startExtraSession()
                }
                .buttonStyle(SoftButtonStyle())
            }
            // Under the button: what comes next is the card's smallest item.
            tomorrowText
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
        }
        .homeCard()
    }

    func doneMark(worked: Bool) -> some View {
        dayMark(.companion.done(worked: worked, streak: Int32(model.stats?.streakDays ?? 0)))
    }

    /// "3 Neue · 24 Checks": the parts and their order are kern's (`TodayReport.tallyParts`).
    func todayTally(_ report: TodayReport) -> Text {
        report.tallyParts().map(tallyText).joined() ?? Text("home.tally.someCards")
    }

    func tallyText(_ part: TallyPart) -> Text {
        let count = Int(part.count)
        switch part.kind {
        case .introduced: return Text("home.tally.newCards \(count)")
        // The day names no settled part (`TodayReport.tallyParts`).
        case .reviewed, .settled: return Text("home.tally.reviews \(count)")
        }
    }

    /// What comes next after a done day; which of the three notes is kern's (`tomorrowNote`).
    var tomorrowText: Text {
        switch model.tomorrowNote {
        case .queued: return Text("home.done.queued")
        case .empty: return Text("home.done.tomorrowFresh")
        case .due: return Text("home.done.tomorrowDue \(model.tomorrowDueCount)")
        }
    }

    // MARK: - Error state card

    func stateCard(emoji: String, title: LocalizedStringKey, message: Text) -> some View {
        VStack(spacing: Theme.spacing.lg) {
            Text(emoji)
                .font(.system(size: 56)) // card-parity: the card's own glyph, not a card prompt
                .accessibilityHidden(true)
            Text(title)
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
            message
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
        }
        .homeCard()
    }
}

extension View {
    /// The surface every card on Home stands on.
    func homeCard(alignment: Alignment = .center) -> some View {
        padding(Theme.spacing.xl)
            .frame(maxWidth: .infinity, alignment: alignment)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                    .fill(Theme.colors.surface)
            )
            .cardShadow()
    }
}
