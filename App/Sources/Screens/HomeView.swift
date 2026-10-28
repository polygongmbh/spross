import SwiftUI
import SprossKern

/// Home: what to do right now, at a glance.
struct HomeView: View {
    let model: AppModel
    /// Opens the Box at an area, or at the top for nil.
    var openBox: (String?) -> Void = { _ in }

    @Environment(\.locale) var locale
    @Environment(\.windowPanes) private var panes

    @State private var listeningPresented = false
    /// The conversation the app does not host (`BriefingSheet`).
    @State private var briefingPresented = false
    /// What the hub has open; held here so the day's card can open a drill too.
    @State private var drillDestination: HubDestination?
    /// Bumped by a hub run's celebrated close; each bump throws a wave.
    @State private var confetti = 0

    var body: some View {
        let offer = model.homeOffer
        let hub = TrainerHubView(model: model, destination: $drillDestination,
                                 celebrate: { confetti += 1 })
        let pick = hub.suggestedDrill
        let lead = dayLead(pick)
        let split = panes?.split == true
        let columns = HomeColumns(split: split)
        let column = { (sections: [HomeSection]) in
            ForEach(sections, id: \.self) { section($0, offer: offer, hub: hub, pick: pick, lead: lead) }
        }
        // why: the primary page holds the one column too, so the hub keeps its place
        // and its sheet and cover survive a fold or a rotation.
        HStack(alignment: .top, spacing: panes?.gutter ?? 0) {
            if let panes, split, Self.primaryEdge == .trailing {
                page(panes.leading) { column(columns.secondary) }
            }
            page(split ? (Self.primaryEdge == .trailing ? panes?.trailing : panes?.leading) : nil) {
                column(columns.primary)
            }
            if let panes, split, Self.primaryEdge == .leading {
                page(panes.trailing) { column(columns.secondary) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .background(Theme.colors.background.ignoresSafeArea())
        // why: ConfettiView throws a wave on appear, so it stays absent until the first celebration.
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
            drillDestination = TrainerHubView(model: model, destination: $drillDestination)
                .destination(for: .numbers) ?? drillDestination
        }
    }

    /// Home's primary pane stands beside the system's vertical bar.
    private static let primaryEdge: HorizontalEdge = .trailing

    /// One of kern's sections (`HomeSection`).
    @ViewBuilder
    private func section(_ section: HomeSection, offer: SessionOffer, hub: TrainerHubView,
                         pick: DrillSuggestion.Pick?, lead: DayLead) -> some View {
        switch section {
        case .header: header
        case .voiceUpgrade: voiceUpgradeBanner
        case .day: day(offer: offer, hub: hub, pick: pick, lead: lead)
        case .listening: listeningCard
        case .drills: hub
        case .conversation: briefingCard
        case .trees: trees()
        default: EmptyView()
        }
    }

    /// The day's card.
    @ViewBuilder
    private func day(offer: SessionOffer, hub: TrainerHubView, pick: DrillSuggestion.Pick?,
                     lead: DayLead) -> some View {
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
                     reason: Text("home.suggestion.reason.listening")) { listeningPresented = true }
        } else if lead == .conversation {
            leadCard(emoji: "💬", title: Text("home.suggestion.conversation"),
                     reason: Text("home.suggestion.reason.conversation")) { briefingPresented = true }
        } else if lead == .round {
            sessionCard(offer)
        } else {
            doneCard
        }
    }

    /// One scrolling page: the pane's readable column, centered in the pane, or the whole width.
    private func page<Content: View>(_ pane: WindowPanes.Pane?,
                                     @ViewBuilder _ content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                content()
            }
            .padding(Theme.spacing.xl)
            .frame(maxWidth: pane?.columnWidth)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .frame(width: pane?.width)
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
        // why: the check is two lookups and two voice probes; dealing the playlist waits for the run to open.
        if model.listeningOffered {
            Button { listeningPresented = true } label: {
                WayInCard(emoji: "🎧", title: Text("listen.title"), subtitle: Text("listen.subtitle"))
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Voice upgrade

    /// Above the day's card while the compact system voice reads the words and a better one is a free download.
    /// The path is spelled out because no public URL opens the Voices pane.
    /// Dismissing is permanent; the settings row keeps the hint.
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
    /// Error chrome as `Text`, resolved in the environment locale; the system `reason` stays as the OS wrote it.
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
