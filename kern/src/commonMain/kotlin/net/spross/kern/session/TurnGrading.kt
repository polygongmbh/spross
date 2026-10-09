package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt

/**
 * What a typed answer is worth to a [TurnMachine] turn, in the language the turn asks it in:
 * the target word against the whole join ([grader], [normalizer]), or — asked by ear — the
 * meaning, in the source language's own terms ([meaningNormalizer]).
 */
internal class TurnGrading(
    private val grader: CatalogAnswerGrader,
    private val normalizer: AnswerNormalizer,
    private val meaningNormalizer: AnswerNormalizer,
) {

    /**
     * The meanings [promptForm] carries besides this card's own — what the reveal has to say
     * where the target language merges two of the source's words into one of its own.
     *
     * Only where the turn ASKS what a word means: a produce prompt stands on the source word,
     * and probing the target index with it would hit real cards in a same-script pair.
     * What already stands on the reveal comes out — the rule `DisplayText.alternates` keeps
     * inside one concept, applied across them, so nothing is offered back as though it were news.
     */
    fun alsoMeans(
        card: Card,
        role: PresentationRole,
        prompt: ProducePrompt,
        promptForm: String,
    ): List<String> {
        if (role != PresentationRole.Recognize && prompt != ProducePrompt.Sound) return emptyList()
        val standing = (listOf(card.source.text) + card.source.teaches).toSet()
        return grader.conceptsSharing(promptForm, card)
            .map { it.source.text }
            .filterNot { it in standing }
            .distinct()
    }

    /**
     * Keep the leading WHOLE words that were already right and drop the wrong tail, so the
     * retype picks up where the slip started instead of from scratch. Nothing kept clears it.
     * A kept word is written as the ANSWER spells it — a forgiven slip is not left for the
     * learner to hunt down — and the last answer word is never primed, so the field cannot
     * hand over a finished retype. A card asked by ear primes nothing: it has no retype.
     */
    fun primed(state: TurnState, text: String): String {
        if (state.prompt == ProducePrompt.Sound) return ""
        val expected = AnswerNormalizer.words(state.answerText)
        val count = normalizer.matchingPrefixWordCount(text, state.answerText)
        val kept = expected
            .take(minOf(count, expected.size - 1))
            .joinToString(" ")
        return if (kept.isEmpty()) "" else "$kept "
    }

    /**
     * Live green is THIS card's answer and no other: a borrowed meaning is right, but it
     * holds on the word the card teaches ([TurnMachine]), and a beat armed here would carry the
     * turn away before that word was ever seen.
     */
    fun isExact(state: TurnState, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val graded = grade(state, trimmed)
        return graded.match == Match.Exact && !graded.merged
    }

    /**
     * The verdict [text] earns, in the language THIS turn asks its answer in.
     *
     * A card asked by ear owes the MEANING ([meaningSide]) — writing back the word that
     * played proves the ear worked and nothing else — so it is graded by the source
     * language's normalizer, whose articles and typo budget are the ones the learner is
     * writing under. Every other turn owes the target word, with the whole join in view.
     *
     * The meaning side reads the join too, from the other end: the form that played may be
     * printed by more than one concept, because the target language merges what the source
     * splits (sw `kuacha` is verlassen AND aufhören), and every meaning it carries is a
     * right answer to what it means. The prompted card leads — its own verdict wins where
     * it has one — and a borrowed one comes back [Graded.merged], for the turn to hold on.
     */
    fun grade(state: TurnState, text: String): Graded {
        if (state.prompt != ProducePrompt.Sound) return Graded(grader.grade(text, state.card, state.promptTag))
        val own = meaningNormalizer.evaluate(text, meaningSide(state.card))
        if (own == Match.Exact) return Graded(own)
        val shared = grader.conceptsSharing(state.promptForm, state.card)
            .map { meaningNormalizer.evaluate(text, meaningSide(it)) }
        shared.firstOrNull { it == Match.Exact }?.let { return Graded(it, merged = true) }
        // A slip is forgiven against the word it was aiming at, and the prompted card owns
        // its own slips first — the same order the normalizer keeps a card's text ahead of
        // its `accepts` in.
        if (own != Match.Wrong) return Graded(own)
        shared.firstOrNull { it is Match.Typo }?.let { return Graded(it, merged = true) }
        return Graded(own)
    }

    /** A verdict, plus whether it was earned on a meaning this card does not itself teach. */
    data class Graded(val match: Match, val merged: Boolean = false)
}
