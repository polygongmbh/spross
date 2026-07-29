import SwiftUI
import SprossKern

/// The write-it-out step. A word you MISSED gets typed once with the answer in
/// view before the session moves on — the bit of encoding a reveal followed by
/// a single tap never gives you.
///
/// It is an encoding step, never a grade: the rating was already chosen by the
/// self-grade buttons and is applied unchanged afterwards, so the 2026-07-22
/// "self-grade only" ruling still owns the schedule (kern README §3). Only words
/// that have not settled take this path, so it never slows a word down that
/// already sticks, and only Again triggers it — Easy/Good/Hard advance straight
/// away, which is how "I already knew this" stays one tap.
extension SessionView {

    /// Only the TARGET language is ever copied; typing the word you already know
    /// would teach nothing. On recognition the target sits in the prompt, on
    /// production in the revealed answer — either way it is on screen.
    private var copyText: String {
        guard let card = model.currentCard else { return "" }
        return card.target.text
    }

    @ViewBuilder
    func copyControls(_ card: Card) -> some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $copyInput,
                            feedback: .neutral,
                            placeholder: copyPlaceholder,
                            focus: $answerFocused) {
                submitCopy(card)
            }
            if copyMissed {
                // why: never punishing — the answer is already on the card, so this
                // is a nudge to look again, not a verdict.
                Text("session.copyMismatch")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            Button {
                submitCopy(card)
            } label: {
                DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            .keyboardShortcut(.defaultAction)
            .disabled(copyInput.trimmingCharacters(in: .whitespaces).isEmpty)

            // why: always reachable — a step you cannot leave is a trap, and the
            // rating is already decided, so skipping costs the schedule nothing.
            Button("session.skipCopy") { applyPendingRating() }
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var copyPlaceholder: String {
        guard let target = model.targetLanguage else { return "" }
        let name = LanguageNames.display(target, locale: locale, catalog: model.catalog)
        return String(format: DLChrome.string("session.copy.placeholder %@", locale: locale), name)
    }

    /// Graded by the same typo-tolerant normalizer as a real produce answer, so a
    /// slipped keystroke does not trap you — but a genuinely different word does
    /// not pass for the copy either.
    func submitCopy(_ card: Card) {
        let trimmed = copyInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let normalizer = model.answerNormalizer else { return }
        switch onEnum(of: normalizer.evaluate(input: trimmed, card: card)) {
        case .wrong:
            DLSound.wrong()
            withAnimation { copyMissed = true }
        default:
            // Exact or a tolerated typo — a slipped keystroke must not trap you.
            DLSound.correct()
            applyPendingRating()
        }
    }

    /// Hand the already-chosen rating to the engine and move on. Goes straight
    /// to `commit`, never back through `rate` — the word is still unsettled here,
    /// so `rate` would divert the same Again into the copy step again.
    func applyPendingRating() {
        guard let rating = copyPending else { return }
        commit(rating)
    }

    /// Whether this card's miss should ask for the word to be written out.
    func wantsCopyStep(_ rating: Rating, card: Card?) -> Bool {
        guard let card, rating == .again, !copyText.isEmpty else { return false }
        return !model.isSettled(card.id)
    }
}
