import SwiftUI

/// The one watch practice screen: a role-aware multiple-choice question over
/// the due queue, then review-ahead. Correctness + response time derive the
/// FSRS rating (`WatchGrading`) — no self-grading. Instant feedback (green
/// right / amber wrong — never red), then auto-advance.
/// - recognize: prompt the target `promptForm` (article-tinted), tap the
///   matching source meaning.
/// - produce:   prompt the source meaning (+ ♀ badge), tap the target word.
struct WatchQuizView: View {
    @Bindable var model: WatchModel

    var body: some View {
        Group {
            if let question = model.currentQuestion {
                quiz(question)
                    .navigationTitle("\(min(model.answeredCount + 1, max(model.reviewTotal, 1)))/\(model.reviewTotal)")
            } else {
                completion
            }
        }
    }

    // MARK: - Quiz

    private static let columns = [GridItem(.flexible(), spacing: 6),
                                  GridItem(.flexible(), spacing: 6)]

    /// Longest prompt that still leaves room for the inline streak; longer
    /// words simply drop it rather than shrink or collide.
    private static let streakPromptLimit = 12

    private func quiz(_ question: WatchPracticeQuestion) -> some View {
        VStack(spacing: 8) {
            // why: streak floats at the word's trailing edge (costs no vertical
            // space, so the word stays big) and is hidden for long words where
            // it would collide.
            prompt(question.promptEntry)
                .frame(maxWidth: .infinity)
                .overlay(alignment: .trailing) {
                    if model.streak > 0, promptLength(question.promptEntry) <= Self.streakPromptLimit {
                        Text("🔥\(model.streak)")
                            .font(.system(.caption, design: .rounded, weight: .bold))
                            .foregroundStyle(Color.wlAccent)
                    }
                }
            // why: 2×2 grid instead of a tall list — the whole round fits on
            // screen without scrolling.
            LazyVGrid(columns: Self.columns, spacing: 6) {
                ForEach(Array(question.options.enumerated()), id: \.offset) { index, option in
                    optionButton(index, option, question)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 2)
    }

    // MARK: - Prompt (role-aware)

    // No emoji here (deliberately) — its room goes to a bigger prompt word.
    private func prompt(_ entry: WatchSnapshot.Entry) -> some View {
        promptText(entry)
            .font(.system(.title2, design: .rounded, weight: .bold))
            .minimumScaleFactor(0.6)
            .multilineTextAlignment(.center)
    }

    private func promptText(_ entry: WatchSnapshot.Entry) -> Text {
        entry.isRecognize ? targetLine(entry, form: entry.promptForm) : sourceLine(entry)
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

    /// Character count of the prompt as shown, per role.
    private func promptLength(_ entry: WatchSnapshot.Entry) -> Int {
        if entry.isRecognize {
            return [entry.articleTint, entry.promptForm].compactMap { $0 }
                .joined(separator: " ").count
        }
        return entry.sourceText.count + (entry.femMarker ? 2 : 0)
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
                .lineLimit(3)
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .tint(tint(for: index, question))
        .foregroundStyle(labelColor(for: index, question))
    }

    /// Neutral until a choice; then the correct tile greens and a wrong pick
    /// ambers (never red), other tiles dim.
    private func tint(for index: Int, _ question: WatchPracticeQuestion) -> Color {
        guard let selected = model.selectedIndex else { return Color.wlTextSecondary.opacity(0.3) }
        if index == question.correctIndex { return .wlSuccess }
        if index == selected { return .wlAmber }
        return Color.wlTextSecondary.opacity(0.15)
    }

    private func labelColor(for index: Int, _ question: WatchPracticeQuestion) -> Color {
        guard let selected = model.selectedIndex else { return .white }
        if index == question.correctIndex || index == selected { return .black }
        return .wlTextSecondary
    }

    // MARK: - Completion

    private var completion: some View {
        VStack(spacing: 8) {
            Text("Fertig 🎉")
                .font(.system(.title3, design: .rounded, weight: .bold))
            Text("\(model.answeredCount) Karten geübt")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
            Button("Schließen") { model.endSession() }
                .font(.system(.headline, design: .rounded))
                .padding(.top, 4)
        }
    }
}
