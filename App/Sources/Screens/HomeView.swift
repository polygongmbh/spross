import SwiftUI
import SprossKern

/// The north star screen: one glance = what to do right now.
struct HomeView: View {
    let model: AppModel
    /// Open the box — at one area when the forest names it, else at the top.
    var openBox: (String?) -> Void = { _ in }

    @Environment(\.locale) var locale

    @State private var listeningPresented = false

    var body: some View {
        let offer = model.homeOffer
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                header
                voiceUpgradeBanner
                if let failure = model.loadFailure {
                    stateCard(emoji: "🫤",
                              title: "error.title",
                              message: failure.text)
                } else if offer.kind != .nothing {
                    sessionCard(offer)
                } else {
                    doneCard
                }
                listeningCard
                TrainerHubView(model: model)
                ForestSection(model: model, open: { openBox($0) })
            }
            .padding(Theme.spacing.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Theme.colors.background.ignoresSafeArea())
        .fullScreenCover(isPresented: $listeningPresented) {
            ListeningView(model: model)
                .environment(\.locale, model.knownLocale)
        }
    }

    // MARK: - Listening

    /// Under the day's round and above the Sprossen: not what the box asks of
    /// the learner, and not a skill with a ladder to climb, but the way in that
    /// needs no hands — up whenever this device can say both sides of enough
    /// words (`docs/design.md`, `docs/surfaces.md` § Listening).
    ///
    /// A full card like the trainer hub beside it: the emoji leads, the title
    /// names the mode once, and the subtitle carries the two facts the name
    /// cannot — the whole card is the tap target.
    @ViewBuilder
    private var listeningCard: some View {
        // why: a box with words in it is the whole gate. Every catalog language
        // but `en` ships several hundred recordings and `en` is spoken by every
        // device there is, so a joined box with nothing sayable in it does not
        // occur — and proving that again on every glance at Home is a walk of
        // the whole join. The playlist is dealt when the run opens.
        if model.box?.cards.isEmpty == false {
            Button { listeningPresented = true } label: {
                HStack(alignment: .top, spacing: Theme.spacing.md) {
                    Text(verbatim: "🎧")
                        .font(.title2)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                        Text("listen.title")
                            .font(Theme.typography.title)
                            .foregroundStyle(Theme.colors.textPrimary)
                        Text("listen.subtitle")
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
            .buttonStyle(.plain)
        }
    }

    // MARK: - Voice upgrade

    /// Said once, above the day's card: the words are being read by the compact
    /// system voice and a much better one is a free download. It cannot be a
    /// link — no public URL opens the Voices pane, and one that landed on the
    /// app's own settings page instead would send the learner somewhere the
    /// setting is not — so the path is spelled out and the banner is a notice,
    /// not a button. Dismissing is permanent; the settings row keeps it.
    @ViewBuilder
    private var voiceUpgradeBanner: some View {
        let hint = VoiceUpgradeHint.shared
        if hint.suggestsBanner(language: model.targetLanguage,
                               activeCards: model.stats?.activeCards ?? 0) {
            HStack(alignment: .top, spacing: Theme.spacing.md) {
                Image(systemName: "speaker.wave.2")
                    .foregroundStyle(Theme.colors.accent)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                    Text("home.voiceUpgrade.title \(targetLanguageName ?? "?")")
                        .font(Theme.typography.headline)
                        .foregroundStyle(Theme.colors.textPrimary)
                    Text("home.voiceUpgrade.path")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
                Spacer(minLength: 0)
                Button {
                    withAnimation { hint.dismissBanner() }
                } label: {
                    Image(systemName: "xmark")
                        .font(.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
                .accessibilityLabel(Text("common.dismiss"))
            }
            .padding(Theme.spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surface)
            )
            .cardShadow()
        }
    }

    // MARK: - Session available

    private func sessionCard(_ offer: SessionOffer) -> some View {
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
    private var sessionStats: some View {
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
    private func sessionSummary(_ offer: SessionOffer) -> Text {
        let parts = offer.summaryParts()
        return parts.map { offerPartText($0, alone: parts.count == 1) }.joined() ?? Text("home.tally.someCards")
    }

    private func offerPartText(_ part: OfferPart, alone: Bool) -> Text {
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
    private var doneCard: some View {
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
    private func doneMark(worked: Bool) -> some View {
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
    private func todayTally(_ report: TodayReport) -> Text {
        report.tallyParts().map(tallyText).joined() ?? Text("home.tally.someCards")
    }

    private func tallyText(_ part: TallyPart) -> Text {
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
    private var tomorrowText: Text {
        switch tomorrowNote(hasPackedWords: model.hasPackedWords,
                            tomorrowDue: Int32(model.tomorrowDueCount)) {
        case .packed: return Text("home.done.packed")
        case .fresh: return Text("home.done.tomorrowFresh")
        case .due: return Text("home.done.tomorrowDue \(model.tomorrowDueCount)")
        }
    }

    // MARK: - Error state card

    private func stateCard(emoji: String, title: LocalizedStringKey, message: Text) -> some View {
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

extension LoadFailure {
    /// Error chrome as `Text`, so it resolves against the environment locale
    /// like every other string. The system `reason` stays as the OS wrote it.
    var text: Text {
        switch self {
        case .catalogMissing:
            return Text("error.catalogMissing")
        case .unknownProfile(let source, let target):
            return Text("error.unknownProfile \(source) \(target)")
        case .contentUnavailable(let reason):
            return Text("error.contentUnavailable \(reason)")
        case .resetFailed(let reason):
            return Text("error.resetFailed \(reason)")
        }
    }
}
