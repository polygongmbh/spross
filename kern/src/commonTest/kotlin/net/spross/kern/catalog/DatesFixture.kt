package net.spross.kern.catalog

/**
 * Calendars over the [Fixture] catalog — de, uk and pt only, so "no file at all" (en, sw)
 * keeps its coverage. Between them the three carry every schema shape: a synonym (de
 * `Sonnabend`), a variant and a note (pt), a `dateForm` on every month (uk), and uk's
 * missing `dateWithYear`, the optional pattern. **pt is declared but has no trainer pack**,
 * which is the lever for "a calendar may be a prompt side and never an answer side".
 */
internal object DatesFixture {
    private const val DE_WEEKDAYS = """
      { "text": "Montag", "abbr": "Mo" },
      { "text": "Dienstag", "abbr": "Di" },
      { "text": "Mittwoch", "abbr": "Mi" },
      { "text": "Donnerstag", "abbr": "Do" },
      { "text": "Freitag", "abbr": "Fr" },
      { "text": "Samstag", "abbr": "Sa", "synonyms": ["Sonnabend"] },
      { "text": "Sonntag", "abbr": "So" }"""

    private const val DE_MONTHS = """
      { "text": "Januar" }, { "text": "Februar" }, { "text": "März" }, { "text": "April" },
      { "text": "Mai" }, { "text": "Juni" }, { "text": "Juli" }, { "text": "August" },
      { "text": "September" }, { "text": "Oktober" }, { "text": "November" }, { "text": "Dezember" }"""

    private const val DE_PATTERNS = """
     { "dayMonth": { "text": "der {day} {month}", "variants": ["den {day} {month}"] },
       "date": { "text": "{weekday}, der {day} {month}", "variants": ["{weekday}, den {day} {month}"] },
       "dateWithYear": { "text": "{weekday}, der {day} {month} {year}" } }"""

    val files: Map<String, String> = mapOf(
        "dates/de.json" to de(),
        "dates/uk.json" to """
            { "weekdays": [
              { "text": "понеділок", "abbr": "пн" }, { "text": "вівторок", "abbr": "вт" },
              { "text": "середа", "abbr": "ср" }, { "text": "четвер", "abbr": "чт" },
              { "text": "п'ятниця", "abbr": "пт" }, { "text": "субота", "abbr": "сб" },
              { "text": "неділя", "abbr": "нд" } ],
              "months": [
              { "text": "січень", "dateForm": "січня" }, { "text": "лютий", "dateForm": "лютого" },
              { "text": "березень", "dateForm": "березня" }, { "text": "квітень", "dateForm": "квітня" },
              { "text": "травень", "dateForm": "травня" }, { "text": "червень", "dateForm": "червня" },
              { "text": "липень", "dateForm": "липня" }, { "text": "серпень", "dateForm": "серпня" },
              { "text": "вересень", "dateForm": "вересня" }, { "text": "жовтень", "dateForm": "жовтня" },
              { "text": "листопад", "dateForm": "листопада" }, { "text": "грудень", "dateForm": "грудня" } ],
              "numeric": "{d}.{m}.{y}",
              "patterns": { "dayMonth": { "text": "{day} {month}" },
                            "date": { "text": "{weekday}, {day} {month}" } } }
        """.trimIndent(),
        "dates/pt.json" to """
            { "weekdays": [
              { "text": "segunda-feira", "abbr": "seg",
                "notes": { "de": "Der Montag ist der ZWEITE Wochentag — gezählt wird ab Sonntag." },
                "variants": ["segunda"] },
              { "text": "terça-feira", "abbr": "ter" }, { "text": "quarta-feira", "abbr": "qua" },
              { "text": "quinta-feira", "abbr": "qui" }, { "text": "sexta-feira", "abbr": "sex" },
              { "text": "sábado", "abbr": "sáb", "variants": ["sabado"] },
              { "text": "domingo", "abbr": "dom" } ],
              "months": [
              { "text": "janeiro" }, { "text": "fevereiro" }, { "text": "março" }, { "text": "abril" },
              { "text": "maio" }, { "text": "junho" }, { "text": "julho" }, { "text": "agosto" },
              { "text": "setembro" }, { "text": "outubro" }, { "text": "novembro" }, { "text": "dezembro" } ],
              "numeric": "{d}/{m}/{y}",
              "patterns": { "dayMonth": { "text": "{day} de {month}" },
                            "date": { "text": "{weekday}, {day} de {month}" },
                            "dateWithYear": { "text": "{weekday}, {day} de {month} de {year}" } } }
        """.trimIndent(),
    )

    /** The [Fixture] catalog with calendars; nothing else is loaded — the parts are independent. */
    fun catalog(): Catalog = Catalog.load(MapCatalogSource(Fixture.files + files))

    /** The same catalog with one calendar replaced — for the parse-failure cases. */
    fun catalogWith(path: String, calendar: String): Catalog =
        Catalog.load(MapCatalogSource(Fixture.files + files + (path to calendar)))

    /** The de calendar with one part swapped out, for the one-rule failure cases. */
    fun deCalendar(
        weekdays: String = DE_WEEKDAYS,
        months: String = DE_MONTHS,
        numeric: String = "{d}.{m}.{y}",
        patterns: String = DE_PATTERNS,
    ): Catalog = catalogWith("dates/de.json", de(weekdays, months, numeric, patterns))

    private fun de(
        weekdays: String = DE_WEEKDAYS,
        months: String = DE_MONTHS,
        numeric: String = "{d}.{m}.{y}",
        patterns: String = DE_PATTERNS,
    ): String =
        """{ "weekdays": [$weekdays], "months": [$months],
             "numeric": "$numeric", "patterns": $patterns }"""
}
