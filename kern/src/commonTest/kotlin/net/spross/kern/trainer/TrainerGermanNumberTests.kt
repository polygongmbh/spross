package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The German cardinal's second spelling: what the generator writes is
 * "einhundert"/"eintausend", what a speaker says is "hundert"/"tausend", and
 * both are the same number — so the drill grades both while the reveal and the
 * reference table keep teaching the written one.
 *
 * Only the LEADING "ein" drops. "eintausendeinhundert" → "tausendeinhundert"
 * keeps its medial one, which no register drops.
 */
class TrainerGermanNumberTests {

    private fun accepted(n: Long) = Trainer.number(n, "de").accepted

    @Test
    fun hundredAndThousandGradeWithoutTheLeadingEin() {
        assertEquals(listOf("einhundert", "hundert"), accepted(100))
        assertEquals(listOf("einhunderteins", "hunderteins"), accepted(101))
        assertEquals(listOf("eintausend", "tausend"), accepted(1000))
        assertEquals(
            listOf("einhunderttausend", "hunderttausend"),
            accepted(100_000),
        )
    }

    @Test
    fun theWrittenFormStaysWhatIsTaught() {
        assertEquals("einhundert", Trainer.number(100, "de").display)
        assertEquals("eintausend", Trainer.number(1000, "de").display)
    }

    @Test
    fun onlyTheLeadingEinDrops() {
        assertEquals(
            listOf("eintausendeinhundert", "tausendeinhundert"),
            accepted(1100),
        )
        // A number that never wrote one has nothing to drop.
        assertEquals(listOf("zweitausendeinhundert"), accepted(2100))
        assertTrue(accepted(1_000_000).all { it == "eine Million" })
    }

    @Test
    fun yearsCarryTheSameTwin() {
        val y1978 = Trainer.year(1978, "de")
        assertEquals("neunzehnhundertachtundsiebzig", y1978.display)
        assertTrue("tausendneunhundertachtundsiebzig" in y1978.accepted)
        assertTrue("eintausendneunhundertachtundsiebzig" in y1978.accepted)
    }

    @Test
    fun theDrillDoesNotInventOtherStems() {
        // "zwanzig" is not a reading of 120: only "ein" is optional, nothing else.
        assertFalse(accepted(120).any { it == "zwanzig" })
        assertEquals(listOf("einhundertzwanzig", "hundertzwanzig"), accepted(120))
    }
}
