import SwiftUI
import SprossKern

/// The north star screen: one glance = what to do right now.
struct HomeView: View {
    let model: AppModel
    /// Open the box — at one area when the forest names it, else at the top.
    var openBox: (String?) -> Void = { _ in }

    @Environment(\.locale) var locale

    @State private var listeningPresented = false
    /// The conversation the app does not host (`BriefingSheet`).
    @State private var briefingPresented = false

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
                briefingCard
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
        .sheet(isPresented: $briefingPresented) {
            BriefingSheet(model: model)
        }
    }

    // MARK: - Conversation

    /// `docs/design.md` § The companion.
    @ViewBuilder
    private var briefingCard: some View {
        if model.hasBriefing {
            Button { briefingPresented = true } label: {
                wayInCard(emoji: "💬", title: "briefing.title", subtitle: "briefing.row.subtitle")
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Listening

    /// `docs/design.md`, `docs/surfaces.md` § Listening.
    @ViewBuilder
    private var listeningCard: some View {
        // why: a box with words, and something able to say both sides of a turn.
        // Two map lookups and two voice probes — never the walk of the whole
        // join, which is what dealing the playlist is, and that waits for the
        // run to open.
        if model.box?.cards.isEmpty == false, model.listeningOffered {
            Button { listeningPresented = true } label: {
                wayInCard(emoji: "🎧", title: "listen.title", subtitle: "listen.subtitle")
            }
            .buttonStyle(.plain)
        }
    }

    /// The face the ways in share: the emoji leads, the title names the mode once, and
    /// the subtitle carries what the name cannot. The whole card is the tap target.
    private func wayInCard(emoji: String,
                           title: LocalizedStringKey,
                           subtitle: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.md) {
            Text(verbatim: emoji)
                .font(.title2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                Text(title)
                    .font(Theme.typography.title)
                    .foregroundStyle(Theme.colors.textPrimary)
                Text(subtitle)
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
