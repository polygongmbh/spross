package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
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

    data object Wrong : Match
}

/**
 * Typed-answer grading for PRODUCE units, configured per ANSWER language (the
 * profile's target) from `languages.json` — recognize units are self-graded and
 * never pass through here.
 *
 * Pipeline (contract §6, both sides symmetric): NFC, lowercase, ß→ss, delete the
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
 * [maxTypoBudget] (default null = the v1 formula untouched) clamps the typo
 * budget for trainer/drill grading — drills pass 1, which never bridges two
 * distinct German number words (guard sweep in TrainerTypoBridgeGuardTests;
 * audited exceptions sw nne↔nane and uk дев'ять↔десять sit one edit apart and
 * are gated there explicitly). With a cap set, digit-bearing accepted forms
 * grade exact-only: distinct digit renderings ("21"/"29", "18:05"/"18:06")
 * sit one edit apart at any sentence length, so no positive budget is safe
 * for them.
 */
class AnswerNormalizer(
    answerLanguage: LanguageInfo,
    private val articleLeniency: Boolean,
    private val maxTypoBudget: Int?,
) {

    /** Lenient vocab-review default; explicit secondary inits keep the ObjC/Swift signatures. */
    constructor(answerLanguage: LanguageInfo) :
        this(answerLanguage, articleLeniency = true, maxTypoBudget = null)

    constructor(answerLanguage: LanguageInfo, articleLeniency: Boolean) :
        this(answerLanguage, articleLeniency, maxTypoBudget = null)

    private val articles: Set<String> = answerLanguage.articles.map { it.lowercase() }.toSet()
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
     * Grade [input] against every accepted target form. Verb-prefix leniency applies
     * iff `kind == verb`; the article-mismatch demotion applies iff the target's
     * grammar carries `gender` (a PRESENT leading article that disagrees is a typo,
     * a missing one stays exact). A stray unrecognized short leading word that,
     * once dropped, makes the rest match is a typo, not a failure.
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
                if (inputVariants.any { damerauLevenshtein(it, candidate) <= typoBudget(candidate) }) {
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
        if (best == Match.Wrong) {
            best = strayLeadingWordRecovery(input, accepted, prefixes)
        }
        return best
    }

    /** Drop a short all-letter leading word and regrade; a match after the drop is a typo. */
    private fun strayLeadingWordRecovery(
        input: String,
        accepted: List<String>,
        prefixes: List<String>,
    ): Match {
        val tokens = input.split(whitespaceRun).filter { it.isNotEmpty() }
        val first = tokens.firstOrNull() ?: return Match.Wrong
        if (tokens.size < 2 || first.length > MAX_LEADING_SLIP_LENGTH) return Match.Wrong
        if (!first.all { it.isLetter() }) return Match.Wrong
        val remainder = tokens.drop(1).joinToString(" ")
        return when (val regraded = evaluate(remainder, accepted, prefixes, expectedArticle = null)) {
            Match.Exact -> Match.Typo(corrected = accepted.first())
            is Match.Typo -> regraded
            Match.Wrong -> Match.Wrong
        }
    }

    /** The v1 length formula, clamped by [maxTypoBudget]; capped digit forms get none. */
    private fun typoBudget(candidate: String): Int {
        val base = allowedTypos(candidate.count { it != ' ' })
        val cap = maxTypoBudget ?: return base
        // why: "21"/"29" and "18:05"/"18:06" are one substitution apart however
        // long the sentence frame — a capped (drill) normalizer therefore takes
        // digit-bearing forms exact-only while word forms keep up to [cap] slips.
        if (candidate.any { it.isDigit() }) return 0
        return minOf(base, cap)
    }

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
     * NFC, lowercase, ß→ss (2 edits — too far for short words' typo budget), joiners
     * `-'’` deleted outright ("E-Mail"/"Email", "geht's"/"gehts"), every other
     * non-alphanumeric — punctuation incl. `…—`, and whitespace — becomes a space.
     */
    private fun cleaned(raw: String): String {
        val lowered = nfcNormalized(raw).lowercase().replace("ß", "ss")
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

    private companion object {
        /**
         * A stray leading word this short (letters only) is treated as a mistyped
         * article/particle rather than part of the answer.
         */
        const val MAX_LEADING_SLIP_LENGTH = 4

        val whitespaceRun = Regex("\\s+")

        /** ~10 % of letters, but never for very short words (v1 formula). */
        fun allowedTypos(letters: Int): Int = if (letters < 5) 0 else maxOf(1, letters / 10)

        /**
         * Optimal-string-alignment Damerau-Levenshtein: insert, delete, substitute,
         * and adjacent transposition each cost 1.
         */
        fun damerauLevenshtein(a: String, b: String): Int {
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
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
