package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.Language

/**
 * `catalog/dates/<lang>.json` → [DateCalendar], on [CountryAtlasParser]'s conventions:
 * unknown keys rejected, every failure a [CatalogFormatException] naming the file and the row.
 *
 * The list lengths are checked here because nothing later could repair them — a calendar
 * naming eleven months is a bug, not a coverage gap — and so are the pattern markers, since
 * a pattern that carries `{year}` where its kind takes none would fill with the wrong value
 * on the first draw. What only the WHOLE catalog can decide is not here: whether a language
 * may answer at all is [Catalog.dateDrillContent]'s, because the day of the month is read by
 * a trainer pack and the parser sees one file at a time.
 */
internal object DateCalendarParser {
    private const val WEEKDAYS = 7
    private const val MONTHS = 12
    private val WEEKDAY_KEYS = setOf("text", "synonyms", "variants", "abbr", "dateForm", "notes")
    private val MONTH_KEYS = WEEKDAY_KEYS - "abbr"

    private val MARKER = Regex("\\{[^{}]*\\}")
    private val NUMERIC_MARKERS = listOf("{d}", "{m}", "{y}")

    /** Which markers each pattern kind takes — exactly these, each exactly once. */
    private val PATTERN_MARKERS = mapOf(
        "dayMonth" to listOf("{day}", "{month}"),
        "date" to listOf("{weekday}", "{day}", "{month}"),
        "dateWithYear" to listOf("{weekday}", "{day}", "{month}", "{year}"),
    )

    fun parse(path: String, text: String, language: Language, declared: Set<Language>): DateCalendar {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("weekdays", "months", "numeric", "patterns"))
        val numeric = root.trimmedString(path, "root", "numeric")
        requireMarkers(path, "numeric", numeric, NUMERIC_MARKERS)
        val patterns = root["patterns"]?.obj(path, "patterns") ?: parseError(path, "missing \"patterns\"")
        patterns.rejectUnknownKeys(path, "patterns", PATTERN_MARKERS.keys)
        return DateCalendar(
            language = language,
            weekdays = names(path, root, "weekdays", WEEKDAYS, weekday = true, declared = declared),
            months = names(path, root, "months", MONTHS, weekday = false, declared = declared),
            numeric = numeric,
            patterns = DatePatterns(
                dayMonth = requirePattern(path, patterns, "dayMonth"),
                date = requirePattern(path, patterns, "date"),
                dateWithYear = optionalPattern(path, patterns, "dateWithYear"),
            ),
        )
    }

    private fun names(
        path: String,
        root: JsonObject,
        key: String,
        count: Int,
        weekday: Boolean,
        declared: Set<Language>,
    ): List<DateNames> {
        val rows = root[key]?.arr(path, key) ?: parseError(path, "missing \"$key\"")
        if (rows.size != count) parseError(path, "$key: expected $count, got ${rows.size}")
        return rows.mapIndexed { i, el ->
            val where = "$key[$i]"
            val o = el.obj(path, where)
            o.rejectUnknownKeys(path, where, if (weekday) WEEKDAY_KEYS else MONTH_KEYS)
            val text = o.trimmedString(path, where, "text")
            val dateForm = o.optionalTrimmedString(path, where, "dateForm")
            // why: a dateForm repeating the text is a key that says nothing, and it says it
            // loudly — the author believes the date is inflected where it is not.
            if (dateForm == text) parseError(path, "$where: dateForm repeats the text")
            DateNames(
                text = text,
                synonyms = forms(path, where, o, "synonyms"),
                variants = forms(path, where, o, "variants"),
                // why: every dates file is a possible PROMPT side and a dated prompt names
                // its weekday short, so a weekday without an abbr has no prompt to wear.
                abbr = if (weekday) o.trimmedString(path, where, "abbr") else null,
                dateForm = dateForm,
                notes = notes(path, where, o, declared),
            )
        }
    }

    private fun notes(
        path: String,
        where: String,
        o: JsonObject,
        declared: Set<Language>,
    ): Map<Language, String> =
        o.stringMap(path, where, "notes").onEach { (reader, note) ->
            if (reader !in declared) parseError(path, "$where: note for undeclared language \"$reader\"")
            if (note.isBlank()) parseError(path, "$where: blank note.$reader")
        }

    private fun forms(path: String, where: String, o: JsonObject, key: String): List<String> =
        o.stringList(path, where, key).onEach {
            if (it.isBlank() || it.trim() != it) parseError(path, "$where: bad $key \"$it\"")
        }

    private fun requirePattern(path: String, o: JsonObject, key: String): DatePattern =
        optionalPattern(path, o, key) ?: parseError(path, "patterns: missing \"$key\"")

    private fun optionalPattern(path: String, o: JsonObject, key: String): DatePattern? {
        val where = "patterns.$key"
        val row = o[key]?.obj(path, where) ?: return null
        row.rejectUnknownKeys(path, where, setOf("text", "variants"))
        val markers = PATTERN_MARKERS.getValue(key)
        val text = row.trimmedString(path, where, "text")
        requireMarkers(path, where, text, markers)
        val variants = forms(path, where, row, "variants")
        for (variant in variants) requireMarkers(path, where, variant, markers)
        return DatePattern(text, variants)
    }

    private fun requireMarkers(path: String, where: String, text: String, markers: List<String>) {
        val found = MARKER.findAll(text).map { it.value }.toList()
        for (marker in markers) {
            val count = found.count { it == marker }
            if (count != 1) parseError(path, "$where: \"$text\" takes $marker once, found $count")
        }
        for (marker in found) {
            if (marker !in markers) parseError(path, "$where: \"$text\" takes no $marker")
        }
    }
}
