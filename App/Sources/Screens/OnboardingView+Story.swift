import SwiftUI

/// The picker's two story pages: what Spross is for, then what a round asks of you.
///
/// They stand BETWEEN the pick and the box being built, which is the only place they can:
/// activating the profile ends onboarding (`phase → .ready`) and takes the sheet with it,
/// so a page shown afterwards would have nothing to stand on. Reading them also covers the
/// join, so the wait for a first box is spent on something.
///
/// The first page answers what the box is for — breadth over mastery, a companion to a
/// course rather than a replacement, no grammar past the gender a word carries — because
/// those are the three things it is easiest to be disappointed by later. The second is the
/// round itself, in the order the learner will meet it — recognize, grade, write — and
/// nothing about scheduling: the app's one job here is that a blank card is not a test
/// you can fail. The session then coaches the same three at the moment each applies
/// (`SessionCoach`), which is why these stay short enough to be read once and left.
extension OnboardingView {

    // MARK: - What Spross is for

    var whyPage: some View {
        OnboardingStoryPage(emoji: "🌱",
                            title: "onboarding.why.title",
                            actionLabel: "common.next",
                            action: { turn(to: .firstRound) },
                            onBack: { turn(to: .languages) }) {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                principle("onboarding.why.breadth.title", "onboarding.why.breadth.body")
                principle("onboarding.why.companion.title", "onboarding.why.companion.body")
                principle("onboarding.why.grammar.title", "onboarding.why.grammar.body")
            }
        }
    }

    /// One principle: what it is called, and what it means for the learner.
    /// Unnumbered on purpose — the three are facets of one box, not steps up a ladder
    /// (the numbered kind is `LettersOverview+Practice.swift`).
    private func principle(_ title: LocalizedStringKey,
                           _ body: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(title)
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Text(body)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // why: name and meaning are one thought — VoiceOver stops on the principle,
        // not twice inside it.
        .accessibilityElement(children: .combine)
    }

    // MARK: - What a round asks of you

    var firstRoundPage: some View {
        OnboardingStoryPage(emoji: "🌿",
                            title: "onboarding.firstRound.title",
                            actionLabel: "onboarding.start",
                            busy: starting,
                            action: { start() },
                            onBack: starting ? nil : { turn(to: .why) }) {
            VStack(alignment: .leading, spacing: DL.Space.m) {
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
            .font(DL.Fonts.body)
            .foregroundStyle(Color.dlTextPrimary)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - OnboardingHero

/// The face of an onboarding page: a mark large enough to be seen across the room,
/// the page's title under it, and — where the page points at something below it —
/// one secondary line. The line stays secondary by design: it is a finger pointing
/// at the content beneath, not prose of its own.
struct OnboardingHero: View {
    let emoji: String
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey?

    var body: some View {
        VStack(spacing: DL.Space.l) {
            // why: verbatim — a plain Text would take the emoji for a localization key
            // and read the key back on a screen that has no entry for it.
            Text(verbatim: emoji)
                .font(.system(size: 56))
                .accessibilityHidden(true)
            VStack(spacing: DL.Space.xs) {
                Text(title)
                    .font(DL.Fonts.title)
                    .foregroundStyle(Color.dlTextPrimary)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle {
                    Text(subtitle)
                        .font(DL.Fonts.subheadline)
                        .foregroundStyle(Color.dlTextSecondary)
                        .multilineTextAlignment(.center)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - OnboardingStoryPage

/// A page that tells rather than asks: centered hero, a slot of left-aligned prose,
/// and the way on. Both story pages take it, so their rhythm cannot drift apart —
/// the picker keeps its own, a form being a different shape of page.
///
/// The page stands alone on `dlBackground`: no card, no panel, nothing for the eye
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
        VStack(spacing: DL.Space.xl) {
            OnboardingHero(emoji: emoji, title: title)
            content
            buttons
        }
    }

    private var buttons: some View {
        VStack(spacing: DL.Space.m) {
            Button(action: action) {
                Group {
                    if busy {
                        ProgressView().tint(Color.dlOnColor)
                    } else {
                        Text(actionLabel)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            .disabled(busy)
            if let onBack {
                Button("common.back", action: onBack)
                    .buttonStyle(DLSoftButtonStyle())
            }
        }
    }
}
