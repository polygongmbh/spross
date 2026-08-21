import SwiftUI
import SprossKern

/// The north star screen: one glance = what to do right now.
struct HeuteView: View {
    let model: AppModel
    /// Open the box — at one area when the forest names it, else at the top.
    var openBox: (String?) -> Void = { _ in }

    @Environment(\.locale) var locale
    @Environment(\.scenePhase) private var scenePhase

    /// What listening can play on THIS device. Rebuilt on every foreground,
    /// never decided once: a voice installed in Settings while the app slept
    /// must put the card up without a relaunch (`ListeningAvailability`).
    @State private var listening: ListeningAvailability?
    @State private var listeningPresented = false

    var body: some View {
        let offer = model.heuteOffer
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                voiceUpgradeBanner
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
                              action: ("heute.empty.action", { openBox(nil) }))
                }
                listeningCard
                TrainerHubView(model: model)
                ForestSection(model: model, open: { openBox($0) })
            }
            .padding(DL.Space.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
        .onAppear { refreshListening() }
        // why: ACTIVE, not willEnterForeground — the speaker drops its cached
        // voice table on that notification, and the pool must read the new one.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshListening() }
        }
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
    @ViewBuilder
    private var listeningCard: some View {
        if listening?.available == true {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                Text("listen.title")
                    .font(DL.Fonts.title)
                    .foregroundStyle(Color.dlTextPrimary)
                Text("listen.subtitle")
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextSecondary)
                Button("listen.start") { listeningPresented = true }
                    .buttonStyle(DLSoftButtonStyle())
            }
            .padding(DL.Space.xl)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .dlCardShadow()
        }
    }

    private func refreshListening() {
        listening = ListeningAvailability(model: model)
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
            HStack(alignment: .top, spacing: DL.Space.m) {
                Image(systemName: "speaker.wave.2")
                    .foregroundStyle(Color.dlAccent)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: DL.Space.xs) {
                    Text("heute.voiceUpgrade.title \(targetLanguageName ?? "?")")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Text("heute.voiceUpgrade.path")
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                Spacer(minLength: 0)
                Button {
                    withAnimation { hint.dismissBanner() }
                } label: {
                    Image(systemName: "xmark")
                        .font(.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                .accessibilityLabel(Text("common.dismiss"))
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .dlCardShadow()
        }
    }

    // MARK: - Session available

    private func sessionCard(_ offer: SessionOffer) -> some View {
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
                // why: Int, not the engine's Int32 — a plural key only varies
                // on a count the String Catalog recognises.
                Text("heute.session.heldBack \(Int(offer.dueHeldBack))")
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
            // A long round is more than an evening some days, and an abandoned one leaves
            // the day unworked; kern says when the two are different enough to offer both.
            if offer.shortRound > 0 {
                Button("heute.session.shortRound") {
                    model.startShortSession()
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

    /// "12 Wiederholungen · 5 neue Wörter" — which counts the round names and in
    /// which order is the offer's own rule (`SessionOffer.summaryParts`); the words are ours.
    /// Built as `Text` (not a joined String) so each part localizes
    /// via the environment locale with catalog plural handling.
    private func sessionSummary(_ offer: SessionOffer) -> Text {
        offer.summaryParts().map(offerPartText).joined() ?? Text("heute.session.someCards")
    }

    private func offerPartText(_ part: OfferPart) -> Text {
        // why: the count crosses into Int here — the engine counts in Int32, and
        // a plural key only varies on a count the String Catalog recognises.
        let count = Int(part.count)
        switch part.kind {
        case .reviews: return Text("heute.session.reviews \(count)")
        case .ahead: return Text("heute.session.ahead \(count)")
        case .fresh: return Text("heute.session.newCards \(count)")
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
        return VStack(spacing: DL.Space.l) {
            doneMark(worked: worked)
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
            // User agency: an extra round is an ordinary round composed on demand, so it
            // renders in every done state with active cards; hidden only when the box has
            // nothing left to compose at all.
            if model.canPracticeMore {
                Button("heute.done.extraRound") {
                    model.startExtraSession()
                }
                .buttonStyle(DLSoftButtonStyle())
            }
            // Under the button on purpose: what happens next is the smallest thing on
            // the card, and the way on is what the thumb is looking for.
            tomorrowText
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
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

    /// "24 Wiederholungen · 3 Frischlinge · 2 gefestigt" — the day's gain, not just
    /// that it happened. Which counts the day names and in which order is the day's
    /// own report (`TodayReport.tallyParts`); the words are ours.
    private func todayTally(_ report: TodayReport) -> Text {
        report.tallyParts().map(tallyText).joined() ?? Text("heute.session.someCards")
    }

    private func tallyText(_ part: TallyPart) -> Text {
        let count = Int(part.count)
        switch part.kind {
        case .reviews: return Text("heute.session.reviews \(count)")
        case .introduced: return Text("heute.session.newCards \(count)")
        case .consolidated: return Text("heute.done.consolidated \(count.formatted())")
        }
    }

    /// A finished day composes nothing, so words packed on one only arrive through the round
    /// above — said as a fact about that round, in the smallest type on the card, because
    /// the pack was the learner's move and does not need answering. Which of the three
    /// notes a done day leaves is kern's (`tomorrowNote`).
    private var tomorrowText: Text {
        switch tomorrowNote(hasPackedWords: model.hasPackedWords,
                            tomorrowDue: Int32(model.tomorrowDueCount)) {
        case .packed: return Text("heute.done.packed")
        case .fresh: return Text("heute.done.tomorrowFresh")
        case .due: return Text("heute.done.tomorrowDue \(model.tomorrowDueCount)")
        }
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
