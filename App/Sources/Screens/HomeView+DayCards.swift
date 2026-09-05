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
                // The cap is a promise, not a loss: name the rest so a backlog
                // never looks like cards that vanished.
                // why: Int, not the engine's Int32 — a plural key only varies
                // on a count the String Catalog recognises.
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
            // A long round is more than an evening some days, and an abandoned one leaves
            // the day unworked; kern says when the two are different enough to offer both.
            if offer.shortRound > 0 {
                Button("home.offer.shortRound") {
                    model.startShortSession()
                }
                .buttonStyle(SoftButtonStyle())
            }
        }
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
    }

    /// Flame hero, or a sprout when there is no streak to show. This card is up
    /// exactly while today's work is still owed, so the flame it wears is the one
    /// place the run's exposure is worth seeing.
    ///
    /// No progress ring here: a growing box sets no daily quota, so any arc has to
    /// divide work done by work still queued — and both climb through the day, which
    /// leaves the ring near-full from the second round on and fullest exactly when a
    /// capped backlog is worst. The counts below say it without the false comfort.
    @ViewBuilder
    var sessionStats: some View {
        let streak = model.stats?.streakDays ?? 0
        if streak > 0 {
            StreakFlameView(days: streak, flame: model.stats?.flame ?? .unlit)
        } else {
            Text(verbatim: "✨")
                .font(.system(size: 56))
                .accessibilityHidden(true)
        }
    }

    /// "12 Checks · 5 Neue" — which counts the round names and in
    /// which order is the offer's own rule (`SessionOffer.summaryParts`); the words are ours.
    /// Built as `Text` (not a joined String) so each part localizes
    /// via the environment locale with catalog plural handling.
    func sessionSummary(_ offer: SessionOffer) -> Text {
        let parts = offer.summaryParts()
        return parts.map { offerPartText($0, alone: parts.count == 1) }.joined() ?? Text("home.tally.someCards")
    }

    func offerPartText(_ part: OfferPart, alone: Bool) -> Text {
        // why: the count crosses into Int here — the engine counts in Int32, and
        // a plural key only varies on a count the String Catalog recognises.
        let count = Int(part.count)
        switch part.kind {
        case .reviews: return Text("home.tally.reviews \(count)")
        case .ahead: return Text("home.tally.ahead \(count)")
        case .fresh:
            return alone
                ? Text("home.tally.newWordsOnly \(count.formatted())")
                : Text("home.tally.newCards \(count)")
        }
    }

    // MARK: - Nothing due (done for today / caught up)

    /// "Done" only once the day has actually been worked;
    /// otherwise nothing is due right now, which is a different message
    /// and must not claim a finish the learner never made.
    ///
    /// Ordered like every other celebration screen in the app — mark, headline, what the
    /// day bought, the way on, fine print. The mark and the streak are ONE badge: as two
    /// elements they sandwiched the prose between them, and a card that both cheers and
    /// counts the run says one thing, not two.
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
                // What the day actually bought, not just that it happened.
                todayTally(today)
                    .font(Theme.typography.body)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            // User agency: an extra round is an ordinary round composed on demand, so it
            // renders in every done state with active cards; hidden only when the box has
            // nothing left to compose at all.
            if model.canPracticeMore {
                Button("home.done.extraRound") {
                    model.startExtraSession()
                }
                .buttonStyle(SoftButtonStyle())
            }
            // Under the button on purpose: what happens next is the smallest thing on
            // the card, and the way on is what the thumb is looking for.
            tomorrowText
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
    }

    /// The day's mark: the celebration wearing the streak, or the bare emoji when there
    /// is no run to name yet — the same fallback `sessionStats` makes with ✨, and the
    /// reason the badge is guarded at all: unguarded it put a flame over "0 Tage" for
    /// anyone who had not started one.
    @ViewBuilder
    func doneMark(worked: Bool) -> some View {
        let emoji = worked ? "🎉" : "🌱"
        let streak = model.stats?.streakDays ?? 0
        if streak > 0 {
            StreakFlameView(days: streak, emoji: emoji)
        } else {
            Text(verbatim: emoji)
                .font(.system(size: 56))
                .accessibilityHidden(true)
        }
    }

    /// "24 Checks · 3 Neue · 2 gefestigt" — the day's gain, not just
    /// that it happened. Which counts the day names and in which order is the day's
    /// own report (`TodayReport.tallyParts`); the words are ours.
    func todayTally(_ report: TodayReport) -> Text {
        report.tallyParts().map(tallyText).joined() ?? Text("home.tally.someCards")
    }

    func tallyText(_ part: TallyPart) -> Text {
        let count = Int(part.count)
        switch part.kind {
        case .reviews: return Text("home.tally.reviews \(count)")
        case .introduced: return Text("home.tally.newCards \(count)")
        case .consolidated: return Text("home.tally.consolidated \(count.formatted())")
        }
    }

    /// A finished day composes nothing, so words packed on one only arrive through the round
    /// above — said as a fact about that round, in the smallest type on the card, because
    /// the pack was the learner's move and does not need answering. Which of the three
    /// notes a done day leaves is kern's (`tomorrowNote`).
    var tomorrowText: Text {
        switch tomorrowNote(hasPackedWords: model.hasPackedWords,
                            tomorrowDue: Int32(model.tomorrowDueCount)) {
        case .packed: return Text("home.done.packed")
        case .fresh: return Text("home.done.tomorrowFresh")
        case .due: return Text("home.done.tomorrowDue \(model.tomorrowDueCount)")
        }
    }

    // MARK: - Error state card

    func stateCard(emoji: String, title: LocalizedStringKey, message: Text) -> some View {
        VStack(spacing: Theme.spacing.lg) {
            Text(emoji)
                .font(.system(size: 56))
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
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
    }
}
