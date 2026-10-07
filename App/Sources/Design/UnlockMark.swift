import SwiftUI
import SprossKern

/// How an overview row that just unlocked is marked, once (kern's `DrillUnlockMark`
/// decides which and times it): the padlock it wore fades where it stood while a wash behind
/// the row fades with it. Short and quiet on purpose — it confirms what the
/// learner earned rather than celebrating it.
enum UnlockMark {
    static let hold: Duration = .milliseconds(Int(DrillUnlockMark.shared.HOLD_MS))
    static let fade: Animation = .easeOut(duration: Double(DrillUnlockMark.shared.FADE_MS) / 1000)
    static let shrink = CGFloat(DrillUnlockMark.shared.PADLOCK_SHRINK)
    static let wash = DrillUnlockMark.shared.WASH_ALPHA

    /// Says the unlocked rows once, a beat after the page settles.
    @MainActor
    static func announce(_ titles: [String], locale: Locale) {
        guard !titles.isEmpty else { return }
        let text = String(format: ChromeStrings.string("a11y.trainer.unlocked %@", locale: locale),
                          titles.formatted(.list(type: .and).locale(locale)))
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(Int(DrillUnlockMark.shared.ANNOUNCE_DELAY_MS)))
            AccessibilityNotification.Announcement(text).post()
        }
    }
}

/// A row's mark that was a padlock the last time the page showed it: the
/// padlock crossfades into `mark`. Where `newlyUnlocked` is false it is `mark` alone.
struct UnlockingMark<Mark: View>: View {
    let newlyUnlocked: Bool
    @ViewBuilder let mark: () -> Mark
    @State private var faded = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if newlyUnlocked && !faded {
                Image(systemName: "lock.fill")
                    .font(.title3)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: UnlockMark.shrink)))
            } else {
                mark().transition(.opacity)
            }
        }
        .task(id: newlyUnlocked) { await fadeOnce(newlyUnlocked, $faded) }
    }
}

/// The padlock in front of a switch's title, where the unlocked switch wears
/// nothing: it fades and gives its room back to the title.
struct FadingPadlock: View {
    let newlyUnlocked: Bool
    @State private var faded = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if newlyUnlocked && !faded {
                Image(systemName: "lock.fill")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: UnlockMark.shrink)))
            }
        }
        .task(id: newlyUnlocked) { await fadeOnce(newlyUnlocked, $faded) }
    }
}

extension View {
    /// The brief wash over a row that just unlocked, gone with its padlock.
    /// `bleed` reaches past a row that draws no surface of its own.
    func unlockWash(_ newlyUnlocked: Bool, bleed: CGFloat = Theme.spacing.xs) -> some View {
        modifier(UnlockWash(newlyUnlocked: newlyUnlocked, bleed: bleed))
    }
}

private struct UnlockWash: ViewModifier {
    let newlyUnlocked: Bool
    let bleed: CGFloat
    @State private var faded = false

    func body(content: Content) -> some View {
        content
            .overlay {
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.accent.opacity(UnlockMark.wash))
                    .padding(-bleed)
                    .opacity(newlyUnlocked && !faded ? 1 : 0)
                    .allowsHitTesting(false)
            }
            .task(id: newlyUnlocked) { await fadeOnce(newlyUnlocked, $faded) }
    }
}

@MainActor
private func fadeOnce(_ newlyUnlocked: Bool, _ faded: Binding<Bool>) async {
    guard newlyUnlocked, !faded.wrappedValue else { return }
    try? await Task.sleep(for: UnlockMark.hold)
    withAnimation(UnlockMark.fade) { faded.wrappedValue = true }
}
