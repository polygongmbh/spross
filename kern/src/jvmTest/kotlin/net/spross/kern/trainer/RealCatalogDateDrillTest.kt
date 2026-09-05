package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import net.spross.kern.catalog.RealCatalog

/**
 * The CONTENT SPEC for the assembled date, against the SHIPPING calendars — what
 * `docs/date-readings.md` rules a language says, read back off the drill that grades it
 * rather than off the JSON. A reading that changes here is a claim about the language.
 *
 * The day-of-month numeral has its own spec ([TrainerDateReadingTests], on the packs); this
 * one is about the pattern the calendar wraps around it and the names it fills in.
 */
class RealCatalogDateDrillTest {

    private fun content(source: String, target: String) =
        assertNotNull(RealCatalog.catalog.dateDrillContent(source, target), "$source→$target: no calendar")

    /**
     * `Jumanne, tarehe tatu Machi mwaka wa elfu mbili na ishirini na sita` — the year hangs
     * off the date with `mwaka wa` and the reveal teaches it, while the `wa`-less form and
     * the headline register that drops `mwaka` altogether only grade.
     */
    @Test
    fun theSwahiliYearHangsOffTheDateWithMwakaWa() {
        val sw = content("de", "sw")
        val task = DateDrillTasks.fullDateWithYear(sw, 3, 2, 2026, weekdayFree = false)
        assertEquals("Jumanne, tarehe tatu Machi mwaka wa elfu mbili na ishirini na sita", task.display)
        assertContains(task.accepted, "Jumanne, tarehe tatu Machi mwaka elfu mbili na ishirini na sita")
        assertContains(task.accepted, "Jumanne, tarehe tatu Machi elfu mbili na ishirini na sita")
    }

    /**
     * Swahili counts its months as well as naming them, so `Mwezi wa Tatu` grades wherever
     * `Machi` does — but the reveal keeps the borrowed name a learner will meet in print.
     * The counted name can only be authored: its `wa` is the associative concord slot the
     * pack refuses to invent for a bare numeral (`docs/number-forms.md` § Swahili).
     */
    @Test
    fun theSwahiliMonthsCountAsWellAsName() {
        val sw = content("de", "sw")
        val march = DateDrillTasks.name(DateTaskKind.Month, sw.months[2], reverse = false)
        assertEquals("Machi", march.display)
        assertContains(march.accepted, "Mwezi wa Tatu")
        assertContains(
            DateDrillTasks.dayMonth(sw, 3, 2).accepted,
            "tarehe tatu Mwezi wa Tatu",
            "a counted month grades inside a date too",
        )
    }

    /**
     * The short conversational order drops `tarehe` and puts the month first. It grades and
     * is never taught: the reveal is the one reading a learner should come away saying.
     */
    @Test
    fun theSwahiliShorthandOrderGradesWithoutBeingTaught() {
        val sw = content("de", "sw")
        val task = DateDrillTasks.dayMonth(sw, 9, 8)
        assertEquals("tarehe tisa Septemba", task.display)
        assertContains(task.accepted, "Septemba tisa")
    }

    /** `tarehe mosi`, the archaic one the numerals never reach; the cardinal grades beside it. */
    @Test
    fun theSwahiliFirstOfTheMonthIsMosi() {
        val sw = content("de", "sw")
        val task = DateDrillTasks.dayMonth(sw, 1, 0)
        assertEquals("tarehe mosi Januari", task.display)
        assertContains(task.accepted, "tarehe moja Januari")
    }

    /**
     * The span Sprossen, against the ruling table in `docs/date-readings.md` § Spans. The
     * prompt is the years the span covers, because the offset IS the skill: the twentieth
     * century is the nineteen-hundreds and a card printing `20.` would hand that over.
     */
    @Test
    fun everySpanReadsWhatItsLanguageRules() {
        val expected = mapOf(
            "de" to ("das zwanzigste Jahrhundert" to "das dritte Jahrtausend"),
            "en" to ("the twentieth century" to "the third millennium"),
            "eo" to ("la dudeka jarcento" to "la tria jarmilo"),
            "es" to ("el siglo veinte" to "el tercer milenio"),
            "fr" to ("le vingtième siècle" to "le troisième millénaire"),
            "it" to ("il ventesimo secolo" to "il terzo millennio"),
            "sw" to ("karne ya ishirini" to "milenia ya tatu"),
            "uk" to ("двадцяте століття" to "третє тисячоліття"),
        )
        for ((target, readings) in expected) {
            val content = content(if (target == "de") "en" else "de", target)
            val century = DateDrillTasks.span(content, DateTaskKind.Century, 20)
            assertEquals("1901–2000", century.promptText, "$target: the prompt is the years")
            assertEquals(readings.first, century.display, target)
            val millennium = DateDrillTasks.span(content, DateTaskKind.Millennium, 3)
            assertEquals("2001–3000", millennium.promptText, "$target: the prompt is the years")
            assertEquals(readings.second, millennium.display, target)
        }
    }

    /**
     * Spanish is the one language whose two spans part ways: the cardinal alone from the
     * eleventh century (`number-forms.md` § Spanish's ordinal cap arriving), while a
     * millennium never reaches the cap and stays the ordinal it is.
     */
    @Test
    fun theSpanishCenturyCountsWhereItsMillenniumRanks() {
        val es = content("de", "es")
        assertEquals("el siglo once", DateDrillTasks.span(es, DateTaskKind.Century, 11).display)
        assertEquals("el primer milenio", DateDrillTasks.span(es, DateTaskKind.Millennium, 1).display)
    }

    /**
     * `karne` and `milenia` are N-class, so the concord is `ya` and the numeral after it is
     * the plain cardinal — the noun-bearing frame `number-forms.md` § Swahili says a Swahili
     * ordinal has always needed, arriving rather than the rule bending.
     */
    @Test
    fun theSwahiliSpansSupplyTheConcordItsOrdinalsNeed() {
        val sw = content("de", "sw")
        assertEquals(
            "karne ya ishirini na moja",
            DateDrillTasks.span(sw, DateTaskKind.Century, 21).display,
        )
    }

    /** English intrusions are not Swahili spellings, and `mechi` is a football match. */
    @Test
    fun theSwahiliCalendarRefusesTheEnglishSpellings() {
        val sw = content("de", "sw")
        val march = DateDrillTasks.name(DateTaskKind.Month, sw.months[2], reverse = false)
        assertFalse("Mechi" in march.accepted)
        val february = DateDrillTasks.name(DateTaskKind.Month, sw.months[1], reverse = false)
        assertFalse("February" in february.accepted)
    }
}
