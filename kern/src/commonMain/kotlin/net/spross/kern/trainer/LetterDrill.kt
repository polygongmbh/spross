package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.gapWord
import net.spross.kern.model.Card
import net.spross.kern.model.Language
import net.spross.kern.model.apostropheFolded
import net.spross.kern.model.caseFolded

/**
 * The letter drill: hear a sound, find the letter — multiple choice, then typing, then
 * transcription of words the learner already holds.
 *
 * Registry-by-file, not by enum: a language has this drill exactly when
 * `catalog/alphabet/<lang>.json` exists, so adding one is dropping a file
 * ([net.spross.kern.catalog.Catalog.alphabet]). [NumbersReading] stays untouched.
 *
 * Everything here is pure and stateless — no schedule is read, no review is booked
 * (transcription is not recall). Sampling takes an injected [Random] like every other
 * generator in this package, so both platforms derive the same run from the same seed and
 * the progression can be pinned in tests rather than described in two UI layers.
 */
object LetterDrill {
    const val MAX_SPROSSE_WITH_DICTATION = 9
    const val MAX_SPROSSE_WITHOUT_DICTATION = 7

    /** One answer plus up to three distractors; two are tolerated on a tiny alphabet. */
    const val CHOICE_COUNT = 4

    /**
     * Three clean wins a Sprosse ([DrillRamp.USUAL_WINS]): each Sprosse asks from the whole
     * alphabet, so a climb should see more than a couple of its rows.
     */
    const val WINS_TO_ADVANCE = DrillRamp.USUAL_WINS

    /** The same floor on the gap word's arrived-first preference; below it, the whole pool. */
    private const val MIN_ARRIVED_CANDIDATES = 3

    fun maxSprosse(dictationAvailable: Boolean): Int =
        if (dictationAvailable) MAX_SPROSSE_WITH_DICTATION else MAX_SPROSSE_WITHOUT_DICTATION

    /** The Sprossen [format] spans — [formatFor]'s reading turned round. */
    fun sprossen(format: LetterFormat): IntRange = when (format) {
        LetterFormat.ChoiceEasy -> 1..2
        LetterFormat.ChoiceConfusable -> 3..5
        LetterFormat.Typed -> 6..7
        LetterFormat.Dictation -> 8..MAX_SPROSSE_WITH_DICTATION
    }

    /** 1–2 easy tiles, 3–5 confusable tiles, 6–7 typing, 8–9 dictation. */
    fun formatFor(sprosse: Int): LetterFormat = when (sprosse.coerceIn(1, MAX_SPROSSE_WITH_DICTATION)) {
        1, 2 -> LetterFormat.ChoiceEasy
        3, 4, 5 -> LetterFormat.ChoiceConfusable
        6, 7 -> LetterFormat.Typed
        else -> LetterFormat.Dictation
    }

    /**
     * An example word WITH its provenance: [slug] is null exactly when the text came from
     * the entry's `exampleText` escape hatch rather than a concept the target language
     * realizes. That distinction is the whole point of the type — it is what keeps a
     * slug's recording from playing over a different word on screen.
     *
     * [arrived] is the learner's side of it: true where the word has arrived in the box, so
     * the draw can favor words that mean something to them (see [sample]).
     */
    data class AlphabetExampleWord(val text: String, val slug: String?, val arrived: Boolean = false)

    /**
     * One letter-format question. [promptableRefs] is the app's own list (what the device
     * can actually speak or play) in file order; [avoidRef] is the previous answer
     * and [avoidWord] the previous gap word, each resampled once so a repeat needs two
     * unlucky draws rather than one.
     *
     * [targetExamples] hands over EVERY word a row could gap, already narrowed to what
     * this device can say (`Catalog.alphabetExamples` upstream of it). Callers precompute
     * it per run rather than per question — it is a catalog sweep, not a lookup.
     *
     * Entries that cannot be ASKED are dropped defensively — a letter without a name, a
     * gap entry whose examples do not resolve or whose glyph sits in none of them exactly
     * once. Lint makes all three unreachable in shipped content; the filter is what turns
     * an authoring slip into a smaller pool instead of an unanswerable question.
     *
     * [solved] is what this run has already got right ([DrillSolved]): those prompts are
     * dropped from the pool too, and a format with nothing left outside them samples null —
     * a spent Sprosse the run climbs past rather than asks again.
     */
    fun sample(
        alphabet: Alphabet,
        targetExamples: (AlphabetEntry) -> List<AlphabetExampleWord>,
        sprosse: Int,
        promptableRefs: List<String>,
        avoidRef: String?,
        avoidWord: String?,
        solved: Set<String>,
        rng: Random,
    ): LetterDrillTask? {
        val allowed = promptableRefs.toSet()
        // why: the letter formats stop at 7 — 8 and 9 are dictation, which draws from the
        // box and enters through sampleDictation, never here.
        val format = formatFor(sprosse.coerceIn(1, MAX_SPROSSE_WITHOUT_DICTATION))
        val pool = alphabet.entries
            .filter { it.ref in allowed && it.drill && it.kind != AlphabetKind.Rule }
            .map { entry -> entry to unsolved(gapCandidates(entry, targetExamples), format, entry, solved) }
            .filter { (entry, words) -> entry.kind == AlphabetKind.Letter || words.isNotEmpty() }
            .filter { (entry, _) -> entry.kind != AlphabetKind.Letter || askableName(entry, format, solved) }
        if (pool.isEmpty()) return null
        val (entry, words) = DrillLadder.pickAvoiding(pool, rng) { it.first.ref == avoidRef }
        val prompt = prompt(entry, words, avoidWord, rng)
        return LetterDrillTask(
            format = format,
            language = alphabet.language,
            answerRef = entry.ref,
            promptText = prompt.text,
            promptKind = prompt.kind,
            promptSlug = prompt.slug,
            promptGlyph = prompt.glyph,
            choices = LetterDrillChoices.tiles(alphabet, entry, format, sprosse, prompt.gap, rng),
            gapText = prompt.gap,
            accepted = listOf(entry.glyph),
            display = entry.glyph,
            gloss = prompt.gloss,
        )
    }

    /**
     * A dictation candidate: the card, plus what the drill weighs that a [Card] cannot carry.
     * [difficulty] is FSRS's own 1–10 (0 stands for "the caller has no schedule for this",
     * which weighs nothing), read from `CardScheduling`, never re-derived here.
     */
    data class DictationCandidate(
        val card: Card,
        val difficulty: Double = 0.0,
    )

    /** One dictation question, weighted toward the words worth dictating; see [LetterDictation.sample]. */
    fun sampleDictation(
        candidates: List<DictationCandidate>,
        alphabet: Alphabet?,
        sprosse: Int,
        avoidCardId: String?,
        solved: Set<String>,
        rng: Random,
    ): LetterDrillTask? = LetterDictation.sample(candidates, alphabet, sprosse, avoidCardId, solved, rng)

    /** The card a dictation answer is graded against; see [LetterDictation.gradingCard]. */
    fun dictationGradingCard(card: Card, task: LetterDrillTask): Card = LetterDictation.gradingCard(card, task)

    /**
     * Typed-glyph grading: exact after normalization, case-insensitive, no typo budget —
     * a one-glyph answer with a slip allowance grades nothing at all. Multigraphs (`sch`,
     * `rr`) go through the same exact test.
     */
    fun gradeLetter(input: String, task: LetterDrillTask): Boolean {
        val typed = graded(input)
        return typed.isNotEmpty() && task.accepted.any { graded(it) == typed }
    }

    /** The prompt side of a task. */
    private data class Prompt(
        val text: String,
        val kind: LetterPromptKind,
        val slug: String?,
        val glyph: String?,
        val gap: String?,
        val gloss: String?,
    )

    /** The words a row could actually gap — empty for a letter row, which is asked by name. */
    private fun gapCandidates(
        entry: AlphabetEntry,
        examples: (AlphabetEntry) -> List<AlphabetExampleWord>,
    ): List<AlphabetExampleWord> =
        if (entry.kind == AlphabetKind.Letter) emptyList()
        else examples(entry).filter { entry.gapWord(it.text) != null }

    /** The gap words this run has not already spelled right in this format. */
    private fun unsolved(
        words: List<AlphabetExampleWord>,
        format: LetterFormat,
        entry: AlphabetEntry,
        solved: Set<String>,
    ): List<AlphabetExampleWord> =
        words.filter { DrillSolved.letterKey(format, entry.ref, it.text) !in solved }

    /** A letter is asked by its NAME, so that one prompt is the whole of what it can offer. */
    private fun askableName(entry: AlphabetEntry, format: LetterFormat, solved: Set<String>): Boolean {
        val name = entry.name ?: return false
        return DrillSolved.letterKey(format, entry.ref, name) !in solved
    }

    private fun prompt(
        entry: AlphabetEntry,
        words: List<AlphabetExampleWord>,
        avoidWord: String?,
        rng: Random,
    ): Prompt {
        if (entry.kind == AlphabetKind.Letter) {
            // why: the NAME is the speakable unit — «ґе», not ґ (measured 0.39 s against 1.32 s).
            val name = requireNotNull(entry.name) { "letter ${entry.ref} without a name" }
            return Prompt(name, LetterPromptKind.Name, null, entry.glyph.lowercase(), null, null)
        }
        // why: a digraph has no name to speak and a bare synthesized sound is unreliable,
        // so the question becomes the classic gap word — which also makes homophone sets
        // (ß/ss, ll/y) answerable, because the word's spelling is what decides them.
        val word = draw(words, avoidWord, rng)
        return Prompt(
            text = word.text,
            kind = if (word.slug != null) LetterPromptKind.Word else LetterPromptKind.PlainText,
            slug = word.slug,
            glyph = null,
            gap = requireNotNull(entry.gapWord(word.text)) { "ungappable ${entry.ref}: ${word.text}" },
            gloss = word.text,
        )
    }

    /**
     * The gap word itself: words the learner already holds first, so the drill spells out
     * a vocabulary rather than a word list — but only while enough of them exist, or a
     * beginner's three arrived words would come round all evening. [avoidWord] is resampled
     * once, the same courtesy the entry draw gets.
     */
    private fun draw(
        words: List<AlphabetExampleWord>,
        avoidWord: String?,
        rng: Random,
    ): AlphabetExampleWord {
        val arrived = words.filter { it.arrived }
        val pool = if (arrived.size >= MIN_ARRIVED_CANDIDATES) arrived else words
        // why: a row with one word has nothing to draw — spending randomness on it would
        // shift every later draw in the run for a choice that was never made.
        if (pool.size == 1) return pool.single()
        return DrillLadder.pickAvoiding(pool, rng) { it.text == avoidWord }
    }

    /**
     * Comparison form for a typed glyph. The apostrophe class is folded to U+02BC — the
     * alphabet files store that one canonically while a keyboard offers U+0027 and
     * autocorrect offers U+2019, and all three mean the same letter.
     */
    private fun graded(text: String): String = apostropheFolded(caseFolded(text))
}
