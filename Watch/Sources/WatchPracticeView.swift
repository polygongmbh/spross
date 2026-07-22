import SwiftUI

/// Endless multiple-choice practice ("Üben"): target-side prompt word, tap
/// the matching source meaning in a 2×2 grid. Instant feedback (green right /
/// amber wrong — never red), then auto-advance. Pure local practice — no
/// FSRS, nothing sent to the phone. Dismissed via the sheet's system close
/// control.
struct WatchPracticeView: View {
    @Bindable var model: WatchPracticeModel
    let onClose: () -> Void

    var body: some View {
        Group {
            if let question = model.question {
                quiz(question)
            } else {
                notEnough
            }
        }
        .onAppear {
            if model.hasEnoughVocab, model.question == nil { model.start() }
        }
        .onDisappear { model.end() }
    }

    // MARK: - Quiz

    private static let columns = [GridItem(.flexible(), spacing: 6),
                                  GridItem(.flexible(), spacing: 6)]

    /// Longest prompt (incl. article) that still leaves room for the inline
    /// streak; longer words simply drop it rather than shrink or collide.
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
                    optionButton(index, option)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 2)
    }

    /// Character count of the prompt as shown (article tint word + target text).
    private func promptLength(_ entry: WatchSnapshot.Entry) -> Int {
        [entry.articleTint, entry.targetText].compactMap { $0 }
            .joined(separator: " ").count
    }

    // No emoji here (deliberately) — its room goes to a bigger prompt word.
    private func prompt(_ entry: WatchSnapshot.Entry) -> some View {
        targetText(entry)
            .font(.system(.title2, design: .rounded, weight: .bold))
            .minimumScaleFactor(0.6)
            .multilineTextAlignment(.center)
    }

    /// Target side, e.g. "die Kellnerin" — articleTint string drives both the
    /// article word and its color; genderless targets render plain.
    private func targetText(_ entry: WatchSnapshot.Entry) -> Text {
        guard let tint = entry.articleTint else { return Text(entry.targetText) }
        return Text("\(tint) ").foregroundStyle(Color.wlTextSecondary)
            + Text(entry.targetText).foregroundStyle(WL.articleColor(tint))
    }

    private func optionButton(_ index: Int, _ option: String) -> some View {
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
        .tint(tint(for: index))
        .foregroundStyle(labelColor(for: index))
    }

    /// Neutral until a choice; then the correct tile greens and a wrong pick
    /// ambers (never red), other tiles dim.
    private func tint(for index: Int) -> Color {
        guard let selected = model.selectedIndex else { return Color.wlTextSecondary.opacity(0.3) }
        if index == model.question?.correctIndex { return .wlSuccess }
        if index == selected { return .wlAmber }
        return Color.wlTextSecondary.opacity(0.15)
    }

    private func labelColor(for index: Int) -> Color {
        guard let selected = model.selectedIndex else { return .white }
        if index == model.question?.correctIndex || index == selected { return .black }
        return .wlTextSecondary
    }

    private var notEnough: some View {
        VStack(spacing: 8) {
            Text("📚")
                .font(.system(size: 36))
            Text("Noch zu wenig Vokabeln – lern zuerst auf dem iPhone.")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
                .multilineTextAlignment(.center)
            Button("Schließen") { onClose() }
                .font(.system(.headline, design: .rounded))
                .padding(.top, 4)
        }
    }
}
