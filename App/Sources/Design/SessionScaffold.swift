import SwiftUI

// MARK: - SessionScaffold
//
// Session container chrome: close button + session progress bar on top,
// arbitrary content below. Pure chrome — knows nothing about cards.

/// One answered item in a session/drill, for the segmented progress bar.
enum SessionOutcome: Equatable {
    case right, tough, wrong

    var color: Color {
        switch self {
        case .right: return .dlSuccess
        case .tough: return .dlAmber
        case .wrong: return .dlWrong
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
    var outcomes: [SessionOutcome] = []
    /// Overrides the "position/total" counter (endless drills show "right/done").
    var counter: String?
    /// Opt-in: only runs that read words aloud show the switch for it.
    var showsMuteButton: Bool = false
    var onClose: () -> Void = {}
    @ViewBuilder var content: Content

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return Double(position - 1) / Double(total)
    }

    /// `Text` (not a String) so it localizes via the environment locale.
    private var progressAccessibility: Text {
        if outcomes.isEmpty {
            return Text("session.cardPosition \(position.formatted()) \(total.formatted())")
        }
        let right = outcomes.filter { $0 == .right }.count
        let tough = outcomes.filter { $0 == .tough }.count
        let wrong = outcomes.filter { $0 == .wrong }.count
        return Text("a11y.sessionTally \(right.formatted()) \(tough.formatted()) \(wrong.formatted())")
    }

    var body: some View {
        VStack(spacing: DL.Space.l) {
            topBar
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(DL.Space.l)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    private var topBar: some View {
        HStack(spacing: DL.Space.m) {
            SessionCloseButton(action: onClose)

            GeometryReader { geo in
                if outcomes.isEmpty {
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.dlSeparator)
                        Capsule()
                            .fill(Color.dlAccent)
                            .frame(width: max(geo.size.width * fraction, 10))
                    }
                } else {
                    let slots = max(total, outcomes.count)
                    HStack(spacing: slots > 40 ? 0.5 : 1) {
                        ForEach(Array(outcomes.enumerated()), id: \.offset) { _, outcome in
                            Rectangle().fill(outcome.color)
                        }
                        if outcomes.count < slots {
                            Rectangle()
                                .fill(Color.dlSeparator)
                                .frame(width: geo.size.width * CGFloat(slots - outcomes.count) / CGFloat(slots))
                        }
                    }
                    .clipShape(Capsule())
                }
            }
            .frame(height: 10)
            .animation(.easeOut(duration: 0.3), value: outcomes.count)
            .animation(.easeOut(duration: 0.3), value: fraction)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(progressAccessibility)

            Text(counter ?? "\(position)/\(total)")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .monospacedDigit()
                .accessibilityHidden(true)

            if showsMuteButton { readAloudButton }
        }
    }

    /// Constant chrome, so it costs the card below it not one point of layout —
    /// which is why the switch lives up here and not on the card itself.
    ///
    /// It governs the SPOKEN WORDS only. The feedback chimes are DLSound's and
    /// deliberately stay outside its scope (the ring/silent switch reaches them
    /// already); a global sound switch, if it is ever wanted, is its own thing.
    /// Switching it ON is read as a request to hear something and lifts autoplay
    /// past a silenced phone — otherwise the switch would say on and say nothing.
    private var readAloudButton: some View {
        Button {
            Pronouncer.shared.setReadAloud(on: Pronouncer.shared.muted)
        } label: {
            // why: the plain pair, not the .bubble one — SF Symbols has
            // speaker.wave.2.bubble but no slashed twin for it, and a switch
            // whose two states come from different families reads as two
            // different controls.
            Image(systemName: Pronouncer.shared.muted ? "speaker.slash" : "speaker.wave.2")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Color.dlTextSecondary)
                .frame(width: 44, height: 44)
                .background(Circle().fill(Color.dlSurfaceTint))
        }
        // why: ONE label, the state as the VALUE — a label that flips with the
        // state leaves VoiceOver announcing the action as if it were the
        // condition ("Ton an" on a muted app).
        .accessibilityLabel("a11y.readAloud")
        .accessibilityValue(readAloudValue)
    }

    private var readAloudValue: LocalizedStringKey {
        Pronouncer.shared.muted ? "a11y.off" : "a11y.on"
    }
}

// MARK: - SessionCloseButton

/// The way out of a running session — and, in the same corner, out of the
/// screen that ends it. The thumb that closed one round early finds the next
/// round's exit where it left it, without a trip to the bottom of the screen.
struct SessionCloseButton: View {
    var label: LocalizedStringKey = "a11y.endSession"
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Color.dlTextSecondary)
                .frame(width: 44, height: 44)
                .background(Circle().fill(Color.dlSurfaceTint))
        }
        .accessibilityLabel(label)
    }
}

extension View {
    /// Hangs the close button in the top-left corner of a summary screen, at
    /// the inset `SessionScaffold` uses — so it lands under the same thumb.
    func sessionCloseCorner(label: LocalizedStringKey = "a11y.endSession",
                            action: @escaping () -> Void) -> some View {
        overlay(alignment: .topLeading) {
            SessionCloseButton(label: label, action: action)
                .padding(DL.Space.l)
        }
    }
}

// MARK: - SessionExitButtons
//
// The pair every finished round exits through — the session summary and a
// drill's alike, so the two screens never disagree about which way out is
// the default one.

struct SessionExitButtons: View {
    var onDone: () -> Void
    /// Left out when there is nothing more to practise.
    var onPractice: (() -> Void)?

    var body: some View {
        VStack(spacing: DL.Space.m) {
            // why: the round that was planned is done — stopping takes the
            // full-width primary, and going on is offered at its own smaller
            // size below rather than as a second slab.
            Button(action: onDone) {
                Text("common.done").frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            if let onPractice {
                Button("session.finished.keepPracticing", action: onPractice)
                    .buttonStyle(DLSoftButtonStyle())
            }
        }
        // why: a celebration ending flush against the bottom edge reads as a
        // form to dismiss; the pair sits off it instead.
        .padding(.bottom, DL.Space.xl)
    }
}

// MARK: - Previews

#Preview("Session chrome") {
    SessionScaffold(position: 4, total: 12, onClose: {}) {
        VStack(spacing: DL.Space.xl) {
            VocabCardView(
                emoji: "🥄",
                prompt: .init(text: "kijiko"),
                answer: .init(text: "Löffel", article: "der", plural: "Pl. Löffel"),
                note: nil,
                revealed: true
            )
            RatingButtonsView { _ in }
            Spacer(minLength: 0)
        }
    }
}
