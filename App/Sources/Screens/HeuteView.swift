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
            sessionStats
            Text(LocalizedStringKey(offer.headlineKey))
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
                Text("heute.session.heldBack \(offer.dueHeldBack)")
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

    /// Flame hero, or a sprout when there is no streak to show.
    ///
    /// No progress ring here: a growing box sets no daily quota, so any arc has to
    /// divide work done by work still queued — and both climb through the day, which
    /// leaves the ring near-full from the second round on and fullest exactly when a
    /// capped backlog is worst. The counts below say it without the false comfort.
    @ViewBuilder
    private var sessionStats: some View {
        let streak = model.stats?.streakDays ?? 0
        if streak > 0 {
            StreakFlameView(days: streak)
        } else {
            Text(verbatim: "✨")
                .font(.system(size: 56))
                .accessibilityHidden(true)
        }
    }

    /// Built as `Text` (not a joined String) so each part localizes
    /// via the environment locale with catalog plural handling.
    /// Words already met and words never seen read differently, so those stay apart —
    /// but a pulled-forward card is the same act of recalling as a due one,
    /// so it counts into the repetitions instead of standing as its own pile.
    /// Only when it carries the round alone does it get named: Auffrischer.
    private func sessionSummary(_ offer: AppModel.HeuteOffer) -> Text {
        var parts: [Text] = []
        if offer.sessionReviews > 0 {
            let repetitions = offer.sessionReviews + offer.aheadCount
            parts.append(Text("heute.session.reviews \(repetitions)"))
        } else if offer.aheadCount > 0 {
            parts.append(Text("heute.session.ahead \(offer.aheadCount)"))
        }
        if offer.freshCount > 0 {
            parts.append(Text("heute.session.newCards \(offer.freshCount)"))
        }
        return parts.joined() ?? Text("heute.session.someCards")
    }

    // MARK: - Nothing due (done for today / caught up)

    /// "Done" only once the day has actually been worked;
    /// otherwise nothing is due right now, which is a different message
    /// and must not claim a finish the learner never made.
    private var doneCard: some View {
        let today = model.today
        let worked = (today?.reviews ?? 0) > 0
        return VStack(spacing: DL.Space.l) {
            Text(verbatim: worked ? "🎉" : "🌱")
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text(worked ? "heute.done.title" : "heute.caughtUp.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            if let today, worked {
                // What the day actually bought, not just that it happened.
                todayTally(today)
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
            StreakFlameView(days: model.stats?.streakDays ?? 0)
            tomorrowText
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            // User agency: an extra round is endless-style when that has
            // content (due + NEW vocab within the round's budget), else
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

    /// "24 Wiederholungen · 3 Frischlinge · 2 gefestigt" — the day's gain, not just
    /// that it happened. Consolidated crossings lead nothing: they are the rarest part
    /// and the one worth reading last.
    private func todayTally(_ report: TodayReport) -> Text {
        var parts: [Text] = [Text("heute.session.reviews \(Int(report.reviews))")]
        if report.introduced > 0 {
            parts.append(Text("heute.session.newCards \(Int(report.introduced))"))
        }
        if report.consolidated > 0 {
            parts.append(Text("heute.done.consolidated \(Int(report.consolidated).formatted())"))
        }
        return parts.joined() ?? Text("heute.session.someCards")
    }

    private var tomorrowText: Text {
        model.tomorrowDueCount == 0
            ? Text("heute.done.tomorrowFresh")
            : Text("heute.done.tomorrowDue \(model.tomorrowDueCount)")
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
