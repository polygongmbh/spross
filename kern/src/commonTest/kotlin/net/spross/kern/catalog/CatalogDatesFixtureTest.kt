package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Calendar parsing and the dates join over the synthetic [DatesFixture]. */
class CatalogDatesFixtureTest {
    private val catalog = DatesFixture.catalog()
    private val de = catalog.dateNames("de") ?: throw AssertionError("no de calendar")
    private val uk = catalog.dateNames("uk") ?: throw AssertionError("no uk calendar")

    private fun rejects(build: () -> Catalog): String =
        assertFailsWith<CatalogFormatException>(block = build).message.orEmpty()

    // -- registry ----------------------------------------------------------------------

    @Test
    fun filePresenceIsTheRegistry() {
        assertEquals("de", de.language)
        assertEquals(listOf("de", "pt", "uk"), catalog.dateCalendars.keys.sorted())
        assertNull(catalog.dateNames("en"))
        assertNull(catalog.dateNames("sw"))
    }

    /** A calendar joins no card, so editing one must never restamp a running box. */
    @Test
    fun calendarsStayOutOfTheFingerprint() {
        assertEquals(Fixture.catalog().fingerprint, catalog.fingerprint)
    }

    // -- parsed values -----------------------------------------------------------------

    @Test
    fun aWeekdayParsesFieldForField() {
        assertEquals(7, de.weekdays.size)
        assertEquals("Montag", de.weekdays.first().text)
        assertEquals("Mo", de.weekdays.first().abbr)
        assertEquals(emptyList(), de.weekdays.first().synonyms)
        val saturday = de.weekdays[5]
        assertEquals("Samstag", saturday.text)
        assertEquals(listOf("Sonnabend"), saturday.synonyms)
        assertNull(saturday.dateForm)
    }

    @Test
    fun monthsCarryTwelveInIsoOrderAndNoAbbreviation() {
        assertEquals(12, de.months.size)
        assertEquals("Januar", de.months.first().text)
        assertEquals("Dezember", de.months.last().text)
        assertTrue(de.months.all { it.abbr == null })
    }

    /** The one key only Ukrainian needs: what a name becomes INSIDE a date. */
    @Test
    fun aDateFormRidesBesideTheCitationName() {
        assertEquals("березень", uk.months[2].text)
        assertEquals("березня", uk.months[2].dateForm)
        assertTrue(uk.weekdays.all { it.dateForm == null })
    }

    @Test
    fun variantsAndNotesFollowTheRealizationSchema() {
        val pt = assertNotNull(catalog.dateNames("pt"))
        assertEquals(listOf("segunda"), pt.weekdays.first().variants)
        assertEquals(setOf("de"), pt.weekdays.first().notes.keys)
        assertEquals(listOf("sabado"), pt.weekdays[5].variants)
    }

    @Test
    fun patternsParseByKindAndTheYearOneIsOptional() {
        assertEquals("der {day} {month}", de.patterns.dayMonth.text)
        assertEquals(listOf("den {day} {month}"), de.patterns.dayMonth.variants)
        assertEquals("{weekday}, der {day} {month}", de.patterns.date.text)
        assertEquals("{weekday}, der {day} {month} {year}", de.patterns.dateWithYear?.text)
        assertEquals("{d}.{m}.{y}", de.numeric)
        // Ukrainian reads no year inside a date, so its ladder stops one Sprosse short.
        assertNull(uk.patterns.dateWithYear)
    }

    // -- the join ----------------------------------------------------------------------

    @Test
    fun theJoinPairsBothSidesNamesInIsoOrder() {
        val content = assertNotNull(catalog.dateDrillContent("de", "uk"))
        assertEquals(7, content.weekdays.size)
        assertEquals(12, content.months.size)
        assertEquals(listOf(0, 1, 2), content.months.take(3).map { it.index })
        assertEquals("März", content.months[2].source.text)
        assertEquals("березень", content.months[2].target.text)
        assertEquals("Mo", content.weekdays.first().source.abbr)
    }

    /**
     * The two asymmetric halves come from the side that owns them: the prompt is written in
     * the SOURCE's digits, the answer spelled with the TARGET's assembly.
     */
    @Test
    fun theDigitsAreTheSourcesAndThePatternsAreTheTargets() {
        val content = assertNotNull(catalog.dateDrillContent("uk", "de"))
        assertEquals("{d}.{m}.{y}", content.numeric)
        assertEquals("{weekday}, der {day} {month}", content.patterns.date.text)
        assertNotNull(content.patterns.dateWithYear)
    }

    @Test
    fun aPairWithoutACalendarOnEitherSideHasNoDrill() {
        assertNull(catalog.dateDrillContent("de", "en"))
        assertNull(catalog.dateDrillContent("en", "de"))
        assertNull(catalog.dateDrillContent("en", "sw"))
    }

    /**
     * The day of the month is generated, never authored, so a language the trainer cannot
     * read numerals for supplies prompts and never answers — the frame rule.
     */
    @Test
    fun aTargetWithoutATrainerPackSuppliesPromptsOnly() {
        assertNull(catalog.dateDrillContent("de", "pt"))
        assertNotNull(catalog.dateDrillContent("pt", "de"))
    }

    // -- parse failures ----------------------------------------------------------------

    @Test
    fun anIncompleteCalendarIsABugAndNeverACoverageGap() {
        assertTrue("expected 7, got 6" in rejects { DatesFixture.deCalendar(weekdays = SIX_WEEKDAYS) })
        assertTrue("expected 12, got 13" in rejects { DatesFixture.deCalendar(months = THIRTEEN_MONTHS) })
    }

    @Test
    fun unknownKeysAreRejectedAndAbbrBelongsToWeekdaysAlone() {
        val flagged = """{ "text": "Montag", "abbr": "Mo", "flag": "🇩🇪" }, """ + SIX_WEEKDAYS
        assertTrue("unknown keys" in rejects { DatesFixture.deCalendar(weekdays = flagged) })
        assertTrue("unknown keys" in rejects { DatesFixture.deCalendar(months = ABBREVIATED_MONTHS) })
    }

    /** Every weekday wears a short form: each file is a possible prompt side. */
    @Test
    fun aWeekdayWithoutAnAbbreviationIsRefused() {
        val bare = """{ "text": "Montag" }, """ + SIX_WEEKDAYS
        assertTrue("missing \"abbr\"" in rejects { DatesFixture.deCalendar(weekdays = bare) })
    }

    @Test
    fun aDateFormRepeatingItsTextSaysNothing() {
        val months = """{ "text": "Januar", "dateForm": "Januar" }, """ + ELEVEN_MONTHS
        assertTrue("dateForm repeats the text" in rejects { DatesFixture.deCalendar(months = months) })
    }

    @Test
    fun aPatternTakesItsOwnMarkersAndNoOthers() {
        fun refusal(patterns: String) = rejects { DatesFixture.deCalendar(patterns = patterns) }
        assertTrue("takes no {year}" in refusal(patterns(date = "{weekday}, der {day} {month} {year}")))
        assertTrue("takes no {weekday}" in refusal(patterns(dayMonth = "{weekday}, der {day} {month}")))
        assertTrue("takes no {jahr}" in refusal(patterns(date = "{weekday}, der {day} {month} {jahr}")))
        assertTrue("takes {month} once, found 0" in refusal(patterns(date = "{weekday}, der {day}")))
        assertTrue("takes {day} once, found 2" in refusal(patterns(dayMonth = "der {day} {day} {month}")))
    }

    /** A variant fills exactly as its text does, so it is held to the same markers. */
    @Test
    fun aPatternVariantIsHeldToTheSameMarkers() {
        val patterns = patterns(dayMonthVariants = """["den {day} {month} {year}"]""")
        assertTrue("takes no {year}" in rejects { DatesFixture.deCalendar(patterns = patterns) })
    }

    @Test
    fun aRequiredPatternIsNeverOptional() {
        val patterns = """{ "dayMonth": { "text": "der {day} {month}" } }"""
        assertTrue("missing \"date\"" in rejects { DatesFixture.deCalendar(patterns = patterns) })
    }

    @Test
    fun theDigitFormatNamesAllThreeFields() {
        assertTrue("takes {y} once, found 0" in rejects { DatesFixture.deCalendar(numeric = "{d}.{m}.") })
    }

    private companion object {
        const val SIX_WEEKDAYS = """
          { "text": "Dienstag", "abbr": "Di" }, { "text": "Mittwoch", "abbr": "Mi" },
          { "text": "Donnerstag", "abbr": "Do" }, { "text": "Freitag", "abbr": "Fr" },
          { "text": "Samstag", "abbr": "Sa" }, { "text": "Sonntag", "abbr": "So" }"""

        const val ELEVEN_MONTHS = """
          { "text": "Februar" }, { "text": "März" }, { "text": "April" }, { "text": "Mai" },
          { "text": "Juni" }, { "text": "Juli" }, { "text": "August" }, { "text": "September" },
          { "text": "Oktober" }, { "text": "November" }, { "text": "Dezember" }"""

        const val THIRTEEN_MONTHS = """{ "text": "Januar" }, { "text": "Nachjahr" }, """ + ELEVEN_MONTHS

        const val ABBREVIATED_MONTHS = """{ "text": "Januar", "abbr": "Jan" }, """ + ELEVEN_MONTHS

        fun patterns(
            dayMonth: String = "der {day} {month}",
            dayMonthVariants: String = """["den {day} {month}"]""",
            date: String = "{weekday}, der {day} {month}",
        ): String =
            """{ "dayMonth": { "text": "$dayMonth", "variants": $dayMonthVariants },
                 "date": { "text": "$date" } }"""
    }
}
