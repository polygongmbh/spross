package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind

/**
 * Which glyphs sit on the choice tiles — the difficulty knob of the multiple-choice
 * stages, kept out of [LetterDrill] because it is the one part with a shape of its own.
 *
 * Everything here works in GLYPHS, not entry refs. A tile is a string the learner reads:
 * de authors three `ch` rows and two `v` rows, and a rule that excluded one ref while
 * another ref put the same glyph back on the grid would exclude nothing at all.
 *
 * Two rules do the work. Easy levels draw fillers from OUTSIDE both confusion axes, so a
 * first question is decided by hearing the letter rather than by discriminating two of
 * them. Confusable levels draw 1, 2, then 3 of the four tiles from the entry's own
 * look-alikes and sound-alikes, which is where the drill stops being recognition and
 * starts being discrimination.
 */
internal object LetterDrillChoices {

    /** The answer plus up to three distractors. */
    private const val DISTRACTORS = LetterDrill.CHOICE_COUNT - 1

    /** Below three tiles the question stops being a choice at all. */
    private const val MIN_DISTRACTORS = 2

    /**
     * Tiles in render order for a choice stage, null for the typed ones. [gapText] decides
     * the homophone rule: two entries with the same IPA sound identical, so offering both
     * against a NAME prompt is unanswerable and they are excluded — but in a gap word the
     * spelling is what decides, which makes them the sharpest distractor there is.
     */
    fun tiles(
        alphabet: Alphabet,
        answer: AlphabetEntry,
        stage: LetterStage,
        level: Int,
        gapText: String?,
        rng: Random,
    ): List<String>? {
        if (stage != LetterStage.ChoiceEasy && stage != LetterStage.ChoiceConfusable) return null
        val answerGlyph = key(answer.glyph)
        val homophones = alphabet.homophones(answer.ref).mapTo(mutableSetOf()) { key(it.glyph) }
        val closure = (alphabet.lookAlikes(answer.ref) + alphabet.soundAlikes(answer.ref))
            .mapTo(mutableSetOf()) { key(it.glyph) }
        // why: a rule row is prose ("б д з ж г") — on a 44 pt tile it reads as nonsense and
        // leaks the answer by elimination. A silent grapheme (drill:false) is a real letter
        // and stays eligible: telling ь from й is exactly the visual skill being drilled.
        val eligible = alphabet.entries
            .filter { it.kind != AlphabetKind.Rule }
            .map { it.glyph }
            .distinctBy(::key)
            .filter { key(it) != answerGlyph }
        val answerable = if (gapText == null) eligible.filter { key(it) !in homophones } else eligible
        val confusable = answerable.filter { key(it) in closure || key(it) in homophones }
        val fillers = answerable.filter { key(it) !in closure && key(it) !in homophones }

        val chosen = LinkedHashSet<String>()
        if (stage == LetterStage.ChoiceConfusable) {
            draw(chosen, confusable, (level - 2).coerceIn(1, DISTRACTORS), rng)
        }
        draw(chosen, fillers, DISTRACTORS - chosen.size, rng)
        // why: a degenerate alphabet (or an entry whose whole file looks like it) can leave
        // the pools empty — then any non-answer glyph beats a one-tile question.
        if (chosen.size < MIN_DISTRACTORS) draw(chosen, eligible, MIN_DISTRACTORS - chosen.size, rng)
        return (listOf(answer.glyph) + chosen).shuffled(rng)
    }

    /** Adds up to [count] glyphs drawn uniformly without replacement. */
    private fun draw(into: MutableSet<String>, from: List<String>, count: Int, rng: Random) {
        if (count <= 0) return
        val remaining = from.filter { it !in into }.toMutableList()
        repeat(minOf(count, remaining.size)) { into += remaining.removeAt(rng.nextInt(remaining.size)) }
    }

    /** Tiles compare as the learner reads them; case is not a distinction on a glyph tile. */
    private fun key(glyph: String): String = glyph.lowercase()
}
