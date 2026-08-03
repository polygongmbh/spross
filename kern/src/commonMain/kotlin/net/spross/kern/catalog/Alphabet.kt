package net.spross.kern.catalog

import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized

/**
 * What a row of the alphabet IS — the field that decides whether it can be asked at all.
 * [Rule] is a sheet-only prose row (uk's no-final-devoicing table): it renders on the
 * reference sheet, is never prompted, and never sits on a choice tile, where a
 * multi-glyph string would read as nonsense and leak the answer by elimination.
 */
enum class AlphabetKind { Letter, Digraph, Contextual, Rule }

/**
 * A named group of rows — the umlauts together, the ch/sch family together, the plain
 * letters last. Which rows belong with which is a fact about the LANGUAGE, so it is
 * authored beside them and not derived from a glyph's shape: German groups ei/ie/eu/äu
 * because they are the vowel pairs, and no property of the strings says so.
 *
 * Optional per file. Ukrainian authors none — its order IS the alphabet, and a learner
 * needs that order for a dictionary or a form, so grouping it would cost more than the
 * reading it buys.
 */
data class AlphabetSection(
    val id: String,
    /** Keyed by READER language, on [AlphabetEntry.hints]' rule. */
    val titles: Map<Language, String>,
)

/** One row of `catalog/alphabet/<lang>.json`; hand-parsed by [AlphabetParser]. */
data class AlphabetEntry( // data class: Swift sees value equality (kern README §9)
    /** The stable key every reference uses: the authored `id`, else the [glyph]. */
    val ref: String,
    /** Lowercase form, or the multigraph as written; NFC-stored. */
    val glyph: String,
    val upper: String?,
    val kind: AlphabetKind,
    /** The letter's own name — the string a synthesizer is handed, never the bare glyph. */
    val name: String?,
    val ipa: String?,
    /** Concept slug of the example word; resolved per language by [Catalog.alphabetExample]. */
    val exampleSlug: String?,
    /** Verbatim example for what no concept covers; [exampleSlug] wins where both stand. */
    val exampleText: String?,
    /** Keyed by READER language. */
    val hints: Map<Language, String>,
    /** When the glyph takes this value, keyed by READER language. */
    val context: Map<Language, String>,
    /** `false` keeps a silent grapheme out of every prompt; it stays a choice tile. */
    val drill: Boolean,
    /**
     * `false` bars the catalog sweep for this row — its glyph string turns up in words
     * that do NOT say what the row teaches, so only the authored example may gap it
     * (de `chs`: the catalog's only hit is a compound seam, `ch`+`s`, not the /ks/).
     * Says nothing on a row [Alphabet.minesExamples] rejects anyway.
     */
    val mine: Boolean,
    /** Id of the [AlphabetSection] this row sits in; null where the file declares none. */
    val section: String?,
    /** Refs that LOOK alike, closed both ways at parse. */
    val confusableLook: List<String>,
    /** Refs that SOUND alike, closed both ways at parse. */
    val confusableSound: List<String>,
)

/** The target-side example word: what the drill speaks and cuts its gap word from. */
data class AlphabetExample(val slug: String, val text: String, val emoji: String?)

/**
 * One language's alphabet, in authored order.
 *
 * Both confusion axes are closed symmetrically at parse (authoring и → й also makes
 * й → и), so every accessor here is a plain lookup and a distractor draw can never
 * depend on which row the author happened to edit. Homophone groups are DERIVED from
 * byte-identical [AlphabetEntry.ipa] strings rather than authored: two rows that sound
 * the same make a heard question unanswerable, and the drill has to know that without
 * anyone maintaining a second table.
 */
data class Alphabet(
    val language: Language,
    /** Declared groups, in the order they are read; empty where the file states none. */
    val sections: List<AlphabetSection>,
    val entries: List<AlphabetEntry>,
) {
    private val byRef: Map<String, AlphabetEntry> = entries.associateBy { it.ref }
    private val byGlyph: Map<String, List<AlphabetEntry>> = entries.groupBy { it.glyph.lowercase() }
    private val bySection: Map<String, List<AlphabetEntry>> =
        entries.filter { it.section != null }.groupBy { it.section!! }
    private val byIpa: Map<String, List<AlphabetEntry>> =
        entries.mapNotNull { entry -> entry.ipa?.let { it to entry } }
            .groupBy({ (ipa, _) -> ipa }, { (_, entry) -> entry })

    /**
     * The graphemes this language singles out as worth a lesson — every drillable digraph
     * and contextual row, lowercased. A word carrying one is a word whose SPELLING is the
     * hard part, which is what a dictation rung is testing; the plain letters say nothing
     * about difficulty, so they are not here.
     */
    val trickyGlyphs: List<String> = entries
        .filter { it.drill && (it.kind == AlphabetKind.Digraph || it.kind == AlphabetKind.Contextual) }
        .map { it.glyph.lowercase() }
        .distinct()

    fun entry(ref: String): AlphabetEntry? = byRef[ref]

    /**
     * Whether the drill may cut this row's gap from ANY word of the language rather than
     * only from its authored example — true iff the glyph string identifies the row's
     * sound on its own.
     *
     * Three ways it does not, and each bars the sweep: a [AlphabetKind.Contextual] row is
     * position-bound by definition, a declared [AlphabetEntry.context] says so even where
     * the kind does not (es `gu` before e/i — *seguro* carries the letters and not the
     * rule), and a glyph two rows share cannot say which of them a word means. [Letter]
     * rows are asked by their spoken name, so they never gap at all, and [Rule] rows are
     * sheet prose. What survives is the plain digraph — de `ei`, `sch`, es `ll` — where
     * containment IS the sound, and [AlphabetEntry.mine] is the author's escape from a
     * string that lies anyway.
     */
    fun minesExamples(entry: AlphabetEntry): Boolean =
        entry.mine &&
            entry.kind == AlphabetKind.Digraph &&
            entry.context.isEmpty() &&
            byGlyph[entry.glyph.lowercase()]?.size == 1

    /** The rows of one section, in authored order — empty for an id no row claims. */
    fun entries(of: String): List<AlphabetEntry> = bySection[of].orEmpty()

    fun lookAlikes(ref: String): List<AlphabetEntry> = resolve(byRef[ref]?.confusableLook)

    fun soundAlikes(ref: String): List<AlphabetEntry> = resolve(byRef[ref]?.confusableSound)

    /** Entries sharing this one's exact IPA string, itself excluded. */
    fun homophones(ref: String): List<AlphabetEntry> {
        val ipa = byRef[ref]?.ipa ?: return emptyList()
        return byIpa[ipa].orEmpty().filter { it.ref != ref }
    }

    private fun resolve(refs: List<String>?): List<AlphabetEntry> = refs.orEmpty().mapNotNull { byRef[it] }
}

/** The blank a gapped grapheme leaves — ONE marker per grapheme, never one per character. */
internal const val GAP_MARKER = "＿"

/** Typewriter, curly, and the modifier letter alphabet files store canonically. */
private val APOSTROPHES = setOf('\u0027', '\u2019', '\u02bc')

/**
 * Apostrophes folded to U+02BC, length-preserving so a folded index still addresses the
 * original string. Alphabet files store U+02BC canonically while catalog realizations
 * keep whatever their author typed — the class has to be one character before anything
 * looks for a glyph inside a word.
 */
private fun apostropheFolded(text: String): String =
    if (text.none { it in APOSTROPHES }) text
    else text.map { if (it in APOSTROPHES) '\u02bc' else it }.joinToString("")

/**
 * [word] with its FIRST occurrence of [glyph] replaced by [GAP_MARKER], or null when the
 * glyph does not occur. NFC, case-insensitive, apostrophe-class-folded; everything
 * outside the gap stays exactly as authored.
 */
internal fun gapText(word: String, glyph: String): String? {
    val source = nfcNormalized(word)
    val needle = apostropheFolded(nfcNormalized(glyph))
    if (needle.isEmpty()) return null
    val at = apostropheFolded(source).indexOf(needle, ignoreCase = true)
    if (at < 0) return null
    return source.substring(0, at) + GAP_MARKER + source.substring(at + needle.length)
}

/**
 * Whether a realization can carry a gap at all: ONE word, and none of the punctuation a
 * catalog sentence brings with it. A gapped "Feierabend!" or "Nein." reads as a typo on
 * the learner's side of the screen, and a blanked sentence is a different exercise.
 */
internal fun isGappableWord(text: String): Boolean =
    text.isNotEmpty() && text.none { it.isWhitespace() || it in SENTENCE_PUNCTUATION }

private const val SENTENCE_PUNCTUATION = ".,;:!?¿¡…«»\"()/"

/** How often [glyph] starts inside [word], overlaps counted — same folding as [gapText]. */
internal fun glyphOccurrences(word: String, glyph: String): Int {
    val haystack = apostropheFolded(nfcNormalized(word))
    val needle = apostropheFolded(nfcNormalized(glyph))
    if (needle.isEmpty()) return 0
    var count = 0
    var at = haystack.indexOf(needle, 0, ignoreCase = true)
    while (at >= 0) {
        count++
        at = haystack.indexOf(needle, at + 1, ignoreCase = true)
    }
    return count
}

/**
 * The gap word this entry would prompt with, cut from [example]; null where it cannot be
 * cut. EXACTLY one occurrence is required: zero leaves nothing to blank, and with two,
 * first-occurrence blanking gaps the wrong, position-bound instance and teaches the
 * opposite of the entry. Lint reports either as a content error; the drill filters on the
 * same predicate, so a content bug costs a pool entry instead of shipping an
 * unanswerable question.
 */
internal fun AlphabetEntry.gapWord(example: String?): String? {
    if (example == null || glyphOccurrences(example, glyph) != 1) return null
    return gapText(example, glyph)
}
