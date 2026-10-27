import SwiftUI
import SprossKern

/// The north star screen: one glance = what to do right now.
struct HomeView: View {
    let model: AppModel
    /// Open the box — at one area when a tree names it, else at the top.
    var openBox: (String?) -> Void = { _ in }

    @Environment(\.locale) var locale
    @Environment(\.windowPanes) private var panes

    @State private var listeningPresented = false
    /// The conversation the app does not host (`BriefingSheet`).
    @State private var briefingPresented = false
    /// What the hub has open — here, so the day's card can open a drill too.
    @State private var drillDestination: HubDestination?
    /// What the last scramble run came to, worn on the hub's card.
    @State private var lastDrillRun: DrillRunResult?
    /// Bumped by a hub run's celebrated close; each bump throws a wave.
    @State private var confetti = 0

    var body: some View {
        let offer = model.homeOffer
        let hub = TrainerHubView(model: model, destination: $drillDestination,
                                 lastRun: $lastDrillRun, celebrate: { confetti += 1 })
        let pick = hub.suggestedDrill
        let lead = dayLead(pick)
        Group {
            if let panes, panes.split {
                // What is looked at, and listening, an occasional detour, on one pane;
                // the day's card and the drills on the other, beside the system's controls.
                PaneSplit(panes: panes) {
                    page {
                        header
                        trees()
                        listeningCard
                    }
                } trailing: {
                    page {
                        day(offer: offer, hub: hub, pick: pick, lead: lead)
                        hub
                        briefingCard
                    }
                }
            } else {
                page {
                    header
                    day(offer: offer, hub: hub, pick: pick, lead: lead)
                    listeningCard
                    hub
                    briefingCard
                    trees()
                }
            }
        }
        .background(Theme.colors.background.ignoresSafeArea())
        .hubPresentations(hub)
        // why: absent until the first celebration — ConfettiView throws a wave on appear.
        .overlay {
            if confetti > 0 { ConfettiView(run: confetti).ignoresSafeArea() }
        }
        .fullScreenCover(isPresented: $listeningPresented) {
            ListeningView(model: model)
                .environment(\.locale, model.knownLocale)
        }
        .paneSheet(isPresented: $briefingPresented) {
            BriefingSheet(model: model)
        }
        // why: a challenge link opens the numbers page, which accepts the code itself.
        .onChange(of: model.pendingChallengeCode, initial: true) { _, code in
            guard code != nil else { return }
            drillDestination = TrainerHubView(model: model, destination: $drillDestination,
                                              lastRun: $lastDrillRun)
                .destination(for: .numbers) ?? drillDestination
        }
    }

    private func page<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                content()
            }
            .padding(Theme.spacing.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    /// The day's card.
    @ViewBuilder
    private func day(offer: SessionOffer, hub: TrainerHubView, pick: DrillSuggestion.Pick?,
                     lead: DayLead) -> some View {
        voiceUpgradeBanner
        if let failure = model.loadFailure {
            stateCard(emoji: "🫤",
                      title: "error.title",
                      message: failure.text)
        } else if lead == .drill, let pick {
            leadCard(emoji: pick.drill.emoji,
                     title: Text("home.suggestion.title \(ChromeStrings.string(pick.drill.titleKeyName, locale: locale))"),
                     reason: pick.reasonText) { drillDestination = hub.destination(for: pick.drill) }
        } else if lead == .listening {
            leadCard(emoji: "🎧", title: Text("home.suggestion.listening"),
                     reason: Text("listen.subtitle")) { listeningPresented = true }
        } else if lead == .conversation {
            leadCard(emoji: "💬", title: Text("home.suggestion.conversation"),
                     reason: Text("briefing.row.subtitle")) { briefingPresented = true }
        } else if lead == .round {
            sessionCard(offer)
        } else {
            doneCard
        }
    }

    private func trees() -> some View {
        Trees(model: model, open: { openBox($0) })
    }

    /// Kern's answer to what leads the day (`DayLead`).
    private func dayLead(_ pick: DrillSuggestion.Pick?) -> DayLead {
        #if DEBUG
        // UI-test hook: `-uitest-suggestion 1` leads with the named drill
        // whatever the day has answered, so a screenshot needs no worked day.
        if pick != nil, UserDefaults.standard.bool(forKey: "uitest-suggestion") {
            return .drill
        }
        #endif
        return model.home?.lead(pick: pick, listening: model.listeningOffered, conversation: model.hasBriefing,
                                nowEpochMillis: Date().epochMillis, tzId: currentTzId()) ?? .done
    }

    // MARK: - Conversation

    /// `docs/design.md` § The companion.
    @ViewBuilder
    private var briefingCard: some View {
        if model.hasBriefing {
            Button { briefingPresented = true } label: {
                WayInCard(emoji: "💬", title: Text("briefing.title"), subtitle: Text("briefing.row.subtitle"))
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Listening

    /// `docs/design.md`, `docs/listening.md` § Listening.
    @ViewBuilder
    private var listeningCard: some View {
        // why: a box with words, and something able to say both sides of a turn.
        // Two map lookups and two voice probes — never the walk of the whole
        // join, which is what dealing the playlist is, and that waits for the
        // run to open.
        if model.listeningOffered {
            Button { listeningPresented = true } label: {
                WayInCard(emoji: "🎧", title: Text("listen.title"), subtitle: Text("listen.subtitle"))
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
            .panelSurface()
            .cardShadow()
        }
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
