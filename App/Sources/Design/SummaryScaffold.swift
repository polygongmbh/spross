import SwiftUI

// MARK: - SummaryScaffold
//
// The screen a round stops on — the session summary and a drill's pause
// alike: a hero, one title, the round's tally with any detail lines under it,
// an optional hint, and the exit pair on the bottom edge. Each fills the
// slots; the layout, the type and the ways out are this one's, so the two
// never drift apart (`docs/design.md` § Counts & sessions).

struct SummaryScaffold<Hero: View, Details: View>: View {
    let title: Text
    var tally: Text?
    /// Why stopping is the better call, where the round says so.
    var hint: Text?
    let onDone: () -> Void
    /// Left out where there is no box to brief (`AppModel.hasBriefing`).
    var onTalk: (() -> Void)?
    /// Left out when there is nothing more to practice.
    var onPractice: (() -> Void)?
    /// Handed the height a hero may grow to — a grown tree's ceiling.
    @ViewBuilder let hero: (_ ceiling: CGFloat) -> Hero
    /// Lines under the tally, in its caption voice.
    @ViewBuilder let details: () -> Details

    var body: some View {
        // why: Spacer()-centered content overflows a fixed frame under large
        // Dynamic Type — a fixed hero height leaves no give, so the caption
        // below it got compressed and truncated instead. A GeometryReader'd
        // min-height keeps the centering when everything fits and falls back
        // to scrolling when it does not.
        GeometryReader { geo in
            ScrollView {
                content(ceiling: geo.size.height * 0.45)
                    .padding(Theme.spacing.xl)
                    .frame(minWidth: geo.size.width, minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        // why: the actions stay on the bottom edge however far the results scroll.
        .safeAreaInset(edge: .bottom) {
            SessionExitButtons(onDone: onDone, onTalk: onTalk, onPractice: onPractice)
                .padding(.horizontal, Theme.spacing.xl)
        }
        .background(Theme.colors.background.ignoresSafeArea())
        .sessionCloseCorner(label: "common.done", action: onDone)
    }

    private func content(ceiling: CGFloat) -> some View {
        VStack(spacing: Theme.spacing.xl) {
            Spacer()
            hero(ceiling)
            title
                .font(Theme.typography.hero)
                .foregroundStyle(Theme.colors.textPrimary)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.8)
            VStack(spacing: Theme.spacing.xs) {
                tally?
                    .font(.system(.title3, design: .rounded))
                    .foregroundStyle(Theme.colors.textSecondary)
                details()
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
            .multilineTextAlignment(.center)
            hint?
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

extension SummaryScaffold where Details == EmptyView {
    init(title: Text, tally: Text? = nil, hint: Text? = nil,
         onDone: @escaping () -> Void, onTalk: (() -> Void)? = nil,
         onPractice: (() -> Void)? = nil,
         @ViewBuilder hero: @escaping (_ ceiling: CGFloat) -> Hero) {
        self.init(title: title, tally: tally, hint: hint, onDone: onDone, onTalk: onTalk,
                  onPractice: onPractice, hero: hero, details: { EmptyView() })
    }
}

/// The emoji a summary stands under where no tree takes the hero slot.
struct SummaryGlyph: View {
    let glyph: String

    var body: some View {
        Text(verbatim: glyph)
            .font(.system(size: 88)) // card-parity: the summary's own glyph, not a card prompt
            .accessibilityHidden(true) // why: purely celebratory; the title carries the message
    }
}

// MARK: - SessionExitButtons
//
// The pair every finished round exits through — the session summary and a
// drill's pause alike, so the two screens never disagree about which way out
// is the default one.

struct SessionExitButtons: View {
    var onDone: () -> Void
    /// Left out where there is no box to brief (`AppModel.hasBriefing`).
    var onTalk: (() -> Void)?
    /// Left out when there is nothing more to practice.
    var onPractice: (() -> Void)?

    var body: some View {
        VStack(spacing: Theme.spacing.md) {
            // why: the round that was planned is done — stopping takes the
            // full-width primary on the bottom edge, and the two ways of going
            // on share one row above it rather than stacking as more slabs.
            HStack(spacing: Theme.spacing.md) {
                // why: the words are warm — the one moment a conversation about
                // them costs nothing to offer; it asks rather than instructs.
                if let onTalk { secondary("session.done.talk", icon: "bubble.left.and.bubble.right", onTalk) }
                if let onPractice { secondary("session.done.keepPracticing", icon: "arrow.right", onPractice) }
            }
            // why: a label that wraps grows its own button only — the pair keeps one height.
            .fixedSize(horizontal: false, vertical: true)
            Button(action: onDone) {
                Label("common.done", systemImage: "checkmark").frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
        }
        // why: a celebration ending flush against the bottom edge reads as a
        // form to dismiss; the pair sits off it instead.
        .padding(.bottom, Theme.spacing.xl)
    }

    private func secondary(_ title: LocalizedStringKey, icon: String,
                           _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            // why: a Label pins its icon to the first line — a wrapped title left it riding high.
            HStack(spacing: Theme.spacing.sm) {
                Image(systemName: icon)
                Text(title)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(SoftButtonStyle())
    }
}
