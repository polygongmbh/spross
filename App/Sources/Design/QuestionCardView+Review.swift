import SprossKern
import SwiftUI

/// The review card: a word over its meaning, set at text size with its grammar,
/// the picture beside the headwords rather than above the whole stack.
extension QuestionCardView {

    /// The prompt is COMPACT (no space reserved for the answer); the reveal expands the card.
    /// The picture belongs to the card rather than the prompt line, so it stays centered against
    /// prompt and reveal together; its slot is held for the card's whole life and mirrored on the
    /// far edge, so a withheld picture fades into a space already kept for it.
    var reviewFace: some View {
        VStack(spacing: Theme.spacing.sm) {
            pictureRow
            if opens && hasClosingLines {
                closingLines
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
            otherWordLine
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        // why: a review card holds one height whether the prompt is a word, a word under an
        // area label, or the replay glyph of a by-ear question.
        .frame(minHeight: Theme.reserve.reviewCard)
        .cardSurface()
        .animation(revealAnimation, value: opens)
    }

    /// The picture stands beside the HEADWORDS — prompt, divider, answer — so it ends up roughly
    /// parallel to the divider instead of floating above the whole stack.
    private var pictureRow: some View {
        HStack(spacing: Theme.spacing.md) {
            if let emoji = reviewEmoji {
                CardEmoji(emoji, cue: question.emojiCue, revealed: opens)
            }
            VStack(spacing: Theme.spacing.sm) {
                VStack(spacing: Theme.spacing.xs) {
                    headwordBlock(question.prompt, emphasized: false)
                    pluralLine(question.prompt)
                }
                if opens {
                    // why: the divider belongs beside the picture, so the reveal
                    // grows the row rather than starting a second stack under it.
                    CardReveal(note: nil) {
                        headwordBlock(question.answer, emphasized: true)
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .frame(maxWidth: .infinity)
            if reviewEmoji != nil { CardEmoji.balance() }
        }
    }

    /// What the reveal says ABOUT the answer rather than as the answer: grammar, the other forms,
    /// the closing note. They are the long lines and sit beside nothing, so they take the full width.
    private var closingLines: some View {
        VStack(spacing: Theme.spacing.xs) {
            pluralLine(question.answer)
            alternatesLine
            if let noteText {
                Text(noteText).noteLine()
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var hasClosingLines: Bool {
        noteText != nil || question.answer.plural != nil || !question.closing.alternates.isEmpty
    }

    private var reviewEmoji: String? {
        guard let emoji = question.emoji, !emoji.isEmpty else { return nil }
        return emoji
    }

    /// The card that owns the screen: height is abundant and width is what the words are short of,
    /// so the picture stands ABOVE them at full size and the words get the card's full width.
    var listeningFace: some View {
        VStack(spacing: Theme.spacing.lg) {
            if let emoji = reviewEmoji {
                CardEmoji(emoji, size: .hero, cue: question.emojiCue, revealed: opens)
            }
            VStack(spacing: Theme.spacing.lg) {
                VStack(spacing: Theme.spacing.xs) {
                    headwordBlock(question.prompt, emphasized: false)
                    pluralLine(question.prompt)
                }
                if opens {
                    CardReveal(note: noteText) {
                        VStack(spacing: Theme.spacing.xs) {
                            headwordBlock(question.answer, emphasized: true)
                            pluralLine(question.answer)
                            alternatesLine
                        }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity)
        .cardSurface()
        .animation(revealAnimation, value: opens)
    }

    private var headlineFont: Font { surface == .listening ? Theme.typography.hero : Theme.typography.title }

    // MARK: - One side

    /// The word itself, under the area named over an ambiguous prompt.
    func headwordBlock(_ side: Question.Side, emphasized: Bool) -> some View {
        VStack(spacing: Theme.spacing.xs) {
            // why: ABOVE the headword, so it reads as a label on the prompt and never
            // sits in the plural/alternates region that belongs to the reveal.
            if let context = side.context {
                Text(areaTitle(context))
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            headline(side, emphasized: emphasized)
                .multilineTextAlignment(.center)
                // why: overflow insurance for a word wider than the line even at the
                // smallest text size (`WholeWords`); a long answer wraps between words.
                .minimumScaleFactor(0.85)
                // why: one floor whether or not the word carries a speaker, so a card does not
                // change height as the synonym rotation lands on an unrecorded form.
                .frame(minHeight: 44)
        }
    }

    @ViewBuilder
    private func headline(_ side: Question.Side, emphasized: Bool) -> some View {
        if side.form == .sound {
            replayGlyph
        } else if let text = side.text {
            SpokenWord(pronounce: pronounce(side), isPlaying: isPlaying(side),
                       badge: side.femMarker ? AnyView(FeminineBadge()) : nil) {
                let spoken = articledForm(article: side.article, form: text)
                // why: tagged with its language, article included, as the line reads on screen.
                WholeWords(text: headlineText(side, text, emphasized: emphasized),
                           content: spoken, font: headlineFont)
                    .spoken(spoken, language: side.lang)
            }
        }
    }

    /// The article inline in its gender's color before the word (poster style); the answer pops
    /// in accent. Both sides share one font, so a word never changes size with its role.
    private func headlineText(_ side: Question.Side, _ text: String, emphasized: Bool) -> Text {
        let word = Text(verbatim: text)
            .font(headlineFont)
            .foregroundStyle(emphasized ? Theme.colors.accent : Theme.colors.textPrimary)
        guard let article = side.article else { return word }
        let gender = Theme.Gender(articleGender(article: article, lang: side.lang))
        return Text(verbatim: articledForm(article: article, form: ""))
            .font(headlineFont)
            .foregroundStyle(Theme.genderColor(gender))
            + word
    }

    @ViewBuilder
    func pluralLine(_ side: Question.Side) -> some View {
        if let plural = side.plural {
            Text(pluralText(plural))
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }

    @ViewBuilder
    var alternatesLine: some View {
        if let alternates = alternatesText {
            // why: matches the plural line — both belong to the reveal,
            // so neither shrinks below the size the learner has to read.
            Text(alternates)
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
        }
    }
}

/// Labeled ♀ badge — marks a feminine-sibling prompt/answer; a grammar cue, never graded.
struct FeminineBadge: View {
    var body: some View {
        Text(verbatim: "♀")
            .pill(Theme.colors.die)
            .accessibilityLabel("a11y.glyph.feminineForm")
    }
}
