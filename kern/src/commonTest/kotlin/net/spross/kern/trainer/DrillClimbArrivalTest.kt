package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.CountryName
import net.spross.kern.catalog.LanguageName
import net.spross.kern.catalog.NationalityName

/**
 * Climbing PAST a spent Sprosse arrives at the one above it just as a promotion does
 * ([DrillLadder.leadsWithAdded]): the draw it makes there is the arriving draw, seed for seed.
 */
class DrillClimbArrivalTest {

    private fun country(slug: String, language: String, source: String, target: String) = AtlasCountryEntry(
        slug = slug,
        flag = "🏳",
        tier = 1,
        languages = listOf(language),
        source = CountryName(text = source, nationality = NationalityName("$source-person")),
        target = CountryName(text = target, nationality = NationalityName("$target-person")),
    )

    private fun language(code: String, source: String, target: String) = AtlasLanguageEntry(
        code = code,
        tier = 1,
        source = LanguageName(name = source, inForm = "in $source"),
        target = LanguageName(name = target, inForm = "in $target"),
    )

    private val atlas = CountryDrillContent(
        source = "de",
        target = "sw",
        countries = listOf(
            country("homeland", "de", "Deutschland", "Ujerumani"),
            country("islands", "sw", "Kenia", "Kenya"),
        ),
        languages = listOf(
            language("de", "Deutsch", "Kijerumani"),
            language("sw", "Suaheli", "Kiswahili"),
            language("fr", "Französisch", "Kifaransa"),
        ),
    )

    @Test
    fun theAtlasArrivesAtTheSprosseAboveASpentOne() {
        val spent = CountryDrill.tasks(atlas, 1).map { DrillSolved.key(it) }.toSet()
        repeat(16) { seed ->
            val climbed = CountryDrill.draw(atlas, 1, false, null, spent, Random(seed))
            val promoted = CountryDrill.sample(atlas, 2, false, null, spent, Random(seed), arriving = true)
            assertEquals(2, climbed.sprosse)
            assertEquals(promoted, climbed.task, "seed $seed")
        }
    }

    @Test
    fun theCalendarArrivesAtTheSprosseAboveASpentOne() {
        val german = DateDrillFixture.germanContent
        val namesOut = ((0..6).map { "${DateTaskKind.Weekday}:$it" } +
            (0..11).map { "${DateTaskKind.Month}:$it" }).toSet()
        repeat(16) { seed ->
            val climbed = DateDrill.draw(german, 3, false, null, namesOut, Random(seed))
            // The spent Sprosse spends its draws first, then the one above draws as arrived.
            val rng = Random(seed)
            DateDrill.sample(german, 3, false, null, namesOut, rng)
            val promoted = DateDrill.sample(german, 4, false, null, namesOut, rng, arriving = true)
            assertEquals(4, climbed.sprosse)
            assertEquals(promoted, climbed.task, "seed $seed")
        }
    }
}
