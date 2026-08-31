package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.gapWord
import net.spross.kern.model.Card
import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized
import net.spross.kern.session.spokenOnly

/**
 * The letter drill: hear a sound, find the letter — multiple choice, then typing, then
 * transcription of words the learner already holds.
 *
 * Registry-by-file, not by enum: a language has this drill exactly when
 * `catalog/alphabet/<lang>.json` exists, so adding one is dropping a file
 * ([net.spross.kern.catalog.Catalog.alphabet]). [TrainerKind] stays untouched.
 *
 * Everything here is pure and stateless — no schedule is read, no review is booked
 * (transcription is not recall). Sampling takes an injected [Random] like every other
 * generator in this package, so both platforms derive the same run from the same seed and
 * the progression can be pinned in tests rather than described in two UI layers.
 */
object LetterDrill {
    const val MAX_LEVEL_WITH_DICTATION = 9
    const val MAX_LEVEL_WITHOUT_DICTATION = 7

    /** One answer plus up to three distractors; two are tolerated on a tiny alphabet. */
    const val CHOICE_COUNT = 4

    /** Entry pacing stops one rung below transcription — nobody starts by taking dictation. */
    private const val ENTRY_LEVEL_CEILING = 6
    private const val CONSOLIDATED_PER_LEVEL = 12

    /** Consolidated words from which one clean win is enough to move up a rung. */
    private const val CONSOLIDATED_FOR_SHORT_STAGES = 60

    /** Dictation at level 8 asks for short words; the count ignores spaces. */
    private const val SHORT_WORD_LETTERS = 6

    /** Below this many short candidates the level-8 filter is dropped — never draw from one. */
    private const val MIN_SHORT_CANDIDATES = 3

    /** The same floor on the gap word's known-first preference; below it, the whole pool. */
    private const val MIN_KNOWN_CANDIDATES = 3

    /** Ceilings on the three things that make a word worth dictating twice (see [dictationWeight]). */
    private const val TRICKY_CAP = 3
    private const val LAPSE_CAP = 3
    private const val DIFFICULTY_CAP = 2

    /** FSRS difficulty runs 1–10; below its middle a word is not what the rung is for. */
    private const val DIFFICULTY_MIDPOINT = 5.0
    private const val DIFFICULTY_PER_STEP = 2.0

    fun maxLevel(dictationAvailable: Boolean): Int =
        if (dictationAvailable) MAX_LEVEL_WITH_DICTATION else MAX_LEVEL_WITHOUT_DICTATION

    /**
     * Where a learner STARTS, from the words they already hold: 0–11 consolidated → 1,
     * 60+ → 6. The `Growth.newBudget` pacing shape (a step per bucket, a hard ceiling) —
     * someone with a vocabulary should not spell out `em` four times before the drill
     * gets interesting, and someone without one should not be dropped into typing.
     */
    fun entryLevel(consolidatedCards: Int): Int =
        minOf(ENTRY_LEVEL_CEILING, 1 + maxOf(0, consolidatedCards) / CONSOLIDATED_PER_LEVEL)

    /**
     * How LONG a rung is — the second half of the same pacing rule. A consolidated
     * vocabulary earns each level in one clean win; below that the classic two apply, so
     * a beginner gets the repetition and nobody else gets the drag.
     */
    fun winsToAdvance(consolidatedCards: Int): Int = if (consolidatedCards >= CONSOLIDATED_FOR_SHORT_STAGES) 1 else 2

    /** 1–2 easy tiles, 3–5 confusable tiles, 6–7 typing, 8–9 dictation. */
    fun stageFor(level: Int): LetterStage = when (level.coerceIn(1, MAX_LEVEL_WITH_DICTATION)) {
        1, 2 -> LetterStage.ChoiceEasy
        3, 4, 5 -> LetterStage.ChoiceConfusable
        6, 7 -> LetterStage.Typed
        else -> LetterStage.Dictation
    }

    /**
     * An example word WITH its provenance: [slug] is null exactly when the text came from
     * the entry's `exampleText` escape hatch rather than a concept the target language
     * realizes. That distinction is the whole point of the type — it is what keeps a
     * slug's recording from playing over a different word on screen.
     *
     * [known] is the learner's side of it: true where the box already holds the word, so
     * the draw can favour words that mean something to them (see [sample]).
     */
    data class AlphabetExampleWord(val text: String, val slug: String?, val known: Boolean = false)

    /**
     * One letter-stage question. [promptableRefs] is the app's own list (what the device
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
     * dropped from the pool too, and a stage with nothing left outside them samples null —
     * a spent rung the run climbs past rather than asks again.
     */
    fun sample(
        alphabet: Alphabet,
        targetExamples: (AlphabetEntry) -> List<AlphabetExampleWord>,
        level: Int,
        promptableRefs: List<String>,
        avoidRef: String?,
        avoidWord: String?,
        solved: Set<String>,
        rng: Random,
    ): LetterDrillTask? {
        val allowed = promptableRefs.toSet()
        // why: the letter stages stop at 7 — 8 and 9 are dictation, which draws from the
        // box and enters through sampleDictation, never here.
        val stage = stageFor(level.coerceIn(1, MAX_LEVEL_WITHOUT_DICTATION))
        val pool = alphabet.entries
            .filter { it.ref in allowed && it.drill && it.kind != AlphabetKind.Rule }
            .map { entry -> entry to unsolved(gapCandidates(entry, targetExamples), stage, entry, solved) }
            .filter { (entry, words) -> entry.kind == AlphabetKind.Letter || words.isNotEmpty() }
            .filter { (entry, _) -> entry.kind != AlphabetKind.Letter || askableName(entry, stage, solved) }
        if (pool.isEmpty()) return null
        var picked = pool[rng.nextInt(pool.size)]
        if (picked.first.ref == avoidRef) picked = pool[rng.nextInt(pool.size)]
        val (entry, words) = picked
        val prompt = prompt(entry, words, avoidWord, rng)
        return LetterDrillTask(
            stage = stage,
            language = alphabet.language,
            answerRef = entry.ref,
            promptText = prompt.text,
            promptKind = prompt.kind,
            promptSlug = prompt.slug,
            promptGlyph = prompt.glyph,
            choices = LetterDrillChoices.tiles(alphabet, entry, stage, level, prompt.gap, rng),
            gapText = prompt.gap,
            accepted = listOf(entry.glyph),
            display = entry.glyph,
            gloss = prompt.gloss,
        )
    }

    /**
     * A dictation candidate: the card, plus the two things about it the drill weighs that
     * a [Card] cannot carry. [difficulty] is FSRS's own 1–10 (0 stands for "the caller has
     * no schedule for this", which weighs nothing), [lapses] the times it has been
     * forgotten. Both are read from `CardScheduling`, never re-derived here.
     */
    data class DictationCandidate(
        val card: Card,
        val difficulty: Double = 0.0,
        val lapses: Int = 0,
    )

    /**
     * One dictation question. [candidates] arrive filtered to consolidated, speakable box
     * cards; kern drops anything with a space of its own — a transcription task is
     * one word, whatever the caller believes.
     *
     * Level 8 asks for short words. If fewer than [MIN_SHORT_CANDIDATES] survive that
     * filter the whole list is used instead: a drill that always dictates the same two
     * words is worse than one that occasionally dictates a long one.
     *
     * Inside whatever pool survives, the draw is WEIGHTED by [dictationWeight] — a rung
     * spent on words already spelt right is a rung spent on nothing. [alphabet] is only
     * consulted for the language's own hard graphemes; a language without one dictates
     * fine, it just weighs the spelling half at zero.
     *
     * [solved] is what this run has already transcribed right; null comes back once every
     * candidate is in it, and the run climbs past the rung rather than dictating twice.
     */
    fun sampleDictation(
        candidates: List<DictationCandidate>,
        alphabet: Alphabet?,
        level: Int,
        avoidCardId: String?,
        solved: Set<String>,
        rng: Random,
    ): LetterDrillTask? {
        val words = candidates.filter {
            ' ' !in it.card.target.text &&
                DrillSolved.letterKey(LetterStage.Dictation, it.card.id, it.card.target.text) !in solved
        }
        if (words.isEmpty()) return null
        val short = words.filter { it.card.target.text.count { ch -> ch != ' ' } <= SHORT_WORD_LETTERS }
        val pool = if (level <= 8 && short.size >= MIN_SHORT_CANDIDATES) short else words
        val tricky = alphabet?.trickyGlyphs.orEmpty()
        val weights = pool.map { dictationWeight(it, tricky) }
        var card = weighted(pool, weights, rng).card
        if (card.id == avoidCardId) card = weighted(pool, weights, rng).card
        return LetterDrillTask(
            stage = LetterStage.Dictation,
            language = card.target.lang,
            answerRef = card.id,
            promptText = card.target.text,
            promptKind = LetterPromptKind.Word,
            promptSlug = card.id,
            promptGlyph = null,
            choices = null,
            gapText = null,
            // why: transcription accepts what was SPOKEN and nothing else — a synonym
            // would credit a word the learner never heard.
            accepted = listOf(card.target.text),
            display = card.target.text,
            gloss = card.source.text,
        )
    }

    /**
     * How much of the dictation draw a candidate is worth. One is the floor every word
     * keeps — nothing is ever excluded, only out-drawn — and three things add to it:
     *
     * the SPELLING (how many of the language's own hard graphemes the word carries, which
     * is what a transcription actually tests), the LAPSES (words this learner has
     * forgotten before), and FSRS's DIFFICULTY above the midpoint. Each is capped, so a
     * single leech cannot take the rung over, and every term is zero on a short clean word
     * — which is exactly when the draw stays uniform.
     */
    fun dictationWeight(candidate: DictationCandidate, trickyGlyphs: List<String>): Int {
        val word = candidate.card.target.text.lowercase()
        val spelling = minOf(TRICKY_CAP, trickyGlyphs.count { it in word })
        val forgotten = minOf(LAPSE_CAP, maxOf(0, candidate.lapses))
        val hard = minOf(
            DIFFICULTY_CAP,
            ((candidate.difficulty - DIFFICULTY_MIDPOINT) / DIFFICULTY_PER_STEP).toInt().coerceAtLeast(0),
        )
        return 1 + spelling + forgotten + hard
    }

    /** Cumulative draw over [weights]; identical to a uniform pick where they all match. */
    private fun <T> weighted(pool: List<T>, weights: List<Int>, rng: Random): T {
        val total = weights.sum()
        var roll = rng.nextInt(total)
        for ((index, weight) in weights.withIndex()) {
            roll -= weight
            if (roll < 0) return pool[index]
        }
        return pool.last()
    }

    /**
     * The card a dictation answer is graded against — [spokenOnly] over what the task
     * actually played. The rule is shared with sound-prompted review, which asks by ear
     * for the same reason and must not credit a word the learner never heard.
     */
    fun dictationGradingCard(card: Card, task: LetterDrillTask): Card =
        spokenOnly(card, task.accepted.firstOrNull() ?: card.target.text)

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

    /** The gap words this run has not already spelt right at this stage. */
    private fun unsolved(
        words: List<AlphabetExampleWord>,
        stage: LetterStage,
        entry: AlphabetEntry,
        solved: Set<String>,
    ): List<AlphabetExampleWord> =
        words.filter { DrillSolved.letterKey(stage, entry.ref, it.text) !in solved }

    /** A letter is asked by its NAME, so that one prompt is the whole of what it can offer. */
    private fun askableName(entry: AlphabetEntry, stage: LetterStage, solved: Set<String>): Boolean {
        val name = entry.name ?: return false
        return DrillSolved.letterKey(stage, entry.ref, name) !in solved
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
     * beginner's three known words would come round all evening. [avoidWord] is resampled
     * once, the same courtesy the entry draw gets.
     */
    private fun draw(
        words: List<AlphabetExampleWord>,
        avoidWord: String?,
        rng: Random,
    ): AlphabetExampleWord {
        val known = words.filter { it.known }
        val pool = if (known.size >= MIN_KNOWN_CANDIDATES) known else words
        // why: a row with one word has nothing to draw — spending randomness on it would
        // shift every later draw in the run for a choice that was never made.
        if (pool.size == 1) return pool.single()
        var word = pool[rng.nextInt(pool.size)]
        if (word.text == avoidWord) word = pool[rng.nextInt(pool.size)]
        return word
    }

    /**
     * Comparison form for a typed glyph. The apostrophe class is folded to U+02BC — the
     * alphabet files store that one canonically while a keyboard offers U+0027 and
     * autocorrect offers U+2019, and all three mean the same letter.
     */
    private fun graded(text: String): String = nfcNormalized(text).trim().lowercase()
        .map { if (it in APOSTROPHES) '\u02bc' else it }
        .joinToString("")

    /** Typewriter, curly, and the modifier letter the alphabet files store canonically. */
    private val APOSTROPHES = setOf('\u0027', '\u2019', '\u02bc')
}
