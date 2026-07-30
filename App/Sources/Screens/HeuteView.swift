import SwiftUI
import SprossKern

/// The north star screen: one glance = what to do right now.
struct HeuteView: View {
    let model: AppModel
    var openBox: () -> Void = {}

    @Environment(\.locale) private var locale

    var body: some View {
        let offer = model.heuteOffer
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                if let failure = model.loadFailure {
                    stateCard(emoji: "🫤",
                              title: "error.title",
                              message: failure.text)
                } else if offer.kind != .nothing {
                    sessionCard(offer)
                } else if (model.stats?.activeCards ?? 0) > 0 {
                    doneCard
                } else {
                    stateCard(emoji: "📦",
                              title: "heute.empty.title",
                              message: Text("heute.empty.message"),
                              action: ("heute.empty.action", openBox))
                }
                TrainerHubView(model: model)
                FortschrittSection(model: model)
            }
            .padding(DL.Space.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(Date().formatted(
                Date.FormatStyle(locale: locale)
                    .weekday(.wide).day().month(.wide)
            ))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            Text("heute.title")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
        }
    }

    // MARK: - Session available

    private func sessionCard(_ offer: AppModel.HeuteOffer) -> some View {
        VStack(spacing: DL.Space.l) {
            sessionStats(offer)
            Text(offer.kind == .reviews ? "heute.session.reviewsReady" : "heute.session.freshReady")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            sessionSummary(offer)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            if offer.dueHeldBack > 0 {
                // The cap is a promise, not a loss: name the rest so a backlog
                // never looks like cards that vanished.
                Text("heute.session.heldBack \(offer.dueHeldBack.formatted())")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
            Button {
                model.startSession()
            } label: {
                DLActionLabel(key: "heute.session.start", targetLocale: model.targetChromeLocale)
            }
            .buttonStyle(DLPrimaryButtonStyle())
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }

    /// Ring + flame hero; either hides when it has nothing to say
    /// (all-new-card plan → no ring, streak 0 → no flame).
    /// The ring counts the reviews THIS round takes, not the whole due pile —
    /// the run is capped, and a number the round won't honour would be a broken promise.
    @ViewBuilder
    private func sessionStats(_ offer: AppModel.HeuteOffer) -> some View {
        let streak = model.stats?.streakDays ?? 0
        if offer.sessionReviews == 0 && streak == 0 {
            Text(verbatim: "✨")
                .font(.system(size: 56))
                .accessibilityHidden(true)
        } else {
            HStack(spacing: DL.Space.l) {
                if offer.sessionReviews > 0 {
                    DueCountRing(remaining: offer.sessionReviews,
                                 total: offer.sessionReviews + model.reviewsDoneToday,
                                 size: 80)
                }
                if streak > 0 {
                    StreakFlameView(days: streak)
                }
            }
        }
    }

    /// Built as `Text` (not a joined String) so each part localizes
    /// via the environment locale with catalog plural handling.
    /// Every part of the round is named for what it is — due, pulled forward, or never seen.
    private func sessionSummary(_ offer: AppModel.HeuteOffer) -> Text {
        var parts: [Text] = []
        if offer.sessionReviews > 0 {
            parts.append(Text("heute.session.reviews \(offer.sessionReviews.formatted())"))
        }
        if offer.freshCount > 0 {
            parts.append(Text("heute.session.newCards \(offer.freshCount.formatted())"))
        }
        if offer.aheadCount > 0 {
            parts.append(Text("heute.session.ahead \(offer.aheadCount.formatted())"))
        }
        return parts.joined() ?? Text("heute.session.someCards")
    }

    // MARK: - Nothing due (done for today / caught up)

    /// "Done" only once the day has actually been worked;
    /// otherwise nothing is due right now, which is a different message
    /// and must not claim a finish the learner never made.
    private var doneCard: some View {
        let worked = model.reviewsDoneToday > 0
        return VStack(spacing: DL.Space.l) {
            Text(verbatim: worked ? "🎉" : "🌱")
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text(worked ? "heute.done.title" : "heute.caughtUp.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            StreakFlameView(days: model.stats?.streakDays ?? 0)
            tomorrowText
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            // User agency: an extra round is endless-style when that has
            // content (due + NEW vocab within budget/health gate), else
            // review-ahead — so it renders in every done state with active
            // cards; hidden only when even the fallback is empty.
            if model.canPracticeExtra {
                Button("heute.done.extraRound") {
                    model.startExtraSession()
                }
                .buttonStyle(DLSoftButtonStyle())
            }
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }

    private var tomorrowText: Text {
        model.tomorrowDueCount == 0
            ? Text("heute.done.tomorrowFresh")
            : Text("heute.done.tomorrowDue \(model.tomorrowDueCount.formatted())")
    }

    // MARK: - Generic state card (error / empty box)

    private func stateCard(emoji: String, title: LocalizedStringKey, message: Text,
                           action: (label: LocalizedStringKey, run: () -> Void)? = nil) -> some View {
        VStack(spacing: DL.Space.l) {
            Text(emoji)
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text(title)
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            message
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            if let action {
                Button(action.label, action: action.run)
                    .buttonStyle(DLSoftButtonStyle())
            }
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
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
