package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * Every authored clock is a 12-hour cycle the language leaves open — "quarter to five"
 * IS the right answer to 04:45 and to 16:45 alike. A reading that names the part of the
 * day is the one thing that closes it, and this sweep holds every such reading to that.
 */
class ClockDayPartSweepTests {

    private val catalog = Fixture.catalog()

    /**
     * The day part is what tells 04:45 from 16:45, so a reading carrying one must be
     * refused for the time twelve hours away — a crossed mapping would teach the
     * learner that the small hours are the afternoon.
     */
    @Test
    fun dayPartReadingsCloseTheTwelveHourCycle() {
        for ((language, pack) in trainerPacks) {
            val parts = pack.clockDayParts
            val normalizer = AnswerNormalizer.drill(
                catalog.languages[language] ?: LanguageInfo(language, language, language, "🏳️"),
            )
            val offenders = sortedSetOf<String>()
            for (h in 0..11) {
                for (m in 0..59) {
                    val here = Trainer.clock(h, m, language)
                    val twelveOn = Trainer.clock(h + 12, m, language)
                    for ((a, b) in listOf(here to twelveOn, twelveOn to here)) {
                        for (form in a.accepted) {
                            if (parts.none { it in form.lowercase() }) continue
                            if (normalizer.evaluate(form, card(language, b.accepted)) == Match.Wrong) continue
                            offenders += "${a.prompt} \"$form\" accepted at ${b.prompt}"
                        }
                    }
                }
            }
            assertEquals(
                emptyList(), offenders.toList(),
                "$language: day-part readings that still answer the other half of the day " +
                    "(markers: ${parts.sorted()})",
            )
        }
    }

    private fun card(language: String, forms: List<String>): Card {
        val side = Realization(lang = language, text = forms.first(), synonyms = forms.drop(1))
        return Card(
            id = "drill", kind = CardKind.Noun, area = "drill", emoji = null, seedIndex = 0,
            components = emptyList(), feminineOf = null,
            source = side, target = side, promptFeminineMarker = false,
        )
    }
}
