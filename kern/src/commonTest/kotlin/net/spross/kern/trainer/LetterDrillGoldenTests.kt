package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The whole ladder replayed from one seed, pinned line by line: stage, answer, prompt
 * provenance, tile ORDER (both platforms render kern's shuffle, so a reordering is a
 * cross-platform change) and gap word.
 *
 * Over the SYNTHETIC fixture alphabet, never the shipped one. Real uk/de sampling cannot
 * be pinned: an ordinary authoring edit — and a native-speaker sweep is scheduled — would
 * read as a code regression, and the promptable set is a device fact (which voices are
 * installed), not a value a test may fix.
 */
class LetterDrillGoldenTests {
    private val fixture = LetterDrillFixture

    @Test
    fun theLadderReplaysItsPinnedRun() {
        assertEquals(GOLDEN.trim(), rendered().trim())
    }

    /** Six consecutive questions per level, each avoiding the one before it. */
    private fun rendered(): String = buildString {
        for (level in 1..LetterDrill.MAX_LEVEL_WITHOUT_DICTATION) {
            appendLine("level $level")
            val rng = Random(SEED)
            var avoid: String? = null
            repeat(RUN_LENGTH) {
                val task = assertNotNull(
                    LetterDrill.sample(
                        fixture.alphabet, fixture.example, level, fixture.allRefs,
                        avoid, null, emptySet(), rng,
                    ),
                )
                avoid = task.answerRef
                appendLine(
                    "  ${task.stage} ${task.answerRef} ${task.promptKind} " +
                        "[${task.choices?.joinToString(" ") ?: "-"}] ${task.gapText ?: "-"}",
                )
            }
        }
        for (level in 8..LetterDrill.MAX_LEVEL_WITH_DICTATION) {
            appendLine("level $level")
            val rng = Random(SEED)
            var avoid: String? = null
            // The fixture's schedules are clean, so the weighting on show is the SPELLING
            // half alone: "Buch" carries a tricky glyph and comes up twice as often.
            val cards = fixture.dictationCandidates()
            repeat(RUN_LENGTH) {
                val task = assertNotNull(
                    LetterDrill.sampleDictation(cards, fixture.alphabet, level, avoid, emptySet(), rng),
                )
                avoid = task.answerRef
                appendLine("  ${task.stage} ${task.answerRef} ${task.display}")
            }
        }
    }

    private companion object {
        const val SEED = 42
        const val RUN_LENGTH = 6

        val GOLDEN = """
            level 1
              ChoiceEasy ch-ach PlainText [h n ch ss] Na＿t
              ChoiceEasy ß Word [ß ch n qu] Stra＿e
              ChoiceEasy f Name [ss m qu f] -
              ChoiceEasy ss PlainText [m ss v u] Wa＿er
              ChoiceEasy n Name [qu v ß n] -
              ChoiceEasy m Name [m u f ß] -
            level 2
              ChoiceEasy ch-ach PlainText [h n ch ss] Na＿t
              ChoiceEasy ß Word [ß ch n qu] Stra＿e
              ChoiceEasy f Name [ss m qu f] -
              ChoiceEasy ss PlainText [m ss v u] Wa＿er
              ChoiceEasy n Name [qu v ß n] -
              ChoiceEasy m Name [m u f ß] -
            level 3
              ChoiceConfusable ch-ach PlainText [h n ch ss] Na＿t
              ChoiceConfusable ß Word [ß ch ss m] Stra＿e
              ChoiceConfusable f Name [ss m qu f] -
              ChoiceConfusable ss PlainText [u ss qu ß] Wa＿er
              ChoiceConfusable n Name [v m qu n] -
              ChoiceConfusable m Name [m n ch f] -
            level 4
              ChoiceConfusable ch-ach PlainText [h n ch ss] Na＿t
              ChoiceConfusable ß Word [ß ch ss m] Stra＿e
              ChoiceConfusable f Name [ss m qu f] -
              ChoiceConfusable ss PlainText [u ss qu ß] Wa＿er
              ChoiceConfusable n Name [qu m u n] -
              ChoiceConfusable m Name [m n ch f] -
            level 5
              ChoiceConfusable ch-ach PlainText [h n ch ss] Na＿t
              ChoiceConfusable ß Word [ß ch ss m] Stra＿e
              ChoiceConfusable f Name [ss m qu f] -
              ChoiceConfusable ss PlainText [u ss qu ß] Wa＿er
              ChoiceConfusable n Name [h m u n] -
              ChoiceConfusable m Name [m n ch f] -
            level 6
              Typed ch-ach PlainText [-] Na＿t
              Typed ss PlainText [-] Wa＿er
              Typed ß Word [-] Stra＿e
              Typed n Name [-] -
              Typed ss PlainText [-] Wa＿er
              Typed ch-ich PlainText [-] Li＿t
            level 7
              Typed ch-ach PlainText [-] Na＿t
              Typed ss PlainText [-] Wa＿er
              Typed ß Word [-] Stra＿e
              Typed n Name [-] -
              Typed ss PlainText [-] Wa＿er
              Typed ch-ich PlainText [-] Li＿t
            level 8
              Dictation book Buch
              Dictation ice Eis
              Dictation house Haus
              Dictation book Buch
              Dictation house Haus
              Dictation book Buch
            level 9
              Dictation rainbow Regenbogen
              Dictation rainbow Regenbogen
              Dictation house Haus
              Dictation rainbow Regenbogen
              Dictation sun Sonne
              Dictation book Buch
        """.trimIndent()
    }
}
