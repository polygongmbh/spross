import SprossKern
import SwiftUI

// MARK: - SessionScaffold
//
// Session container chrome: close button + session progress bar on top,
// arbitrary content below — or, in a wide window, the same chrome as items in the
// system's toolbar (`SystemBar`), the content taking the full width. Pure chrome — knows nothing about cards.

/// One answered item's segment hue — which answer reads as which outcome is kern's (`AnswerOutcome`).
extension AnswerOutcome {
    var color: Color {
        switch self {
        case .right: return Theme.colors.success
        case .almost: return Theme.colors.amber
        case .wrong: return Theme.colors.wrong
        }
    }
}

struct SessionScaffold<Content: View>: View {
    /// 1-based position of the current card in the composed session.
    let position: Int
    let total: Int
    /// Answered items in order; when non-empty the bar renders one colored
    /// segment per answer (green right / amber tough / brick wrong) with
    /// the unanswered remainder neutral.
    var outcomes: [AnswerOutcome] = []
    /// Opt-in: only runs that read words aloud show the switch for it.
    var showsMuteButton: Bool = false
    /// A run whose sound no mute reaches (the letter drill): the low-volume
    /// hint stands whatever the switch says.
    var speaksPastMute: Bool = false
    /// The run's own line under the bar — a drill's score line — kept still
    /// while the content under it scrolls; nil where the bar says it all.
    var status: AnyView?
    var onClose: () -> Void = {}
    @ViewBuilder var content: Content

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return Double(position - 1) / Double(total)
    }

    var body: some View {
        NavigationStack {
            PanesReader { panes in
                let inBar = SystemBar.hostsChrome(wide: panes?.split == true)
                VStack(spacing: 0) {
                    if !inBar { topBar }
                    volumeHint
                    status?.padding(.top, Theme.spacing.md)
                    content
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.top, Theme.spacing.lg)
                }
                .typeScaled(session: true)
                .padding(Theme.spacing.lg)
                .sessionSystemBar(enabled: inBar, readsAloud: showsMuteButton, onClose: onClose) {
                    VerticalBarReader { vertical in
                        progressBar(vertical: vertical)
                            .frame(width: vertical ? 10 : 160, height: vertical ? 240 : 10)
                    }
                } readAloud: {
                    readAloudSwitch(inBar: true)
                }
            }
            .background(Theme.colors.background.ignoresSafeArea())
        }
    }

    private var volumeHint: some View {
        VolumeHint(active: speaksPastMute || (showsMuteButton && !Pronouncer.shared.muted))
    }

    private var topBar: some View {
        HStack(spacing: Theme.spacing.md) {
            SessionCloseButton(action: onClose)
            progressBar(vertical: false)
                .frame(height: 10)
            if showsMuteButton { readAloudButton }
        }
    }

    /// Fills along its length — across on a phone, downward in an upright system bar.
    private func progressBar(vertical: Bool) -> some View {
        GeometryReader { geo in
            let length = vertical ? geo.size.height : geo.size.width
            if outcomes.isEmpty {
                ZStack(alignment: vertical ? .top : .leading) {
                    Capsule().fill(Theme.colors.separator)
                    Capsule()
                        .fill(Theme.colors.accent)
                        .frame(width: vertical ? nil : max(length * fraction, 10),
                               height: vertical ? max(length * fraction, 10) : nil)
                }
            } else {
                // The window and the partings are kern's (`SegmentsBar`).
                let bar = SegmentsBar(answered: Int32(outcomes.count),
                                      remaining: Int32(max(total - outcomes.count, 0)))
                let visible = outcomes.suffix(Int(bar.shown))
                let remaining = Int(bar.remaining)
                let slots = Int(bar.slots)
                let spacing = CGFloat(bar.gap)
                // The partings come off the row before any slot is measured,
                // so the remainder takes its share of what is LEFT for
                // segments — from the full width it charged every gap to the
                // answered side, drawing the fill short of the bar.
                let forSegments = max(length - CGFloat(visible.count) * spacing, 0)
                let rest = forSegments * CGFloat(remaining) / CGFloat(slots)
                let stack = vertical ? AnyLayout(VStackLayout(spacing: spacing))
                    : AnyLayout(HStackLayout(spacing: spacing))
                stack {
                    ForEach(Array(visible.enumerated()), id: \.offset) { _, outcome in
                        Rectangle().fill(outcome.color)
                    }
                    if remaining > 0 {
                        Rectangle()
                            .fill(Theme.colors.separator)
                            .frame(width: vertical ? nil : rest, height: vertical ? rest : nil)
                    }
                }
                .clipShape(Capsule())
            }
        }
        .animation(.easeOut(duration: 0.3), value: outcomes.count)
        .animation(.easeOut(duration: 0.3), value: fraction)
        .accessibilityHidden(true)
    }

    /// Constant chrome, so it costs the card below it not one point of layout —
    /// which is why the switch lives up here and not on the card itself.
    ///
    /// It governs the SPOKEN WORDS only. The feedback chimes are Sound's and
    /// deliberately stay outside its scope (the ring/silent switch reaches them
    /// already); a global sound switch, if it is ever wanted, is its own thing.
    /// Switching it ON is read as a request to hear something and lifts autoplay
    /// past a silenced phone — otherwise the switch would say on and say nothing.
    private var readAloudButton: some View {
        readAloudSwitch(inBar: false)
    }

    /// In the system's bar the button takes the bar's own look rather than its circle.
    private func readAloudSwitch(inBar: Bool) -> some View {
        Button {
            Pronouncer.shared.setReadAloud(on: Pronouncer.shared.muted)
        } label: {
            // why: the plain pair, not the .bubble one — SF Symbols has
            // speaker.wave.2.bubble but no slashed twin for it, and a switch
            // whose two states come from different families reads as two
            // different controls.
            let icon = Image(systemName: Pronouncer.shared.muted ? "speaker.slash" : "speaker.wave.2")
            if inBar {
                icon
            } else {
                icon
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.colors.textSecondary)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Theme.colors.surfaceTint))
            }
        }
        // why: ONE label, the state as the VALUE — a label that flips with the
        // state leaves VoiceOver announcing the action as if it were the
        // condition ("Ton an" on a muted app).
        .accessibilityLabel("a11y.action.readAloud")
        .accessibilityValue(readAloudValue)
    }

    private var readAloudValue: LocalizedStringKey {
        Pronouncer.shared.muted ? "a11y.state.off" : "a11y.state.on"
    }
}

// MARK: - Endless chrome

extension SessionScaffold {
    /// The chrome for a run that may or may not still be counting toward a
    /// composed plan. Endless — every drill, and a review run once "Weiter
    /// üben" has switched it — shows exactly the one card in front of the
    /// learner as the bar's open segment: kern may pull a whole batch on an
    /// endless refill, but the learner never sees that batch as a plan with
    /// an end (`DrillRunning.runScreen`, `SessionView`).
    static func running(endless: Bool,
                        position: Int = 1,
                        total: Int = 1,
                        outcomes: [AnswerOutcome],
                        showsMuteButton: Bool = false,
                        speaksPastMute: Bool = false,
                        status: AnyView? = nil,
                        onClose: @escaping () -> Void,
                        @ViewBuilder content: () -> Content) -> SessionScaffold {
        SessionScaffold(position: endless ? outcomes.count + 1 : position,
                        total: endless ? outcomes.count + 1 : total,
                        outcomes: outcomes,
                        showsMuteButton: showsMuteButton,
                        speaksPastMute: speaksPastMute,
                        status: status,
                        onClose: onClose,
                        content: content)
    }
}

// MARK: - SessionCloseButton

/// The way out of a running session — and, in the same corner, out of the
/// screen that ends it. The thumb that closed one round early finds the next
/// round's exit where it left it, without a trip to the bottom of the screen.
struct SessionCloseButton: View {
    var label: LocalizedStringKey = "a11y.action.endSession"
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.colors.textSecondary)
                .frame(width: 44, height: 44)
                .background(Circle().fill(Theme.colors.surfaceTint))
        }
        .accessibilityLabel(label)
    }
}

extension View {
    /// Hangs the close button in the top-left corner of a summary screen, at
    /// the inset `SessionScaffold` uses — so it lands under the same thumb.
    func sessionCloseCorner(label: LocalizedStringKey = "a11y.action.endSession",
                            action: @escaping () -> Void) -> some View {
        overlay(alignment: .topLeading) {
            SessionCloseButton(label: label, action: action)
                .padding(Theme.spacing.lg)
        }
    }
}

// MARK: - Previews

#Preview("Session chrome") {
    SessionScaffold(position: 4, total: 12, onClose: {}) {
        VStack(spacing: Theme.spacing.xl) {
            QuestionCardView(question: Question(
                key: "kijiko", ask: nil,
                prompt: .init(text: "kijiko", lang: "sw", form: .word, article: nil, gender: nil, plural: nil,
                              marker: nil, fixedLeading: 0, saying: nil),
                answer: .init(text: "Löffel", lang: "de", form: .word, article: "der", gender: .masculine,
                              plural: PluralForm.Form(text: "Löffel"),
                              marker: nil, fixedLeading: 0, saying: nil),
                emoji: "🥄", emojiCue: .upfront, emojiIsQuestion: false, hint: nil,
                opens: true, growsNote: false, closing: .init(alternates: [], note: nil), otherWord: nil
            ), surface: .review)
            RatingButtonsView { _ in }
            Spacer(minLength: 0)
        }
    }
}
