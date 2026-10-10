package net.spross.kern.session

import net.spross.kern.model.APOSTROPHES
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.FormTag
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.answerForms
import net.spross.kern.model.article
import net.spross.kern.model.hyphensAndApostrophesStripped
import net.spross.kern.model.nfcNormalized
import net.spross.kern.model.stressFolded

/**
 * Typed-answer grading for PRODUCE units, configured per ANSWER language (the
 * profile's target) from `languages.json` — recognize units are self-graded and
 * never pass through here.
 *
 * Pipeline (`kern/docs/grading.md`, both sides symmetric): NFC, stress marks dropped, lowercase, ß→ss, the answer
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

    /** Declared before [articleForms]: [cleaned] reads it, and that field is built through [cleaned]. */
    private val digraphFolds: List<Pair<String, String>> = answerLanguage.diacriticDigraphs
        .map { (letter, digraph) -> letter.lowercase() to digraph.lowercase() }

    private val articles: Set<String> = answerLanguage.articles.map { it.lowercase() }.toSet()

    /** The listed articles that elide (`l'`), without their apostrophe. */
    private val elidedArticles: List<String> =
        articles.filter { it.lastOrNull() in APOSTROPHES }.map { it.dropLast(1) }.filter { it.isNotEmpty() }

    /** The same articles in comparison shape, so a typed token can be measured against them. */
    private val articleForms: Set<String> =
        articles.map { cleaned(it).trim() }.filter { it.isNotEmpty() }.toSet()
    private val verbPrefixes: List<String> = answerLanguage.optionalVerbPrefixes
        .map(::normalizedPrefix)
        .filter { it.isNotEmpty() }

    /**
     * Canonical comparison form. Under [articleLeniency] a leading listed article is
     * stripped, and only when more words follow — typing just "die" must never match
     * "die Spülmaschine"; with leniency off the article stays part of the form.
     */
    fun normalize(raw: String): String {
        var words = tokenize(raw)
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
            val a = cleaned(typed[count]).trim()
            val b = cleaned(expected[count]).trim()
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
        val expectedArticle = card.target.article?.let { cleaned(it.lowercase()).trim() }
        // A form authored with its own article is read back against it; one without (a language that writes none) never against the citation's.
        val genderedForms = listOf(card.target.text) + card.target.accepts +
            card.target.forms.filter { it.article != null }.map { it.written }
        val result = evaluate(input, forms.right, prefixes, expectedArticle, genderedForms)
        if (result != Match.Wrong) return result
        // The word in a form the prompt did not ask for corrects to the one it did.
        if (forms.almost.isNotEmpty() && evaluate(input, forms.almost, prefixes, expectedArticle = null) != Match.Wrong) {
            return Match.Typo(corrected = forms.right.first())
        }
        return result
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
        val typed = cleaned(token).trim()
        return articleForms.any { damerauLevenshtein(typed, it) <= maxOf(1, allowedTypos(it.length)) }
    }

    /** Is [input] within the slips [candidate] forgives, its digits typed exactly? */
    private fun withinBudget(input: String, candidate: String): Boolean {
        if (digitWords(input) != digitWords(candidate)) return false
        if (damerauLevenshtein(input, candidate) > allowedTypos(candidate.count { it != ' ' })) return false
        val typed = input.split(' ')
        val expected = candidate.split(' ')
        // why: the per-word cap needs the words to line up — a dropped or added
        // word leaves the whole-form budget alone to decide.
        if (typed.size != expected.size) return true
        return expected.indices.all { i -> damerauLevenshtein(typed[i], expected[i]) <= wordCap(expected[i]) }
    }

    private fun wordCap(word: String): Int = (word.length + LETTERS_PER_WORD_SLIP - 1) / LETTERS_PER_WORD_SLIP

    private fun digitWords(form: String): List<String> =
        form.split(' ').filter { word -> word.any { it.isDigit() } }

    /**
     * One word's slips for [matchingPrefixWordCount]'s retry-priming rule:
     * the length-scaled formula measured per word, so a short mistyped word
     * never keeps more of the retry field than a retry is meant to prime.
     */
    private fun prefixWordBudget(word: String): Int =
        if (word.any { it.isDigit() }) 0 else allowedTypos(word.length)

    /** The listed leading article a raw answer starts with (only when more words follow). */
    private fun leadingArticle(raw: String): String? {
        val words = tokenize(raw)
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

    private fun tokenize(raw: String): List<String> =
        cleaned(elisionSeparated(raw)).split(' ').filter { it.isNotEmpty() }

    /**
     * [raw] with a leading elided article (fr/it `l'`) set apart as its own word, so the article
     * rules see it as they see `la` — the apostrophe pass would otherwise glue it on (`linvitée`).
     */
    private fun elisionSeparated(raw: String): String {
        val trimmed = raw.trimStart()
        val head = trimmed.lowercase()
        for (article in elidedArticles) {
            if (head.length > article.length && head.startsWith(article) && head[article.length] in APOSTROPHES) {
                return trimmed.substring(0, article.length + 1) + " " + trimmed.substring(article.length + 1)
            }
        }
        return raw
    }

    /**
     * The one character pass everything shares, so tokenization can never disagree:
     * NFC, stress marks dropped (uk `пі́вніч` is typed `північ`), lowercase, ß→ss (2 edits — too far for short words' typo budget), the answer
     * language's [LanguageInfo.diacriticDigraphs] (de ä→ae, ö→oe, ü→ue), joiners
     * `-` and the apostrophe class deleted outright ("E-Mail"/"Email", "geht's"/"gehts"), every other
     * non-alphanumeric — punctuation incl. `…—`, and whitespace — becomes a space.
     *
     * The digraph fold runs on both sides like ß→ss does, and for the same reason: it is
     * a full, established ASCII spelling of the letter rather than a slip, so "Kueche"
     * grades [Match.Exact] on a "Küche" card. Folding also LENGTHENS a short word before
     * [allowedTypos] measures it — "für" becomes "fuer" and forgives the slip three
     * letters could not. Dropping a diacritic outright is the other rule and stays out of
     * here on purpose: it is free inside [damerauLevenshtein] only, so it can never reach
     * the exact test and bypass [CatalogAnswerGrader]'s collision check.
     */
    private fun cleaned(raw: String): String {
        var lowered = stressFolded(nfcNormalized(raw)).lowercase().replace("ß", "ss")
        for ((letter, digraph) in digraphFolds) lowered = lowered.replace(letter, digraph)
        val out = StringBuilder(lowered.length)
        for (ch in hyphensAndApostrophesStripped(lowered)) {
            when {
                ch.isLetter() || ch.isDigit() -> out.append(ch)
                else -> out.append(' ')
            }
        }
        return out.toString()
    }

    /** Prefix in comparison shape, space-preserving: en `"to "` keeps its trailing space. */
    private fun normalizedPrefix(raw: String): String =
        cleaned(raw).replace(whitespaceRun, " ")

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

        private val whitespaceRun = Regex("\\s+")

        /**
         * The words an answer is graded and primed by, so a caller that counts them
         * ([matchingPrefixWordCount]) and a caller that keeps them cannot disagree.
         */
        internal fun words(text: String): List<String> =
            text.trim().split(whitespaceRun).filter { it.isNotEmpty() }

        /**
         * ~⅙ of letters, but never for words under [MIN_TYPO_LENGTH]:
         * a four-letter word forgives one slip and a long phrase a slip per six letters.
         * That much is safe because [CatalogAnswerGrader] withdraws the credit wherever the
         * typed form is really another concept's word (RealCatalogGradingTest
         * sweeps the shipping catalog for exactly that).
         *
         * A dropped diacritic never needs this budget at all: [damerauLevenshtein]
         * charges nothing for it, so fr "ou" for "où" is a typo even at the floor
         * where the budget is zero — which is the whole point, short accented words
         * being where the floor bit hardest.
         */
        private fun allowedTypos(letters: Int): Int =
            if (letters < MIN_TYPO_LENGTH) 0 else maxOf(1, letters / TYPO_LETTERS_PER_SLIP)

        /** Below this many letters an answer is graded exact-only. */
        private const val MIN_TYPO_LENGTH = 4

        private const val TYPO_LETTERS_PER_SLIP = 6

        /** A word's own cap on its share of the budget: a slip per this many letters, rounded up. */
        private const val LETTERS_PER_WORD_SLIP = 4
    }
}
