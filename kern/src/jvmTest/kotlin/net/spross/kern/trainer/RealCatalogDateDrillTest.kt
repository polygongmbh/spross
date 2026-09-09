package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.catalog.dateDrillContent

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
