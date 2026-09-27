import SwiftUI
import SprossKern

/// How an overview row that just unlocked is marked, once (kern's `DrillUnlockMark`
/// decides which): the padlock it wore fades where it stood while a wash behind
/// the row fades with it. Short and quiet on purpose — it confirms what the
/// learner earned rather than celebrating it.
enum UnlockMark {
    /// How long the padlock stands before it goes — past a sheet sliding in,
    /// so the eye finds it first.
    static let hold: Duration = .milliseconds(600)
    static let fade: Animation = .easeOut(duration: 0.9)

    /// Says the unlocked rows once, a beat after the page settles, so the
    /// screen change VoiceOver is announcing is not talked over.
    @MainActor
    static func announce(_ titles: [String], locale: Locale) {
        guard !titles.isEmpty else { return }
        let text = String(format: ChromeStrings.string("a11y.trainer.unlocked %@", locale: locale),
                          titles.formatted(.list(type: .and).locale(locale)))
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(700))
            AccessibilityNotification.Announcement(text).post()
        }
    }
}

/// A row's mark that was a padlock the last time the page showed it: the
/// padlock crossfades into `mark`. Where `fresh` is false it is `mark` alone.
struct UnlockingMark<Mark: View>: View {
    let fresh: Bool
    @ViewBuilder let mark: () -> Mark
    @State private var faded = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if fresh && !faded {
                Image(systemName: "lock.fill")
                    .font(.title3)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.6)))
            } else {
                mark().transition(.opacity)
            }
        }
        .task(id: fresh) { await fadeOnce(fresh, $faded) }
    }
}

/// The padlock in front of a switch's title, where the unlocked switch wears
/// nothing: it fades and gives its room back to the title.
struct FadingPadlock: View {
    let fresh: Bool
    @State private var faded = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if fresh && !faded {
                Image(systemName: "lock.fill")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.6)))
            }
        }
        .task(id: fresh) { await fadeOnce(fresh, $faded) }
    }
}

extension View {
    /// The brief wash over a row that just unlocked, gone with its padlock.
    /// `bleed` reaches past a row that draws no surface of its own.
    func unlockWash(_ fresh: Bool, bleed: CGFloat = Theme.spacing.xs) -> some View {
        modifier(UnlockWash(fresh: fresh, bleed: bleed))
    }
}

private struct UnlockWash: ViewModifier {
    let fresh: Bool
    let bleed: CGFloat
    @State private var faded = false

    func body(content: Content) -> some View {
        content
            .overlay {
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.accent.opacity(0.12))
                    .padding(-bleed)
                    .opacity(fresh && !faded ? 1 : 0)
                    .allowsHitTesting(false)
            }
            .task(id: fresh) { await fadeOnce(fresh, $faded) }
    }
}

@MainActor
private func fadeOnce(_ fresh: Bool, _ faded: Binding<Bool>) async {
    guard fresh, !faded.wrappedValue else { return }
    try? await Task.sleep(for: UnlockMark.hold)
    withAnimation(UnlockMark.fade) { faded.wrappedValue = true }
}
