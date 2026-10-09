import SwiftUI
import SprossKern

/// The picker's two story pages: where things are, then what a round asks of you.
///
/// They stand BETWEEN the pick and the box being built, which is the only place they can:
/// activating the profile ends onboarding (`phase → .ready`) and takes the sheet with it,
/// so a page shown afterwards would have nothing to stand on. Reading them also covers the
/// join, so the wait for a first box is spent on something.
///
/// The tour names each stop by the name its own screen carries, so the learner
/// recognizes it on arrival. The round page says nothing about scheduling: its one job is
/// that a blank card is not a test you can fail. The session then coaches the same three
/// moments as each applies (`SessionCoach`), which is why these stay short.
extension OnboardingView {

    // MARK: - Where things are

    var tourPage: some View {
        OnboardingStoryPage(emoji: page.emoji,
                            title: "onboarding.tour.title",
                            actionLabel: "common.next",
                            action: { turn(to: page.next) },
                            onBack: { turn(to: page.back(joining: starting)) }) {
            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                ForEach(OnboardingTourStop.allCases, id: \.self) { stop in
                    let (title, body) = Self.tourCopy(stop)
                    tourStop(stop.emoji, title, body)
                }
            }
        }
    }

    /// Each stop's name is the key its own screen shows, so the two can never disagree.
    private static func tourCopy(_ stop: OnboardingTourStop) -> (LocalizedStringKey, LocalizedStringKey) {
        switch stop {
        case .home: ("home.name", "onboarding.tour.home")
        case .box: ("box.name", "onboarding.tour.box")
        case .drills: ("trainer.hub.title", "onboarding.tour.drills")
        case .listening: ("listen.title", "onboarding.tour.listening")
        }
    }

    /// One stop: its glyph, its name, and what the learner does there.
    private func tourStop(_ emoji: String,
                          _ title: LocalizedStringKey,
                          _ body: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.spacing.md) {
            // why: verbatim — a plain Text would take the emoji for a localization key.
            Text(verbatim: emoji)
                .font(Theme.typography.headline)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                Text(title)
                    .font(Theme.typography.headline)
                    .foregroundStyle(Theme.colors.textPrimary)
                Text(body)
                    .font(Theme.typography.body)
                    .foregroundStyle(Theme.colors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // why: name and meaning are one thought — VoiceOver stops on the stop, not twice inside it.
        .accessibilityElement(children: .combine)
    }

    // MARK: - What a round asks of you

    var firstRoundPage: some View {
        OnboardingStoryPage(emoji: page.emoji,
                            title: "onboarding.firstRound.title",
                            actionLabel: "onboarding.start",
                            busy: starting,
                            action: { start() },
                            onBack: page.back(joining: starting).map { back in { turn(to: back) } }) {
            VStack(alignment: .leading, spacing: Theme.spacing.md) {
                moment("onboarding.firstRound.recognize")
                moment("onboarding.firstRound.grade")
                moment("onboarding.firstRound.write")
            }
        }
    }

    /// One moment of a round, in the learner's own voice — full-strength text, since
    /// this is the page's substance and not a footnote to the title above it.
    private func moment(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(Theme.typography.body)
            .foregroundStyle(Theme.colors.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - OnboardingHero

/// The face of an onboarding page: a mark large enough to be seen across the room,
/// and the page's title under it.
struct OnboardingHero<Trailing: View>: View {
    let emoji: String
    let title: LocalizedStringKey
    /// Stands beside the emoji in the space to its right, wrapping inside it,
    /// so a long label never covers the centered emoji.
    @ViewBuilder var trailing: Trailing

    var body: some View {
        VStack(spacing: Theme.spacing.lg) {
            HStack(spacing: 0) {
                Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
                // why: verbatim — a plain Text would take the emoji for a localization key
                // and read the key back on a screen that has no entry for it.
                Text(verbatim: emoji)
                    .font(.system(size: 56)) // card-parity: the story page's own glyph, not a card prompt
                    .accessibilityHidden(true)
                trailing.frame(maxWidth: .infinity, alignment: .trailing)
            }
            Text(title)
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity)
    }
}

extension OnboardingHero where Trailing == EmptyView {
    init(emoji: String, title: LocalizedStringKey) {
        self.init(emoji: emoji, title: title) { EmptyView() }
    }
}

// MARK: - OnboardingStoryPage

/// A page that tells rather than asks: centered hero, a slot of left-aligned prose,
/// and the way on. Both story pages take it, so their rhythm cannot drift apart —
/// the picker keeps its own, a form being a different shape of page.
///
/// The page stands alone on `Theme.colors.background`: no card, no panel, nothing for the eye
/// to weigh before it reads.
struct OnboardingStoryPage<Content: View>: View {
    let emoji: String
    let title: LocalizedStringKey
    let actionLabel: LocalizedStringKey
    /// The commit is running: the primary spins, and nothing on the page can be tapped.
    var busy: Bool = false
    var action: () -> Void
    /// Left out where there is nothing behind the page — or nothing left to undo.
    /// The sheet cannot be swiped away (`RootView` disables that), so this is the
    /// only way back to a mis-picked language.
    var onBack: (() -> Void)?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: Theme.spacing.xl) {
            OnboardingHero(emoji: emoji, title: title)
            content
            buttons
        }
    }

    private var buttons: some View {
        VStack(spacing: Theme.spacing.md) {
            Button(action: action) {
                Group {
                    if busy {
                        ProgressView().tint(Theme.colors.onColor)
                    } else {
                        Text(actionLabel)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(busy)
            if let onBack {
                Button("common.back", action: onBack)
                    .buttonStyle(SoftButtonStyle())
            }
        }
    }
}
