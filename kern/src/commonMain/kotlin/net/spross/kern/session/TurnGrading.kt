package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.PresentationRole

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
        promptForm: String,
    ): List<String> {
        if (role == PresentationRole.Produce) return emptyList()
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
        if (state.role == PresentationRole.Hear) return ""
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
        return graded.match == Match.Exact && graded.hold == null
    }

    /**
     * The verdict [text] earns, in the language THIS turn asks its answer in.
     *
     * A card asked by ear owes the MEANING ([meaningSide]) — writing back the word that
     * played proves the ear worked and nothing else — so it is graded by the source
     * language's normalizer, whose articles and typo budget are the ones the learner is
     * writing under. Every other turn owes the target word, with the whole join in view.
     *
     * Either way the prompt may stand for more than one concept: the target writes alike what
     * the source splits (sw `kuacha` is verlassen AND aufhören), or the source writes alike what
     * the target splits (de `gehen` is kwenda AND kutembea), and every concept the prompt stands
     * for answers it. The prompted card leads — its own verdict wins where it has one — and a
     * borrowed one comes back held as [AlmostReason.Merged].
     *
     * The other word of the prompt's family is almost: typed for de `Husten`, sw `kukohoa`
     * (`husten`) is the right word in the wrong word class, held as [AlmostReason.Family] on this card's own.
     */
    fun grade(state: TurnState, text: String): Graded {
        if (state.role != PresentationRole.Hear) {
            val graded = merged(
                own = grader.grade(text, state.card, state.promptTag),
                shared = grader.promptSharers(state.card, meaningNormalizer.answerLanguage).map { grader.grade(text, it, state.promptTag) },
            )
            if (graded.match !is Match.OtherWord || grader.otherOwners(text, state.card).none { family(it, state.card) }) return graded
            return Graded(Match.Typo(corrected = state.answerText), hold = AlmostReason.Family)
        }
        return merged(
            own = meaningNormalizer.evaluate(text, meaningSide(state.card)),
            shared = grader.conceptsSharing(state.promptForm, state.card).map { meaningNormalizer.evaluate(text, meaningSide(it)) },
        )
    }

    private fun merged(own: Match, shared: List<Match>): Graded {
        if (own == Match.Exact) return Graded(own)
        shared.firstOrNull { it == Match.Exact }?.let { return Graded(it, AlmostReason.Merged) }
        // A slip is forgiven against the word it was aiming at, and the prompted card owns
        // its own slips first — the same order the normalizer keeps a card's text ahead of
        // its `accepts` in.
        if (own is Match.Typo) return Graded(own)
        shared.firstOrNull { it is Match.Typo }?.let { return Graded(it, AlmostReason.Merged) }
        return Graded(own)
    }

    /**
     * A noun and a verb whose sources show the same word once its article and citation prefix are set aside
     * (de `Husten`/`husten`, en `cough`/`to cough`): one family, two word classes.
     */
    private fun family(other: Card, card: Card): Boolean {
        if (setOf(other.kind, card.kind) != setOf(CardKind.Noun, CardKind.Verb)) return false
        val asked = bare(card)
        return bare(other).any { it in asked }
    }

    private fun bare(card: Card): List<String> =
        meaningNormalizer.comparisonForms(card.source.text, verbLeniency = card.kind == CardKind.Verb)

    /** A verdict, plus the hold it earns where it is not this card's own clean answer: a merged meaning, or the family's other word. */
    data class Graded(val match: Match, val hold: AlmostReason? = null)
}
