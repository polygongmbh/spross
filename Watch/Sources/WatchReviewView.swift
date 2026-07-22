import SwiftUI

/// Micro-review loop, role-driven (no direction concept):
/// recognize — prompt the rotated target form, flip reveals the source
/// meaning (+ ♀ badge); produce — prompt the source meaning (+ ♀ badge),
/// flip reveals the target family. Both roles are SELF-GRADED via the 2×2
/// rating grid — the watch never types.
struct WatchReviewView: View {
    @Bindable var model: WatchModel

    var body: some View {
        if let entry = model.currentEntry {
            ScrollView {
                VStack(spacing: 6) {
                    front(entry)
                    if model.revealed {
                        back(entry)
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

    private func front(_ entry: WatchSnapshot.Entry) -> some View {
        VStack(spacing: 2) {
            // Emoji is pre-gated by the phone (policy) — no fallback glyph:
            // absence means "hidden on purpose".
            if let emoji = entry.emoji {
                Text(emoji)
                    .font(.system(size: 34))
            }
            Group {
                if entry.isRecognize {
                    targetLine(entry, form: entry.promptForm)
                } else {
                    sourceLine(entry)
                }
            }
            .font(.system(.title3, design: .rounded, weight: .bold))
            .minimumScaleFactor(0.6)
            .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private func back(_ entry: WatchSnapshot.Entry) -> some View {
        VStack(spacing: 2) {
            Group {
                if entry.isRecognize {
                    sourceLine(entry)
                } else {
                    targetLine(entry, form: entry.targetText)
                }
            }
            .font(.system(.body, design: .rounded, weight: .semibold))
            .multilineTextAlignment(.center)

            // Produce reveal shows the rest of the accepted family ("auch: …").
            if !entry.isRecognize, entry.accepted.count > 1 {
                Text("auch: \(entry.accepted.dropFirst().joined(separator: " / "))")
                    .font(.system(.caption2, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    /// Source meaning; ♀ is a labeled badge, never part of the word.
    private func sourceLine(_ entry: WatchSnapshot.Entry) -> Text {
        let word = Text(entry.sourceText)
        guard entry.femMarker else { return word }
        return word + Text(" ♀").foregroundStyle(Color.wlDie)
    }

    /// Target-side form; the article word + tint render only for the
    /// canonical text (a rotated synonym may carry a different gender).
    private func targetLine(_ entry: WatchSnapshot.Entry, form: String) -> Text {
        guard let tint = entry.articleTint, form == entry.targetText else {
            return Text(form)
        }
        return Text("\(tint) ").foregroundStyle(Color.wlTextSecondary)
            + Text(form).foregroundStyle(WL.articleColor(tint))
    }

    // MARK: - Rating (FSRS raw values 1–4)

    private var ratingGrid: some View {
        Grid(horizontalSpacing: 4, verticalSpacing: 4) {
            GridRow {
                ratingButton("Nochmal", 1, .wlAmber)
                ratingButton("Schwer", 2, .wlTeal)
            }
            GridRow {
                ratingButton("Gut", 3, .wlSuccess)
                ratingButton("Einfach", 4, .wlDer)
            }
        }
        .padding(.top, 4)
    }

    private func ratingButton(_ label: String, _ rating: Int, _ color: Color) -> some View {
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
