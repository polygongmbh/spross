package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Language

/**
 * The drill run against the SHIPPING alphabets — rules only, never a pinned draw. What a
 * seeded sequence produces over real content is an authoring fact (a native-speaker sweep
 * is scheduled) and a device fact (which voices are installed decide the promptable set),
 * so the goldens live on the synthetic fixture and these assertions state invariants that
 * must survive every content edit.
 */
class RealCatalogLetterDrillTest {
    private val catalog get() = RealCatalog.catalog
    private val languages = listOf("uk", "de")

    /** §5.1 with every voice installed — the widest set the app could ever hand in. */
    private fun promptableRefs(lang: Language, alphabet: Alphabet): List<String> =
        alphabet.entries.filter { entry ->
            if (!entry.drill || entry.kind == AlphabetKind.Rule) {
                false
            } else if (entry.kind == AlphabetKind.Letter) {
                entry.name != null || catalog.letterRecordingPath(lang, entry.glyph) != null
            } else {
                example(lang)(entry) != null
            }
        }.map { it.ref }

    private fun example(lang: Language): (AlphabetEntry) -> LetterDrill.AlphabetExampleWord? = { entry ->
        catalog.alphabetExample(entry, lang)
            ?.let { LetterDrill.AlphabetExampleWord(it.text, it.slug) }
            ?: entry.exampleText?.let { LetterDrill.AlphabetExampleWord(it, null) }
    }

    private fun draws(lang: Language, level: Int, count: Int = 120): List<LetterDrillTask> {
        val alphabet = assertNotNull(catalog.alphabet(lang), "no $lang alphabet is authored")
        val refs = promptableRefs(lang, alphabet)
        assertTrue(refs.size >= 5, "$lang: only ${refs.size} promptable entries")
        val rng = Random(level * 1000 + lang.hashCode())
        var avoid: String? = null
        return (1..count).map {
            LetterDrill.sample(alphabet, example(lang), level, refs, avoid, rng).also { avoid = it.answerRef }
        }
    }

    @Test
    fun everyQuestionAsksSomethingTheAppOffered() {
        for (lang in languages) {
            val alphabet = assertNotNull(catalog.alphabet(lang))
            val offered = promptableRefs(lang, alphabet).toSet()
            for (level in 1..LetterDrill.MAX_LEVEL_WITHOUT_DICTATION) {
                for (task in draws(lang, level)) {
                    val entry = assertNotNull(alphabet.entry(task.answerRef), "$lang: unknown ref")
                    assertTrue(task.answerRef in offered, "$lang: asked ${task.answerRef} unbidden")
                    assertTrue(entry.drill, "$lang: ${entry.ref} is opted out of the drill")
                    assertFalse(entry.kind == AlphabetKind.Rule, "$lang: a prose row was prompted")
                    assertTrue(task.promptText.isNotBlank(), "$lang: ${entry.ref} would be spoken as nothing")
                    if (task.promptKind == LetterPromptKind.Name) {
                        assertEquals(entry.glyph.lowercase(), task.promptGlyph)
                    }
                    assertEquals(listOf(entry.glyph), task.accepted)
                }
            }
        }
    }

    @Test
    fun everyGapBlanksExactlyOneGrapheme() {
        for (lang in languages) {
            for (level in 1..LetterDrill.MAX_LEVEL_WITHOUT_DICTATION) {
                for (task in draws(lang, level)) {
                    val gap = task.gapText ?: continue
                    assertEquals(1, gap.count { it == '＿' }, "$lang: ${task.answerRef} gapped \"$gap\"")
                    // The prompt is the whole word; only the screen carries the blank.
                    assertFalse('＿' in task.promptText)
                }
            }
        }
    }

    @Test
    fun noTileEverLeaksOrRepeatsTheAnswer() {
        for (lang in languages) {
            val alphabet = assertNotNull(catalog.alphabet(lang))
            val prose = alphabet.entries.filter { it.kind == AlphabetKind.Rule }.map { it.glyph }.toSet()
            for (level in 1..5) {
                for (task in draws(lang, level)) {
                    val tiles = assertNotNull(task.choices, "$lang level $level: a choice stage needs tiles")
                    assertEquals(tiles.distinct(), tiles, "$lang: repeated tile in $tiles")
                    assertEquals(1, tiles.count { it == task.display }, "$lang: $tiles")
                    assertTrue(tiles.size >= 3, "$lang: ${task.answerRef} got $tiles")
                    assertTrue(tiles.none { it in prose }, "$lang: a prose row reached $tiles")
                    // Same-glyph siblings (the three de `ch` rows) can never share a question.
                    assertEquals(
                        1,
                        tiles.count { it.equals(task.display, ignoreCase = true) },
                        "$lang: ${task.answerRef} offers its own glyph twice — $tiles",
                    )
                }
            }
        }
    }

    @Test
    fun aHeardNameNeverOffersTwoTilesThatSoundAlike() {
        for (lang in languages) {
            val alphabet = assertNotNull(catalog.alphabet(lang))
            for (level in 1..5) {
                for (task in draws(lang, level)) {
                    if (task.gapText != null) continue
                    val identical = alphabet.homophones(task.answerRef).map { it.glyph }.toSet()
                    assertTrue(
                        task.choices.orEmpty().none { it in identical },
                        "$lang: ${task.answerRef} is unanswerable against ${task.choices}",
                    )
                }
            }
        }
    }

    @Test
    fun theConfusableRungsDrawWhatTheyPromise() {
        for (lang in languages) {
            val alphabet = assertNotNull(catalog.alphabet(lang))
            for (level in 3..5) {
                for (task in draws(lang, level)) {
                    val near = nearGlyphs(alphabet, task)
                    val drawn = (task.choices.orEmpty() - task.display).count { it in near }
                    assertEquals(
                        minOf(level - 2, near.size),
                        drawn,
                        "$lang level $level: ${task.answerRef} drew $drawn of $near",
                    )
                }
            }
        }
    }

    /**
     * The confusable draw's own candidates: the closed axes, plus homophones in a gap
     * word. Keyed by GLYPH — de authors `ch` three times and `v` twice, so a set of refs
     * would let one row put a tile back that another row's rule had just excluded.
     */
    private fun nearGlyphs(alphabet: Alphabet, task: LetterDrillTask): Set<String> {
        val answer = assertNotNull(alphabet.entry(task.answerRef))
        val homophones = alphabet.homophones(answer.ref).map { it.glyph }.toSet()
        val closure = (alphabet.lookAlikes(answer.ref) + alphabet.soundAlikes(answer.ref))
            .map { it.glyph }.toSet()
        return alphabet.entries
            .filter { it.kind != AlphabetKind.Rule }
            .map { it.glyph }
            .distinct()
            .filter { !it.equals(answer.glyph, ignoreCase = true) }
            .filter { task.gapText != null || it !in homophones }
            .filter { it in closure || (task.gapText != null && it in homophones) }
            .toSet()
    }
}
