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
        SessionScaffold(position: played + 1,
                        total: played + 1,
                        counter: played.formatted(),
                        showsProgress: false,
                        showsMuteButton: false,
                        onClose: { dismiss() }) {
            VStack(spacing: DL.Space.xl) {
                Spacer(minLength: 0)
                cardFace
                Spacer(minLength: 0)
                controls
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

    private var played: Int { Int(driver.state.played) }

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
                          emojiCue: LISTENING_EMOJI_CUE == .upfront ? .upfront : .onReveal,
                          prompt: .init(text: turn.targetForm,
                                        article: article(of: turn),
                                        language: model.targetLanguage),
                          answer: .init(text: turn.sourceForm),
                          note: nil,
                          revealed: driver.revealed)
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

    // MARK: - Controls

    private var controls: some View {
        VStack(spacing: DL.Space.l) {
            HStack(spacing: DL.Space.m) {
                control(symbol: "arrow.counterclockwise", title: Text("listen.repeat")) {
                    driver.again()
                }
                control(symbol: driver.state.paused ? "play.fill" : "pause.fill",
                        title: Text(driver.state.paused ? "listen.resume" : "listen.pause")) {
                    driver.togglePause()
                }
                control(symbol: "forward.end.fill", title: Text("listen.skip")) {
                    driver.skip()
                }
            }
            bedtimeChip
        }
    }

    private func control(symbol: String, title: Text, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: DL.Space.s) {
                Image(systemName: symbol)
                    .font(.title2)
                    .foregroundStyle(Color.dlAccent)
                    .accessibilityHidden(true)
                title
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
            .frame(maxWidth: .infinity, minHeight: DL.Reserve.tile)
            .padding(.vertical, DL.Space.s)
            .padding(.horizontal, DL.Space.xs)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurfaceTint)
            )
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(title)
    }

    /// The bedtime, as one cycling chip: off, then kern's lengths, then off
    /// again. Set, it counts what is LEFT rather than what was picked — the
    /// number a learner reaches for at midnight is how much longer this goes on.
    private var bedtimeChip: some View {
        Button { driver.cycleBedtime() } label: {
            HStack(spacing: DL.Space.s) {
                Image(systemName: "moon.zzz")
                    .accessibilityHidden(true)
                if let left = bedtimeLeft {
                    Text(verbatim: left)
                }
            }
            .font(DL.Fonts.caption)
            .foregroundStyle(driver.bedtime.minutes > 0 ? Color.dlAccent : Color.dlTextSecondary)
            .padding(.horizontal, DL.Space.m)
            .padding(.vertical, DL.Space.s)
            .background(Capsule().fill(Color.dlSurfaceTint))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityValue(bedtimeLeft.map { Text(verbatim: $0) } ?? Text("a11y.off"))
    }

    /// Minutes left, rounded UP so a bedtime never reads zero while words are
    /// still playing; nil while none is set.
    private var bedtimeLeft: String? {
        guard driver.bedtime.minutes > 0, let remaining = driver.bedtime.remainingMs else { return nil }
        let minutes = Int((Double(remaining) / 60_000).rounded(.up))
        return Measurement(value: Double(minutes), unit: UnitDuration.minutes)
            .formatted(.measurement(width: .abbreviated, usage: .asProvided)
                .locale(locale))
    }
}
