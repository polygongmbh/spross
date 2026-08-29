package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * The collision check on the drill grader: a slip the typo budget accepts is refused
 * when it NAMES a different value, and only then — the disjointness rule pinned from
 * both sides ([otherNumber]).
 */
class DrillGradingTests {

    @Test
    fun aBareTwinIsRefusedAndNamed() {
        val match = grade("es", "setenta", listOf("sesenta"))
        assertIs<Match.OtherWord>(match)
        assertEquals("setenta", match.word)
        assertEquals(listOf("70"), match.meanings)
    }

    @Test
    fun aCompoundNamingAnotherValueIsRefusedWhole() {
        val match = grade("es", "ciento setenta y ocho", listOf("ciento sesenta y ocho"))
        assertIs<Match.OtherWord>(match)
        assertEquals(listOf("178"), match.meanings)
    }

    @Test
    fun theSpaceTwinIsRefusedByTheWholeAnswerProbe() {
        // "un décimo" (1/10) vs "undécimo" (11th): different word counts, so only the
        // whole-answer probe can see the pair.
        val match = grade("es", "un décimo", listOf("undécimo"))
        assertIs<Match.OtherWord>(match)
        assertEquals(listOf("1/10"), match.meanings)
    }

    @Test
    fun aTwinInsideAWrapperIsRefusedPositionally() {
        // The negative wrapper is not indexed, so the whole answer names nothing and
        // the differing word must carry the evidence alone.
        val match = grade("sw", "hasi nane", listOf("hasi nne"))
        assertIs<Match.OtherWord>(match)
        assertEquals("nane", match.word)
        assertEquals(listOf("8"), match.meanings)
    }

    @Test
    fun aFumbleNamingNothingStaysATypo() {
        assertIs<Match.Typo>(grade("es", "sesemta", listOf("sesenta")))
    }

    @Test
    fun anAcceptedSpellingStaysExactBeforeTheCheckCanRun() {
        // Both twins accepted: the Exact arm wins, so the check never sees the pair.
        assertEquals(Match.Exact, grade("es", "setenta", listOf("sesenta", "setenta")))
    }

    @Test
    fun aValuedWordAgainstAnUnvaluedOneNeverFires() {
        // "dos" names 2 but "los" names nothing: one-sided evidence is a slip, not
        // another number — the disjointness rule needs BOTH sides valued.
        assertIs<Match.Typo>(grade("es", "dos gatos", listOf("los gatos")))
    }

    @Test
    fun aReversedDigitsAnswerIsUntouched() {
        // Digits grade exact-only already; the check must not resurrect anything.
        assertEquals(Match.Wrong, grade("es", "168", listOf("178")))
    }

    @Test
    fun theClockQuarterRefusesTheCardinalInItsPlace() {
        val match = grade("es", "son las cuatro y cuatro", listOf("son las cuatro y cuarto"))
        assertIs<Match.OtherWord>(match)
        assertEquals("cuatro", match.word)
        assertEquals(listOf("4"), match.meanings)
    }

    private fun grade(language: String, input: String, accepted: List<String>): Match {
        val info = Fixture.catalog().languages[language]
            ?: LanguageInfo(language, language, language, "🏳️")
        val normalizer = AnswerNormalizer.drill(info)
        return gradeDrillAnswer(
            input = input,
            accepted = accepted,
            display = accepted.first(),
            language = language,
            cardId = "drill",
            normalizer = normalizer,
            index = NumberReadingIndex(language, normalizer),
        )
    }
}
