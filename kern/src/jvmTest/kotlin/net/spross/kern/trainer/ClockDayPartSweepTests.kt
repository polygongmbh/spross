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
 * day closes it, and so does the 24-hour register's hour from thirteen up; this sweep
 * holds every such reading to that.
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
                    val here = Numbers.clock(h, m, language)
                    val twelveOn = Numbers.clock(h + 12, m, language)
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

    /**
     * From thirteen up, and at midnight, the 24-hour register names the half of the day by
     * number — `achtzehn Uhr` is 18:00 and never 06:00 — so the time twelve hours away
     * must refuse it. Below that its hour word is the 12-hour one, open by design.
     */
    @Test
    fun twentyFourHourReadingsCloseTheTwelveHourCycle() {
        for ((language, pack) in trainerPacks) {
            val normalizer = AnswerNormalizer.drill(
                catalog.languages[language] ?: LanguageInfo(language, language, language, "🏳️"),
            )
            val offenders = sortedSetOf<String>()
            for (h in listOf(0) + (13..23)) {
                for (m in 0..59) {
                    val other = Numbers.clock(h + 12, m, language)
                    for (form in pack.clockTwentyFourHour(h, m)) {
                        if (normalizer.evaluate(form, card(language, other.accepted)) == Match.Wrong) continue
                        offenders += "\"$form\" accepted at ${other.prompt}"
                    }
                }
            }
            assertEquals(
                emptyList(), offenders.toList(),
                "$language: 24-hour readings that still answer the other half of the day",
            )
        }
    }

    private fun card(language: String, forms: List<String>): Card {
        val side = Realization(lang = language, text = forms.first(), teaches = forms.drop(1))
        return Card(
            id = "drill", kind = CardKind.Noun, area = "drill", emoji = null, seedIndex = 0,
            components = emptyList(), feminineOf = null,
            source = side, target = side, promptFeminineMarker = false,
        )
    }
}
