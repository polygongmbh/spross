import SwiftUI
import DuoKern

/// Endless multiple-choice practice ("Üben"): emoji + prompt word, tap the
/// matching translation. Instant feedback (green right / amber wrong — never
/// red), then auto-advance. Pure local practice — no FSRS, nothing sent to the
/// phone. Mirrors WatchReviewView's look (ScrollView + rounded system fonts,
/// article-colored German side).
struct WatchPracticeView: View {
    @Bindable var model: WatchPracticeModel
    let onClose: () -> Void

    var body: some View {
        Group {
            if !model.hasEnoughVocab {
                notEnough
            } else if model.showingSummary {
                summary
            } else if let question = model.question {
                quiz(question)
            } else {
                notEnough
            }
        }
        .onAppear {
            if model.hasEnoughVocab, model.question == nil, !model.showingSummary {
                model.start()
            }
        }
        .onDisappear { model.end() }
    }

    // MARK: - Quiz

    private static let columns = [GridItem(.flexible(), spacing: 6),
                                  GridItem(.flexible(), spacing: 6)]

    private func quiz(_ question: WatchPracticeQuestion) -> some View {
        VStack(spacing: 6) {
            if model.streak > 0 {
                Text("🔥 \(model.streak)")
                    .font(.system(.footnote, design: .rounded, weight: .bold))
                    .foregroundStyle(Color.wlAccent)
            }
            prompt(question.promptCard)
            // why: 2×2 grid instead of a tall list — the whole round fits on
            // screen without scrolling.
            LazyVGrid(columns: Self.columns, spacing: 6) {
                ForEach(Array(question.options.enumerated()), id: \.offset) { index, option in
                    optionButton(index, option)
                }
            }
            Button("Beenden") { finish() }
                .buttonStyle(.plain)
                .font(.system(.caption2, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 2)
    }

    // No emoji here (deliberately) — it costs vertical room the options need.
    private func prompt(_ card: WatchSnapshot.Card) -> some View {
        Group {
            if model.direction == .targetToDe {
                Text(card.translation)
            } else {
                germanText(card)
            }
        }
        .font(.system(.title3, design: .rounded, weight: .bold))
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

    private func finish() {
        model.end()
        if model.answeredCount > 0 { model.showingSummary = true } else { onClose() }
    }

    // MARK: - Summary / fallback

    private var summary: some View {
        VStack(spacing: 8) {
            Text("Geübt 🎯")
                .font(.system(.title3, design: .rounded, weight: .bold))
            Text("\(model.correctCount)/\(model.answeredCount) richtig")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
            Text("Beste Serie: 🔥 \(model.bestStreak)")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
            Button("Schließen") { onClose() }
                .font(.system(.headline, design: .rounded))
                .padding(.top, 4)
        }
        .multilineTextAlignment(.center)
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
