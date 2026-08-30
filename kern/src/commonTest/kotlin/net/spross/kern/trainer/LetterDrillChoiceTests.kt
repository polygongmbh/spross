package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.AlphabetParser

/** What may stand on a choice tile — the drill's difficulty knob and its one leak risk. */
class LetterDrillChoiceTests {
    private val alphabet = LetterDrillFixture.alphabet

    private fun tasks(level: Int, seeds: IntRange = 1..200): List<LetterDrillTask> = seeds.map {
        assertNotNull(
            LetterDrill.sample(
                alphabet, LetterDrillFixture.example, level, LetterDrillFixture.allRefs,
                null, null, emptySet(), Random(it),
            ),
        )
    }

    // A tile is a string the learner reads, so every set here is keyed by GLYPH: de writes
    // `ch` three times, and a rule stated over refs would let one row put back what
    // another row's exclusion took away.
    private fun closureGlyphs(ref: String): Set<String> =
        (alphabet.lookAlikes(ref) + alphabet.soundAlikes(ref)).map { it.glyph }.toSet()

    private fun homophoneGlyphs(ref: String): Set<String> = alphabet.homophones(ref).map { it.glyph }.toSet()

    /** Glyphs that could stand beside this answer at all: not its own, not prose. */
    private fun eligibleGlyphs(answer: AlphabetEntry): List<String> = alphabet.entries
        .filter { it.kind != AlphabetKind.Rule }
        .map { it.glyph }
        .distinct()
        .filter { !it.equals(answer.glyph, ignoreCase = true) }

    @Test
    fun everyChoiceQuestionOffersFourDistinctTilesWithOneAnswer() {
        for (level in 1..5) {
            for (task in tasks(level)) {
                val tiles = assertNotNull(task.choices, "level $level: a choice stage needs tiles")
                assertEquals(LetterDrill.CHOICE_COUNT, tiles.size, "level $level, ${task.answerRef}: $tiles")
                assertEquals(tiles.distinct(), tiles, "a tile may never repeat: $tiles")
                assertEquals(1, tiles.count { it == task.display }, "the answer sits once: $tiles")
            }
        }
    }

    @Test
    fun proseRowsAndSameGlyphSiblingsNeverReachATile() {
        for (level in 1..5) {
            for (task in tasks(level)) {
                val answer = LetterDrillFixture.entry(task.answerRef)
                val tiles = task.choices.orEmpty()
                // A rule row on a 44 pt tile reads as nonsense and leaks by elimination.
                assertFalse("b d g" in tiles, "a rule row reached a tile: $tiles")
                // The two `ch` rows are one tile; a question can never offer its own glyph twice.
                assertEquals(
                    1,
                    tiles.count { it.equals(answer.glyph, ignoreCase = true) },
                    "${task.answerRef}: the answer glyph is on two tiles — $tiles",
                )
            }
        }
    }

    @Test
    fun silentGraphemesStayEligibleAsTiles() {
        // `h-length` is drill:false — never asked, but telling it apart is a real skill.
        val tiles = (1..5).flatMap { tasks(it) }.flatMap { it.choices.orEmpty() }.toSet()
        assertTrue("h" in tiles, "a silent grapheme must remain a distractor: $tiles")
    }

    @Test
    fun theEasyRungStaysOffBothConfusionAxes() {
        for (level in 1..2) {
            for (task in tasks(level)) {
                val answer = LetterDrillFixture.entry(task.answerRef)
                val near = closureGlyphs(answer.ref) + homophoneGlyphs(answer.ref)
                val distractors = task.choices.orEmpty() - task.display
                assertTrue(
                    distractors.none { it in near },
                    "level $level, ${task.answerRef}: a confusable slipped into the easy rung — $distractors",
                )
            }
        }
    }

    @Test
    fun theConfusableRungDrawsOneThenTwoThenThree() {
        for (level in 3..5) {
            val wanted = level - 2
            for (task in tasks(level)) {
                val answer = LetterDrillFixture.entry(task.answerRef)
                val near = nearGlyphs(answer, task.gapText)
                val drawn = (task.choices.orEmpty() - task.display).count { it in near }
                assertEquals(
                    minOf(wanted, near.size),
                    drawn,
                    "level $level, ${task.answerRef}: drew $drawn of $near in ${task.choices}",
                )
            }
        }
    }

    @Test
    fun homophonesAreKeptOffANameQuestionAndPreferredInAGapWord() {
        for (level in 1..5) {
            for (task in tasks(level)) {
                if (task.gapText != null) continue
                val homophones = homophoneGlyphs(task.answerRef)
                assertTrue(
                    task.choices.orEmpty().none { it in homophones },
                    "${task.answerRef}: ${task.choices} offers a tile that sounds identical",
                )
            }
        }
        // `v` and `f` share an IPA string, so a spoken NAME cannot decide between them.
        val vau = tasks(5).filter { it.answerRef == "v" }
        assertTrue(vau.isNotEmpty(), "the fixture must ask about v")
        assertTrue(vau.none { "f" in it.choices.orEmpty() })

        // `ß` and `ss` share one too — but the WORD's spelling decides, so there they are
        // the sharpest question the drill has, and the confusable rung always offers it.
        val sharp = tasks(3).filter { it.answerRef == "ß" }
        assertTrue(sharp.isNotEmpty(), "the fixture must ask about ß")
        assertTrue(sharp.all { "ss" in it.choices.orEmpty() }, "the homophone belongs on the gap question")
    }

    @Test
    fun typedStagesCarryNoTiles() {
        for (level in 6..7) {
            for (task in tasks(level, seeds = 1..40)) {
                assertEquals(LetterStage.Typed, task.stage)
                assertNull(task.choices)
            }
        }
    }

    @Test
    fun aTinyAlphabetFallsBackRatherThanAskOneTile() {
        // Three rows, two of them homophones: keeping `f` off `v`'s question would leave a
        // single distractor, and a two-tile coin flip is worse than an imperfect one.
        val tiny = AlphabetParser.parse(
            "alphabet/zz.json",
            """
            { "entries": [
              { "glyph": "v", "name": "vau", "ipa": "f", "hints": { "en": "one" } },
              { "glyph": "f", "name": "ef", "ipa": "f", "hints": { "en": "two" } },
              { "glyph": "x", "name": "iks", "ipa": "ks", "hints": { "en": "three" } }
            ] }
            """.trimIndent(),
            "zz",
            setOf("zz", "en"),
        )
        val task = assertNotNull(
            LetterDrill.sample(tiny, { emptyList() }, 1, listOf("v"), null, null, emptySet(), Random(7)),
        )
        assertEquals(3, task.choices?.size)
        assertTrue("f" in task.choices.orEmpty(), "the last resort is any non-answer glyph")
    }

    /** The tiles the confusable draw may take: closure, plus homophones where a gap decides. */
    private fun nearGlyphs(answer: AlphabetEntry, gapText: String?): Set<String> {
        val homophones = homophoneGlyphs(answer.ref)
        val closure = closureGlyphs(answer.ref)
        return eligibleGlyphs(answer)
            .filter { gapText != null || it !in homophones }
            .filter { it in closure || (gapText != null && it in homophones) }
            .toSet()
    }
}
