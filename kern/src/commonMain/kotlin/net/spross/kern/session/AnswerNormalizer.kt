package net.spross.kern.session

import net.spross.kern.model.ACCENTED_VOWEL_BASE
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Rating
import net.spross.kern.model.baseVowel
import net.spross.kern.model.nfcNormalized

/** Grading verdict for a typed produce answer. */
sealed interface Match {
    data object Exact : Match

    /**
     * Accepted with a small slip; carries the accepted form as authored in the
     * catalog (proper spelling for the UI's correction display, not the
     * lowercased/stripped comparison form).
     */
    data class Typo(val corrected: String) : Match

    /**
     * The typed answer IS a different concept's word in the answer language —
     * a miss, but a nameable one: [word] as the catalog spells it, [meanings]
     * the source-side words of every concept that owns it (seed order).
     * Only [CatalogAnswerGrader] produces this; a bare normalizer sees one card.
     */
    data class OtherWord(val word: String, val meanings: List<String>) : Match

    data object Wrong : Match

    /**
     * The FSRS rating this match earns on its own, before any reveal/retry
     * step takes over. [Exact] came back clean (Good); a [Typo] came back
     * readable but imperfect — the same Hard a finished retype after a reveal
     * earns, because neither came back on the first, unaided try. One rule
     * here rather than in each platform's UI, so a produce screen never
     * re-derives it and drifts from another (iOS graded a typo Good until
     * 2026-08-07; Android never did).
     * Null for [OtherWord] and [Wrong]: neither has a rating of its own —
     * both route through reveal, where the eventual retype (Hard) or
     * give-up (Again) decides it.
     */
    fun producedRating(): Rating? = when (this) {
        Exact -> Rating.Good
        is Typo -> Rating.Hard
        is OtherWord, Wrong -> null
    }
}

/**
 * Typed-answer grading for PRODUCE units, configured per ANSWER language (the
 * profile's target) from `languages.json` — recognize units are self-graded and
 * never pass through here.
 *
 * Pipeline (`kern/docs/grading.md`, both sides symmetric): NFC, lowercase, ß→ss, the answer
 * language's digraph spellings (de ä→ae, ö→oe, ü→ue), delete the
 * joiners `-'’`, other punctuation → space (incl. `…—`), collapse whitespace →
 * ONE leading listed article of the answer language is optional → iff the card is
 * a verb, any listed citation prefix (en `"to "`, sw `ku`/`kw`) is optional →
 * Damerau-Levenshtein (OSA) typo budget. Accepted forms = target
 * `text ∪ synonyms ∪ variants`.
 *
 * [articleLeniency] (the one-arg constructor's default, true) is that
 * optional-article contract for vocab reviews. Drill callers grading article
 * choice itself pass false: [normalize] keeps the leading article, and a form
 * only matches when the typed leading article equals the form's authored one —
 * a wrong or missing article grades [Match.Wrong], never typo-bridges.
 *
 * [maxTyposPerWord] (default null = one budget for the whole form) switches
 * grading to the WORD-WISE rule trainer drills need — drills pass 1. What a
 * drill must not do is accept one number for another, and that danger lives
 * inside the number, not across the sentence around it: most distinct
 * cardinals sit ≥ 2 edits apart, so capping each word at one slip keeps them
 * apart while the sentence as a whole may fumble once per word, and the few
 * one-edit twins (sw nne↔nane, uk дев'ять↔десять) are refused above this
 * budget by the drill grader's own value check (`otherNumber`). The cap applies flatly
 * to every word regardless of its own length, unlike the whole-phrase rule
 * below — a short word (e.g. "für") forgives the same one slip a long one
 * does. A word carrying a digit still grades exact-only: distinct digit
 * renderings ("21"/"29", "18:05" → "18" "05") sit one edit apart, so no
 * positive budget is safe for them.
 */
class AnswerNormalizer(
    answerLanguage: LanguageInfo,
    private val articleLeniency: Boolean,
    private val maxTyposPerWord: Int?,
) {

    /** Lenient vocab-review default; explicit secondary inits keep the ObjC/Swift signatures. */
    constructor(answerLanguage: LanguageInfo) :
        this(answerLanguage, articleLeniency = true, maxTyposPerWord = null)

    constructor(answerLanguage: LanguageInfo, articleLeniency: Boolean) :
        this(answerLanguage, articleLeniency, maxTyposPerWord = null)

    /** Declared before [articleForms]: [cleaned] reads it, and that field is built through [cleaned]. */
    private val digraphFolds: List<Pair<String, String>> = answerLanguage.diacriticDigraphs
        .map { (letter, digraph) -> letter.lowercase() to digraph.lowercase() }

    private val articles: Set<String> = answerLanguage.articles.map { it.lowercase() }.toSet()

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
        if (articleLeniency && words.size > 1 && words.first() in articles) words = words.subList(1, words.size)
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
        val typed = input.trim().split(whitespaceRun).filter { it.isNotEmpty() }
        val expected = answer.trim().split(whitespaceRun).filter { it.isNotEmpty() }
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
     * grammar carries `gender` (a PRESENT leading article that disagrees is a typo,
     * a missing one stays exact). A leading word that reads as a mistyped article
     * and, once dropped, makes the rest match is a typo, not a failure — in vocab
     * reviews only, see [strayLeadingWordRecovery].
     */
    fun evaluate(input: String, card: Card): Match {
        val accepted = listOf(card.target.text) + card.target.synonyms + card.target.variants
        val prefixes = if (card.kind == CardKind.Verb) verbPrefixes else emptyList()
        val expectedArticle = card.target.grammar["gender"]?.lowercase()
        val result = evaluate(input, accepted, prefixes, expectedArticle)
        // Base-word answer on a feminine card grades as typo, not failure (§3):
        // anything the BASE concept would accept demotes to the feminine correction.
        if (result == Match.Wrong && card.baseAccepted.isNotEmpty() &&
            evaluate(input, card.baseAccepted, prefixes, expectedArticle = null) != Match.Wrong
        ) {
            return Match.Typo(corrected = card.target.text)
        }
        return result
    }

    private fun evaluate(
        input: String,
        accepted: List<String>,
        prefixes: List<String>,
        expectedArticle: String?,
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
                    // A tie keeps the earlier form, so a card's own text leads its variants.
                    val distance = damerauLevenshtein(variant, candidate)
                    if (distance >= bestDistance) continue
                    bestDistance = distance
                    // Reveal always shows the catalog spelling of the matched form.
                    best = Match.Typo(corrected = form)
                    bestForm = form
                }
            }
        }

        if (best == Match.Exact && expectedArticle != null) {
            val typed = leadingArticle(input)
            if (typed != null && typed != expectedArticle) {
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
     * Null in a drill too ([maxTyposPerWord] set): there every word carries the answer —
     * "fünf vor halb sieben" minus its first word is 18:30, not a misspelling of it —
     * and the recovery RECURSES, peeling one word per level ("son las doce y uno" →
     * "uno"), so a reading decayed onto four other times' answers.
     *
     * [CatalogAnswerGrader] probes this remainder against its owner index, so the form
     * a peeled answer really wrote is read through the rule that peeled it and the two
     * can never drift apart.
     */
    internal fun articlePeeledRemainder(input: String): String? {
        if (maxTyposPerWord != null) return null
        val tokens = input.split(whitespaceRun).filter { it.isNotEmpty() }
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

    /**
     * Is [input] within the slips [candidate] forgives? One budget over the whole
     * form for vocab reviews; word by word once [maxTyposPerWord] is set.
     */
    private fun withinBudget(input: String, candidate: String): Boolean {
        val cap = maxTyposPerWord
            ?: return damerauLevenshtein(input, candidate) <= allowedTypos(candidate.count { it != ' ' })
        val typed = input.split(' ')
        val expected = candidate.split(' ')
        // why: word-wise grading needs words to line up — a dropped or added one
        // falls back to the whole-form rule, so drills forgive everything they
        // forgave before, digit-bearing forms exact-only included.
        if (typed.size != expected.size) {
            if (candidate.any { it.isDigit() }) return false
            val budget = minOf(allowedTypos(candidate.count { it != ' ' }), cap)
            return damerauLevenshtein(input, candidate) <= budget
        }
        return expected.indices.all { i ->
            damerauLevenshtein(typed[i], expected[i]) <= wordBudget(expected[i], cap)
        }
    }

    /**
     * One word's slips under the drill's per-word cap: none at all for a
     * digit, else the cap itself — flat, regardless of the word's own
     * length. The cap (currently always 1) already dominated the length
     * formula for every word the formula was tuned for (≥4 letters), so a
     * shorter word now gets the same cap instead of being floored to zero
     * for no safety reason: the digit check above is what actually keeps a
     * drill from bridging one number into another.
     */
    private fun wordBudget(word: String, cap: Int): Int =
        if (word.any { it.isDigit() }) 0 else cap

    /**
     * One word's slips for [matchingPrefixWordCount]'s retry-priming rule:
     * the length-scaled formula, same floor whole-phrase vocab reviews use.
     * Unrelated to [maxTyposPerWord] — this UI-only helper (which words of a
     * miss to keep in the retry field) has never read it and must not start
     * now, or a short mistyped word would keep far more of the field than a
     * retry is meant to prime.
     */
    private fun prefixWordBudget(word: String): Int =
        if (word.any { it.isDigit() }) 0 else allowedTypos(word.length)

    /** The listed leading article a raw answer starts with (only when more words follow). */
    private fun leadingArticle(raw: String): String? {
        val words = tokenize(raw)
        return words.firstOrNull()?.takeIf { it in articles && words.size > 1 }
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
        cleaned(raw).split(' ').filter { it.isNotEmpty() }

    /**
     * The one character pass everything shares, so tokenization can never disagree:
     * NFC, lowercase, ß→ss (2 edits — too far for short words' typo budget), the answer
     * language's [LanguageInfo.diacriticDigraphs] (de ä→ae, ö→oe, ü→ue), joiners
     * `-'’` deleted outright ("E-Mail"/"Email", "geht's"/"gehts"), every other
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
        var lowered = nfcNormalized(raw).lowercase().replace("ß", "ss")
        for ((letter, digraph) in digraphFolds) lowered = lowered.replace(letter, digraph)
        val out = StringBuilder(lowered.length)
        for (ch in lowered) {
            when {
                ch == '-' || ch == '\'' || ch == '’' -> {}
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
         * missing article grades Wrong), one slip per word flatly. Both drills on
         * both platforms grade through this, so the triple can never drift apart.
         */
        fun drill(answerLanguage: LanguageInfo): AnswerNormalizer =
            AnswerNormalizer(answerLanguage, articleLeniency = false, maxTyposPerWord = 1)

        /**
         * No listed article is longer than this, so a longer leading word is part of
         * the answer whatever else it resembles — the cheap pre-filter in front of the
         * article test itself.
         */
        private const val MAX_LEADING_SLIP_LENGTH = 4

        private val whitespaceRun = Regex("\\s+")

        /**
         * ~⅙ of letters, but never for words under [MIN_TYPO_LENGTH].
         * Wider than v1's `<5 → 0, /10` on both ends: a four-letter word now
         * forgives one slip and a long phrase forgives a slip per six letters,
         * because [CatalogAnswerGrader] withdraws the credit again wherever the
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

        const val TYPO_LETTERS_PER_SLIP = 6

        /**
         * Optimal-string-alignment Damerau-Levenshtein: insert, delete, substitute,
         * and adjacent transposition each cost 1 — except a substitution between two
         * spellings of the same base vowel ([ACCENTED_VOWEL_BASE]), which is free, so a
         * dropped or wrong diacritic costs nothing however short the word. Only the
         * listed typing-convenience accents are free; `ç`, `ñ`, Esperanto `ĉĝĥĵŝŭ` and
         * Ukrainian `й`/`ї` are distinct letters and stay full price (es "ano"/"año").
         * The comparison strings keep their accents, so this reaches the typo path only
         * — a diacritic miss grades [Match.Typo], never [Match.Exact].
         */
        fun damerauLevenshtein(a: String, b: String): Int {
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    val same = a[i - 1] == b[j - 1] || baseVowel(a[i - 1]) == baseVowel(b[j - 1])
                    val cost = if (same) 0 else 1
                    d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                    }
                }
            }
            return d[a.length][b.length]
        }
    }
}
