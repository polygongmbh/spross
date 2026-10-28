package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.FormTag
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.answerForms
import net.spross.kern.model.article
import net.spross.kern.model.articledForm

/**
 * Typed-answer grading for PRODUCE units, configured per ANSWER language (the
 * profile's target) from `languages.json` — recognize units are self-graded and
 * never pass through here.
 *
 * Pipeline (`docs/grading.md`, both sides symmetric): NFC, stress marks dropped, lowercase, ß→ss, the answer
 * language's digraph spellings (de ä→ae, ö→oe, ü→ue), delete the
 * joiners `-'’`, other punctuation → space (incl. `…—`), collapse whitespace →
 * ONE leading listed article of the answer language is optional → iff the card is
 * a verb, any listed citation prefix (en `"to "`, sw `ku`/`kw`) is optional →
 * Damerau-Levenshtein (OSA) typo budget. Accepted forms = target
 * `text ∪ teaches ∪ accepts` and the `forms` agreeing with the prompt ([answerForms]).
 *
 * [articleLeniency] (the one-arg constructor's default, true) is that
 * optional-article contract for vocab reviews. Drill callers grading article
 * choice itself pass false: [normalize] keeps the leading article, and a form
 * only matches when the typed leading article equals the form's authored one —
 * a wrong or missing article grades [Match.Wrong], never typo-bridges.
 *
 * One typo budget over the whole form, for reviews and drills alike: the more of an
 * answer is right, the more room a misspelled word in it gets — up to a slip per four
 * letters of that word, so one word cannot spend a long sentence's budget on becoming
 * another (`morning` for `evening`). A word carrying a digit
 * grades exact-only: distinct digit renderings ("21"/"29", "18:05" → "18" "05") sit one
 * edit apart, so no positive budget is safe for them.
 */
class AnswerNormalizer(
    private val answerLanguage: LanguageInfo,
    private val articleLeniency: Boolean,
) {

    /** Lenient vocab-review default; the explicit secondary init keeps the ObjC/Swift signature. */
    constructor(answerLanguage: LanguageInfo) : this(answerLanguage, articleLeniency = true)

    /** Declared before [articleForms] and [verbPrefixes]: both are built through it. */
    private val shape = ComparisonShape(answerLanguage)

    /** The listed articles in comparison shape, so a typed token can be measured against them. */
    private val articleForms: Set<String> = answerLanguage.articles
        .map { shape.cleaned(it.lowercase()).trim() }.filter { it.isNotEmpty() }.toSet()
    private val verbPrefixes: List<String> = answerLanguage.optionalVerbPrefixes
        .map(shape::normalizedPrefix)
        .filter { it.isNotEmpty() }

    /**
     * Canonical comparison form. Under [articleLeniency] a leading listed article is
     * stripped, and only when more words follow — typing just "die" must never match
     * "die Spülmaschine"; with leniency off the article stays part of the form.
     */
    fun normalize(raw: String): String {
        var words = shape.tokenize(raw)
        if (articleLeniency && words.size > 1 && words.first() in articleForms) words = words.subList(1, words.size)
        return words.joinToString(" ")
    }

    /** True when the typed input means the card's target answer. */
    fun matches(input: String, card: Card): Boolean = evaluate(input, card) != Match.Wrong

    /**
     * How many leading whole words of [input] already match [answer], word by
     * word, each within its own typo budget — a miss reveals [answer], and a
     * retry only needs to fix the word where the slip actually started rather
     * than retyping the words that were already right.
     */
    fun matchingPrefixWordCount(input: String, answer: String): Int {
        val typed = words(input)
        val expected = words(answer)
        var count = 0
        while (count < typed.size && count < expected.size) {
            val a = shape.cleaned(typed[count]).trim()
            val b = shape.cleaned(expected[count]).trim()
            if (b.isEmpty() || damerauLevenshtein(a, b) > prefixWordBudget(b)) break
            count++
        }
        return count
    }

    /**
     * Every comparison form [raw] can take: the normalized shape first, then —
     * under [verbLeniency] — the same shape with each listed citation prefix
     * dropped. [CatalogAnswerGrader] builds and probes its catalog-wide index
     * through this, so the index can never disagree with [evaluate]'s exact test.
     */
    internal fun comparisonForms(raw: String, verbLeniency: Boolean): List<String> {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return emptyList()
        return prefixVariants(normalized, if (verbLeniency) verbPrefixes else emptyList())
    }

    /**
     * Grade [input] against every accepted target form. Verb-prefix leniency applies
     * iff `kind == verb`; the article-mismatch demotion applies iff the target's
     * grammar carries `gender` and the form matched is the text or an `accepts` entry (a PRESENT
     * leading article that disagrees is a typo, a missing one stays exact; an `accepts` entry
     * authored with its own article is read back against that one) — a `teaches` entry
     * is another word whose article the catalog does not carry, so its own article
     * never demotes. A leading word that reads as a mistyped article
     * and, once dropped, makes the rest match is a typo, not a failure — in vocab
     * reviews only, see [strayLeadingWordRecovery].
     */
    fun evaluate(input: String, card: Card, promptTag: FormTag? = null): Match {
        val forms = answerForms(card, promptTag)
        val prefixes = if (card.kind == CardKind.Verb) verbPrefixes else emptyList()
        val expectedArticle = card.target.article?.let { shape.cleaned(it.lowercase()).trim() }
        // A form authored with its own article is read back against it; one without (a language that writes none) never against the citation's.
        val genderedForms = listOf(card.target.text) + card.target.accepts +
            card.target.forms.filter { it.article != null }.map { it.written }
        val result = evaluate(input, forms.right, prefixes, expectedArticle, genderedForms)
        if (result is Match.Typo) return withTypedArticle(result, input, card)
        if (result != Match.Wrong) return result
        // The word in a form the prompt did not ask for corrects to the one it did.
        if (forms.almost.isNotEmpty() && evaluate(input, forms.almost, prefixes, expectedArticle = null) != Match.Wrong) {
            return withTypedArticle(Match.Typo(corrected = forms.right.first()), input, card)
        }
        return result
    }

    /**
     * The correction as the learner wrote it: an answer typed with a leading article
     * reads back with the card's article ("das Zeugniss" → "das Zeugnis").
     * Only the card's text takes it; every other form carries its own article or one the catalog does not know.
     */
    private fun withTypedArticle(typo: Match.Typo, input: String, card: Card): Match.Typo {
        val article = card.target.article
        if (article == null || typo.corrected != card.target.text) return typo
        if (leadingArticle(input) == null && articlePeeledRemainder(input) == null) return typo
        return typo.copy(shown = articledForm(article, typo.corrected))
    }

    /** [genderedForms] are the accepted forms that share [expectedArticle]; a match elsewhere is never demoted. */
    private fun evaluate(
        input: String,
        accepted: List<String>,
        prefixes: List<String>,
        expectedArticle: String?,
        genderedForms: Collection<String> = emptyList(),
    ): Match {
        val normalizedInput = normalize(input)
        if (normalizedInput.isEmpty()) return Match.Wrong
        val inputVariants = prefixVariants(normalizedInput, prefixes)
        val inputArticle = if (articleLeniency) null else leadingArticle(input)

        var best: Match = Match.Wrong
        var bestForm: String? = null
        var bestDistance = Int.MAX_VALUE
        for (form in accepted) {
            // why: with leniency off a wrong or missing leading article must
            // grade Wrong — the form is out entirely, so the typo budget can
            // never bridge "die zug" to "der Zug".
            if (!articleLeniency && leadingArticle(form) != inputArticle) continue
            val target = normalize(form)
            if (target.isEmpty()) continue
            val candidates = prefixVariants(target, prefixes)
            if (candidates.any { it in inputVariants }) {
                best = Match.Exact
                bestForm = form
                break
            }
            for (candidate in candidates) {
                for (variant in inputVariants) {
                    if (!withinBudget(variant, candidate)) continue
                    // why: the NEAREST accepted form is the correction, not the last one
                    // inside budget — sw `white` carries eight stems, and a slip at
                    // "nyeupe" was corrected to "myeupe" purely by authoring order.
                    // A tie keeps the earlier form, so a card's own text leads its `accepts`.
                    val distance = damerauLevenshtein(variant, candidate)
                    if (distance >= bestDistance) continue
                    bestDistance = distance
                    // Reveal always shows the catalog spelling of the matched form.
                    best = Match.Typo(corrected = form)
                    bestForm = form
                }
            }
        }

        val formArticle = bestForm?.let(::leadingArticle) ?: expectedArticle
        if (best == Match.Exact && formArticle != null && bestForm in genderedForms) {
            val typed = leadingArticle(input)
            if (typed != null && typed != formArticle) {
                best = Match.Typo(corrected = bestForm ?: normalizedInput)
            }
        }
        if (best == Match.Wrong) best = strayLeadingWordRecovery(input, accepted, prefixes)
        return best
    }

    /**
     * Regrade what is left once a mistyped article is dropped; a match after the drop
     * is a typo rather than a failure — the article list holds exact forms only, so
     * "de Zug" would otherwise fail where "der Zug" passes.
     */
    private fun strayLeadingWordRecovery(
        input: String,
        accepted: List<String>,
        prefixes: List<String>,
    ): Match {
        val remainder = articlePeeledRemainder(input) ?: return Match.Wrong
        return when (val regraded = evaluate(remainder, accepted, prefixes, expectedArticle = null)) {
            Match.Exact -> Match.Typo(corrected = accepted.first())
            is Match.Typo -> regraded
            // One card at a time there is no catalog to name — that is the grader's verdict.
            is Match.OtherWord, Match.Wrong -> Match.Wrong
        }
    }

    /**
     * What is left of [input] once a leading mistyped article is dropped, or null when
     * nothing may be dropped. The peeled word must be letters only, no longer than
     * [MAX_LEADING_SLIP_LENGTH], leave at least one word behind, and read as one of the
     * answer language's listed articles — a language that lists none has nothing to
     * mistype, and peeling there is leniency the catalog cannot pay for (de "wann"
     * answered sw "muda nini" came back as a spelling slip of "lini").
     *
     * Null without [articleLeniency] too — the recovery IS article leniency, and a drill
     * grading article choice has every word carry the answer —
     * "fünf vor halb sieben" minus its first word is 18:30, not a misspelling of it —
     * and the recovery RECURSES, peeling one word per level ("son las doce y uno" →
     * "uno"), so a reading decayed onto four other times' answers.
     *
     * [CatalogAnswerGrader] probes this remainder against its owner index, so the form
     * a peeled answer really wrote is read through the rule that peeled it and the two
     * can never drift apart.
     */
    internal fun articlePeeledRemainder(input: String): String? {
        if (!articleLeniency) return null
        val tokens = words(input)
        val first = tokens.firstOrNull() ?: return null
        if (tokens.size < 2 || first.length > MAX_LEADING_SLIP_LENGTH) return null
        if (!first.all { it.isLetter() }) return null
        if (!readsAsArticle(first)) return null
        return tokens.drop(1).joinToString(" ")
    }

    /**
     * Within one slip of a listed article. Every article is shorter than the length
     * [allowedTypos] starts forgiving at, so the budget floors at the single slip this
     * whole rule exists to read back.
     */
    private fun readsAsArticle(token: String): Boolean {
        val typed = shape.cleaned(token).trim()
        return articleForms.any { damerauLevenshtein(typed, it) <= maxOf(1, allowedTypos(it.length)) }
    }

    /** The listed leading article a raw answer starts with (only when more words follow). */
    private fun leadingArticle(raw: String): String? {
        val words = shape.tokenize(raw)
        return words.firstOrNull()?.takeIf { it in articleForms && words.size > 1 }
    }

    /** The form plus, per matching prefix, the form with that leading prefix dropped. */
    private fun prefixVariants(normalized: String, prefixes: List<String>): List<String> {
        val variants = mutableListOf(normalized)
        for (prefix in prefixes) {
            if (normalized.length > prefix.length && normalized.startsWith(prefix)) {
                variants += normalized.substring(prefix.length)
            }
        }
        return variants
    }

    companion object {
        /**
         * The drill's strictness in one place: no article leniency (a wrong or
         * missing article grades Wrong). Every drill on both platforms grades
         * through this, so they can never drift apart.
         */
        fun drill(answerLanguage: LanguageInfo): AnswerNormalizer =
            AnswerNormalizer(answerLanguage, articleLeniency = false)

        /**
         * Nothing worth grading was typed — a submit carrying it MEANS reveal wherever a
         * reveal is still legal, so a button press and an Enter take the same action.
         * A typing-first surface reads this to LABEL its ONE primary action; which action
         * that press then IS, kern decides.
         */
        fun isBlankAnswer(raw: String): Boolean = raw.trim().isEmpty()

        /**
         * No listed article is longer than this, so a longer leading word is part of
         * the answer whatever else it resembles — the cheap pre-filter in front of the
         * article test itself.
         */
        private const val MAX_LEADING_SLIP_LENGTH = 4

        internal val whitespaceRun = Regex("\\s+")

        /**
         * The words an answer is graded and primed by, so a caller that counts them
         * ([matchingPrefixWordCount]) and a caller that keeps them cannot disagree.
         */
        internal fun words(text: String): List<String> =
            text.trim().split(whitespaceRun).filter { it.isNotEmpty() }
    }
}
