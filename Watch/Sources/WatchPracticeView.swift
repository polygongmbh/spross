import SwiftUI
import DuoKern

/// Endless multiple-choice practice ("Üben"): prompt word, tap the matching
/// translation in a 2×2 grid. Instant feedback (green right / amber wrong —
/// never red), then auto-advance. Pure local practice — no FSRS, nothing sent
/// to the phone. Dismissed via the sheet's system close control.
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

    private func quiz(_ question: WatchPracticeQuestion) -> some View {
        VStack(spacing: 8) {
            prompt(question.promptCard)
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
        // why: streak sits in its own strip at the very top (below the system
        // ✕/clock) so it never collides with a long prompt word.
        .safeAreaInset(edge: .top) {
            if model.streak > 0 {
                Text("🔥\(model.streak)")
                    .font(.system(.caption, design: .rounded, weight: .bold))
                    .foregroundStyle(Color.wlAccent)
            }
        }
    }

    // No emoji here (deliberately) — its room goes to a bigger prompt word.
    private func prompt(_ card: WatchSnapshot.Card) -> some View {
        Group {
            if model.direction == .targetToDe {
                Text(card.translation)
            } else {
                germanText(card)
            }
        }
        .font(.system(.title2, design: .rounded, weight: .bold))
        .minimumScaleFactor(0.6)
        .multilineTextAlignment(.center)
    }

    /// German side: "der Kühlschrank" with the article-colored noun.
    private func germanText(_ card: WatchSnapshot.Card) -> Text {
        let word = Text(card.german).foregroundStyle(WL.articleColor(card.article))
        guard let article = card.article else { return word }
        return Text("\(article) ").foregroundStyle(Color.wlTextSecondary) + word
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
