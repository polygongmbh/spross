import SwiftUI
import DuoKern

/// Micro-review loop: front (emoji + prompt word) → "Aufdecken" →
/// back (answer + plural + note) → 2×2 rating grid → next card.
/// Direction-aware: deToTarget prompts German, targetToDe prompts the
/// target word; the German side always wears its article color.
struct WatchReviewView: View {
    @Bindable var model: WatchModel

    var body: some View {
        if let card = model.currentCard {
            ScrollView {
                VStack(spacing: 6) {
                    front(card)
                    if model.revealed {
                        back(card)
                        ratingGrid
                    } else {
                        Button {
                            model.reveal()
                        } label: {
                            Text("Aufdecken")
                                .font(.system(.headline, design: .rounded, weight: .bold))
                                .foregroundStyle(.black)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.wlAmber)
                        .padding(.top, 8)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .navigationTitle("\(min(model.answeredCount + 1, max(model.reviewTotal, 1)))/\(model.reviewTotal)")
        } else {
            completion
        }
    }

    // MARK: - Card faces

    private func front(_ card: WatchSnapshot.Card) -> some View {
        VStack(spacing: 2) {
            Text(card.emoji ?? "🗂️")
                .font(.system(size: 34))
            promptText(card)
                .font(.system(.title3, design: .rounded, weight: .bold))
                .minimumScaleFactor(0.6)
                .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private func promptText(_ card: WatchSnapshot.Card) -> some View {
        if model.snapshot?.direction == .targetToDe {
            Text(card.translation)
        } else {
            germanText(card)
        }
    }

    /// German side: "der Kühlschrank" with the article-colored word.
    private func germanText(_ card: WatchSnapshot.Card) -> Text {
        let word = Text(card.german)
            .foregroundStyle(WL.articleColor(card.article))
        guard let article = card.article else { return word }
        return Text("\(article) ")
            .foregroundStyle(Color.wlTextSecondary) + word
    }

    @ViewBuilder
    private func back(_ card: WatchSnapshot.Card) -> some View {
        VStack(spacing: 2) {
            Group {
                if model.snapshot?.direction == .targetToDe {
                    germanText(card)
                } else {
                    Text(card.translation)
                }
            }
            .font(.system(.body, design: .rounded, weight: .semibold))
            .multilineTextAlignment(.center)

            if let plural = card.plural {
                Text(plural)
                    .font(.system(.caption2, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary)
            }
            if let note = card.note {
                Text(note)
                    .font(.system(.caption2, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    // MARK: - Rating

    private var ratingGrid: some View {
        Grid(horizontalSpacing: 4, verticalSpacing: 4) {
            GridRow {
                ratingButton("Nochmal", .again, .wlAmber)
                ratingButton("Schwer", .hard, .wlTeal)
            }
            GridRow {
                ratingButton("Gut", .good, .wlSuccess)
                ratingButton("Einfach", .easy, .wlDer)
            }
        }
        .padding(.top, 4)
    }

    private func ratingButton(_ label: String, _ rating: Rating, _ color: Color) -> some View {
        Button {
            model.rate(rating)
        } label: {
            Text(label)
                .font(.system(.footnote, design: .rounded, weight: .semibold))
                .minimumScaleFactor(0.7)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(color.opacity(0.5))
        .foregroundStyle(color)
    }

    // MARK: - Completion

    private var completion: some View {
        VStack(spacing: 8) {
            Text("Fertig 🎉")
                .font(.system(.title3, design: .rounded, weight: .bold))
            Text("\(model.answeredCount) Karten geübt")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
            Button("Schließen") { model.endReview() }
                .font(.system(.headline, design: .rounded))
                .padding(.top, 4)
        }
    }
}
