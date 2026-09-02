import SwiftUI

/// The one watch practice screen: a role-aware multiple-choice question over
/// the due queue, then review-ahead. Correctness + response time derive the
/// FSRS rating (`WatchGrading`) — no self-grading. Instant feedback on three
/// channels (green right / red wrong plus a red wash, a haptic shaped like the
/// rating, and that rating badged on the tile — `WatchFeedback`), then
/// auto-advance.
/// One progress indicator, in the title: the due batch counts to its end,
/// free practice shows the answer streak (having no total to count toward).
/// - recognize: prompt the target `promptForm` (article-tinted), tap the
///   matching source meaning.
/// - produce:   prompt the source meaning (+ ♀ badge), tap the target word.
///
/// A card that has a picture shows it on the prompt line once answered — never
/// before, since on a recognition question the picture depicts the answer.
struct WatchQuizView: View {
    @Bindable var model: WatchModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if let question = model.currentQuestion {
                quiz(question)
                    .navigationTitle(progressTitle)
            } else {
                completion
            }
        }
        .overlay { wrongFlash }
        .animation(feedbackAnimation, value: model.selectedIndex)
        .animation(feedbackAnimation, value: model.wrongFlash)
    }

    /// Reduce Motion keeps the colors and the badge — only the movement goes,
    /// exactly as every phone screen treats `.cardFlip`.
    private var feedbackAnimation: Animation {
        reduceMotion ? .easeOut(duration: 0.15) : .spring(response: 0.28, dampingFraction: 0.6)
    }

    /// The alarm a tile tint alone was too quiet to raise. Hit testing stays off
    /// so the wash can never swallow the tap that follows it.
    @ViewBuilder
    private var wrongFlash: some View {
        if model.wrongFlash {
            Color.wlWrong
                .opacity(0.55)
                .ignoresSafeArea()
                .allowsHitTesting(false)
                .transition(.opacity)
                .accessibilityHidden(true)
        }
    }

    /// The one indicator: "3/12" in the due batch, "🔥3" in practice — and a
    /// bare flame at streak 0, because a "🔥0" after a miss reads as a scolding.
    private var progressTitle: String {
        switch model.run {
        case .session:
            return "\(min(model.answeredCount + 1, max(model.sessionTotal, 1)))/\(model.sessionTotal)"
        case .practice:
            return model.streak > 0 ? "🔥\(model.streak)" : "🔥"
        }
    }

    // MARK: - Quiz

    private static let columns = [GridItem(.flexible(), spacing: 6),
                                  GridItem(.flexible(), spacing: 6)]

    private func quiz(_ question: WatchPracticeQuestion) -> some View {
        // why: the scroll view is a seatbelt, not a layout — kern's text cap
        // (`WatchSnapshotBuilder.MAX_TEXT_CHARS`) keeps the 2×2 grid on screen,
        // and this only guarantees that a tile can still be READ and reached
        // rather than clipped when Dynamic Type or the smallest face runs out.
        ScrollView {
            VStack(spacing: 8) {
                prompt(question.promptEntry)
                    .frame(maxWidth: .infinity)
                // why: 2×2 grid instead of a tall list — all four options stay
                // in sight at once, so no tap lands on an unseen tile.
                LazyVGrid(columns: Self.columns, spacing: 6) {
                    ForEach(Array(question.options.enumerated()), id: \.offset) { index, option in
                        optionButton(index, option, question)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 2)
        }
    }

    // MARK: - Prompt (role-aware)

    // No emoji here (deliberately) — its room goes to a bigger prompt word.
    private func prompt(_ entry: WatchSnapshot.Entry) -> some View {
        promptText(entry)
            .font(.system(.title2, design: .rounded, weight: .bold))
            .minimumScaleFactor(0.6)
            // why: unbounded, a multi-line prompt takes the height the grid needs
            // and pushes the tiles off the face — the prompt yields first.
            .lineLimit(3)
            .multilineTextAlignment(.center)
    }

    private func promptText(_ entry: WatchSnapshot.Entry) -> Text {
        let line = entry.isRecognize ? targetLine(entry, form: entry.promptForm) : sourceLine(entry)
        guard model.selectedIndex != nil, let picture = revealPicture(entry) else { return line }
        return Text("\(picture) ") + line
    }

    /// The card's picture, once the tile has been tapped and it can give nothing
    /// away. It joins the prompt LINE rather than taking a slot of its own, so
    /// the answered card never reflows under the thumb — and nothing appears at
    /// all before the answer, which is what keeps a produce prompt honest.
    ///
    /// Either key will do here: at reveal both are safe, and kern fills exactly
    /// the one its emoji policy allows.
    private func revealPicture(_ entry: WatchSnapshot.Entry) -> String? {
        entry.revealEmoji ?? entry.emoji
    }

    /// Source meaning; ♀ is a labeled badge, never part of the word.
    private func sourceLine(_ entry: WatchSnapshot.Entry) -> Text {
        let word = Text(entry.sourceText)
        guard entry.femMarker else { return word }
        return word + Text(" ♀").foregroundStyle(Color.wlDie)
    }

    /// Target side, e.g. "die Kellnerin" — articleTint drives the article word
    /// and its color, but only for the canonical text (a rotated synonym may
    /// carry a different gender); genderless targets render plain.
    private func targetLine(_ entry: WatchSnapshot.Entry, form: String) -> Text {
        guard let tint = entry.articleTint, form == entry.targetText else {
            return Text(form)
        }
        return Text("\(tint) ").foregroundStyle(Color.wlTextSecondary)
            + Text(form).foregroundStyle(WL.articleColor(tint))
    }

    // MARK: - Options

    private func optionButton(_ index: Int, _ option: String,
                              _ question: WatchPracticeQuestion) -> some View {
        Button {
            model.choose(index)   // guarded against double-taps in the model
        } label: {
            Text(option)
                .font(.system(.footnote, design: .rounded, weight: .semibold))
                .minimumScaleFactor(0.5)
                // why: a capped phrase wants four half-width lines before it
                // starts shrinking — `minHeight` is a floor, so the tile grows.
                .lineLimit(4)
                .frame(maxWidth: .infinity, minHeight: 44)
                // why: an overlay costs no layout, so nothing shifts under the
                // thumb the instant the badge appears.
                .overlay(alignment: .topTrailing) { ratingBadge(index) }
        }
        .buttonStyle(.borderedProminent)
        .tint(tint(for: index, question))
        .foregroundStyle(labelColor(for: index, question))
    }

    /// The rating the tap earned, on the tile that earned it — an emoji rather
    /// than a word, so it tells an insider how the answer landed without
    /// announcing a grade the learner could start playing to (`WatchFeedback`).
    @ViewBuilder
    private func ratingBadge(_ index: Int) -> some View {
        if index == model.selectedIndex, let rating = model.lastRating {
            Text(WatchFeedback.emoji(forRating: rating))
                .font(.system(size: 13))
                // why: inset rather than offset out — a badge hanging past the
                // tile's own frame is the first thing a clipping cell eats.
                .padding(2)
                .transition(.scale.combined(with: .opacity))
                .accessibilityHidden(true)
        }
    }

    /// Neutral until a choice; then the correct tile greens, a wrong pick reds,
    /// other tiles dim.
    private func tint(for index: Int, _ question: WatchPracticeQuestion) -> Color {
        guard let selected = model.selectedIndex else { return Color.wlTextSecondary.opacity(0.3) }
        if index == question.correctIndex { return .wlSuccess }
        if index == selected { return .wlWrong }
        return Color.wlTextSecondary.opacity(0.15)
    }

    private func labelColor(for index: Int, _ question: WatchPracticeQuestion) -> Color {
        guard let selected = model.selectedIndex else { return .white }
        if index == question.correctIndex || index == selected { return .black }
        return .wlTextSecondary
    }

    // MARK: - Completion

    private var completion: some View {
        WatchCelebrationView(answered: model.answeredCount) { model.endSession() }
    }
}
