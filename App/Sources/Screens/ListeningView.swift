import SwiftUI
import SprossKern

/// Listening: a playlist over the learner's own words, made entirely of sound.
/// Each turn says the target word, then its meaning, then the target again, and
/// it laps for as long as it is left playing — with the screen locked, in a
/// pocket, over a sink of dishes.
///
/// It books no review, writes no schedule and moves no streak, so it can be
/// closed at any moment and costs the box nothing (`docs/surfaces.md`
/// § Listening). The whole machine is kern's (`ListeningRun`); the driver that
/// turns its effects into sound is `ListeningDriver`, and this is only the face.
///
/// No mute button: entering a surface whose only content is a sound is itself
/// the request to hear one, so neither mute reaches the run.
struct ListeningView: View {
    let model: AppModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale
    @State private var driver: ListeningDriver

    init(model: AppModel) {
        self.model = model
        _driver = State(initialValue: ListeningDriver(model: model))
    }

    private var turn: ListeningTurn? { driver.state.turn }

    var body: some View {
        ListeningScaffold(onClose: { dismiss() }, trailing: { timerCapsule }) {
            VStack(spacing: DL.Space.xl) {
                Spacer(minLength: 0)
                cardFace
                Spacer(minLength: 0)
                transport
            }
        }
        .onAppear { driver.open() }
        // why: leaving must silence and hand the audio back — a cover dismissed
        // by the ✕, by the bedtime or by a swipe all land here.
        .onDisappear { driver.close() }
        .onChange(of: driver.closed) { _, closed in
            if closed { dismiss() }
        }
    }

    // MARK: - The card

    /// The review card's own face: the target stands from the first frame — a
    /// run with nothing to answer has nothing to give away — and the meaning
    /// arrives with its reading, picture and all.
    @ViewBuilder
    private var cardFace: some View {
        if let turn {
            VocabCardView(emoji: card(turn)?.emoji,
                          // why: kern names the rule (LISTENING_EMOJI_CUE) — listening
                          // owes no answer, so nothing is withheld. Picked per phone
                          // it was picked differently, and the picture vanished and
                          // returned on every word.
                          emojiCue: LISTENING_EMOJI_CUE,
                          prompt: .init(text: turn.targetForm,
                                        article: article(of: turn),
                                        language: model.targetLanguage),
                          answer: .init(text: turn.sourceForm),
                          note: nil,
                          revealed: driver.revealed,
                          // why: nothing is typed, pressed or scrolled here, so the card
                          // has the screen to itself — picture above, words the full width.
                          arrangement: .above)
        }
    }

    private func card(_ turn: ListeningTurn) -> Card? {
        model.box?.cards[turn.cardId]
    }

    /// The article the card paints, out of the one kern already decided is
    /// spoken — so what is heard and what is read can never disagree.
    private func article(of turn: ListeningTurn) -> DLArticle? {
        guard let article = turn.spokenArticle else { return nil }
        return DLArticle(article, gender: DLGender(articleGender(article: article)))
    }

    // MARK: - Transport

    /// Three glyphs and no captions: a run whose only content is sound has one
    /// row of controls, and a label under each of them names what the shape
    /// already says while stealing the height the card wants. The names stay —
    /// as what VoiceOver reads, which is where they are needed.
    private var transport: some View {
        HStack(spacing: DL.Space.xl) {
            transportButton(symbol: "arrow.counterclockwise",
                            size: 56,
                            label: Text("listen.repeat")) { driver.again() }
            // why: the LABEL flips with the state, unlike the read-aloud switch
            // one screen over. These are buttons that name an ACTION — what the
            // next tap does — not switches naming a condition, so "Pause" on a
            // playing run is the correct reading, and moving it to a value
            // would leave the button unnamed.
            transportButton(symbol: driver.state.paused ? "play.fill" : "pause.fill",
                            size: 72,
                            emphasized: true,
                            label: Text(driver.state.paused ? "listen.resume" : "listen.pause")) {
                driver.togglePause()
            }
            transportButton(symbol: "forward.end.fill",
                            size: 56,
                            label: Text("listen.skip")) { driver.skip() }
        }
    }

    private func transportButton(symbol: String,
                                 size: CGFloat,
                                 emphasized: Bool = false,
                                 label: Text,
                                 action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: size * 0.38, weight: .semibold))
                .foregroundStyle(emphasized ? Color.dlOnColor : Color.dlAccent)
                .frame(width: size, height: size)
                .background(Circle().fill(emphasized ? Color.dlAccent : Color.dlSurfaceTint))
                .accessibilityHidden(true)
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
    }

    // MARK: - The sleep timer

    /// The bedtime, as one labeled capsule in the corner: off, then kern's
    /// lengths, then off again. Set, it counts what is LEFT rather than what was
    /// picked — the number a learner reaches for at midnight is how much longer
    /// this goes on — and it counts in MINUTES: a ticking clock is a clock you
    /// watch, which is the opposite of what a sleep timer is for.
    private var timerCapsule: some View {
        Button { driver.bedtime.step(1) } label: {
            HStack(spacing: DL.Space.xs) {
                Image(systemName: "moon.zzz")
                    .accessibilityHidden(true)
                timerText
            }
            .font(DL.Fonts.caption)
            .foregroundStyle(driver.bedtime.minutes > 0 ? Color.dlAccent : Color.dlTextSecondary)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.s)
            .background(Capsule().fill(Color.dlSurfaceTint))
        }
        .buttonStyle(TrainerChipButtonStyle())
        // why: the LENGTH is a state of the timer, so the label stays put and
        // the reading moves — the opposite of the transport above, whose
        // buttons name what the tap will do.
        .accessibilityLabel(Text("listen.timer"))
        .accessibilityValue(timerValue)
        // The picker sighted users get by cycling: up and down walk kern's
        // lengths, and each one is read out as it is reached.
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: driver.bedtime.step(1)
            case .decrement: driver.bedtime.step(-1)
            @unknown default: break
            }
        }
    }

    /// The capsule's own word: its name while off, what is left of it once set.
    private var timerText: Text {
        guard let left = driver.bedtime.minutesLeft else { return Text("listen.timer") }
        return Text("listen.minutesLeft \(left)")
    }

    private var timerValue: Text {
        guard let left = driver.bedtime.minutesLeft else { return Text("a11y.off") }
        return Text("listen.minutesLeft \(left)")
    }
}

// MARK: - Chrome

/// What listening puts around its card — and it is not `SessionScaffold`.
///
/// That frame is built around a run that ASKS something: a bar filling toward a
/// total and a count of what has been answered. This run asks nothing, so both
/// would be furniture, and the counter would be a score on the one surface that
/// grades nothing. What it needs instead is a TITLE — it is the only run whose
/// card carries no question, so nothing else on screen says what the learner is
/// in — and the same ✕ in the same corner as every other run.
private struct ListeningScaffold<Trailing: View, Content: View>: View {
    let onClose: () -> Void
    @ViewBuilder var trailing: Trailing
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: DL.Space.l) {
            HStack(spacing: DL.Space.m) {
                SessionCloseButton(action: onClose)
                Spacer(minLength: 0)
                Text("listen.title")
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Spacer(minLength: 0)
                trailing
            }
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(DL.Space.l)
        .background(Color.dlBackground.ignoresSafeArea())
    }
}
