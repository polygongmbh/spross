package net.spross.kern.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.trainer.Trainer

/**
 * The shipping calendars, linted whole: what only the real catalog can prove. The
 * one-file shape rules hard-fail `Catalog.load` itself; the loaded model is restated
 * once below so a parser regression fails here by name, and everything else is
 * cross-file — the registry, the joins, the confusability ruling, the pattern words.
 */
class CatalogDatesLintTest {

    private val catalog get() = RealCatalog.catalog

    private fun forEachName(action: (lang: Language, where: String, name: DateNames) -> Unit) {
        for ((lang, calendar) in catalog.dateCalendars) {
            for (day in calendar.weekdays) action(lang, "dates/$lang.json ${day.text}", day)
            for (month in calendar.months) action(lang, "dates/$lang.json ${month.text}", month)
        }
    }

    @Test
    fun everyDatesFileBelongsToADeclaredLanguageAndLoads() {
        // The folder's README.md is its documentation, not content — only .json is the registry.
        val files = File(RealCatalog.root, "dates").listFiles().orEmpty()
            .map { it.name }.filter { it.endsWith(".json") }
        assertTrue(files.isNotEmpty(), "no calendars under catalog/dates/")
        for (name in files) {
            val lang = name.removeSuffix(".json")
            assertTrue(lang in catalog.languages, "dates/$name: undeclared language")
            assertNotNull(catalog.dateCalendars[lang], "dates/$name: declared but never loaded")
        }
    }

    @Test
    fun everyCalendarCarriesTheFullWeekAndYearWithAbbreviatedWeekdays() {
        for ((lang, calendar) in catalog.dateCalendars) {
            assertEquals(7, calendar.weekdays.size, "dates/$lang.json: weekdays")
            assertEquals(12, calendar.months.size, "dates/$lang.json: months")
            for (day in calendar.weekdays) {
                assertNotNull(day.abbr, "dates/$lang.json: ${day.text} wears no abbr")
            }
        }
    }

    @Test
    fun everyFormIsTrimmedDedupedAndNeverEchoesItsText() {
        forEachName { _, where, name ->
            val forms = listOf(name.text) + name.synonyms + name.variants +
                listOfNotNull(name.dateForm, name.abbr)
            for (form in forms) {
                assertTrue(form.isNotBlank() && form.trim() == form, "$where: untrimmed \"$form\"")
                assertTrue('|' !in form && '\n' !in form, "$where: bad char in \"$form\"")
            }
            val alternates = name.synonyms + name.variants
            assertTrue(name.text !in alternates, "$where: an alternate repeats the text")
            assertEquals(alternates.toSet().size, alternates.size, "$where: duplicate alternates")
            assertTrue(name.dateForm != name.text, "$where: dateForm echoes the text")
        }
    }

    /**
     * A pattern wears the same three lists a name does, so it can go wrong the same way:
     * an assembly repeated across them is a form that says nothing, and a synonym repeating
     * the text claims the language says one thing two ways when it does not.
     */
    @Test
    fun everyPatternAlternateIsDistinctFromItsTextAndFromTheOthers() {
        for ((lang, calendar) in catalog.dateCalendars) {
            val patterns = listOfNotNull(
                "dayMonth" to calendar.patterns.dayMonth,
                "date" to calendar.patterns.date,
                calendar.patterns.dateWithYear?.let { "dateWithYear" to it },
            )
            for ((key, pattern) in patterns) {
                val where = "dates/$lang.json patterns.$key"
                assertEquals(
                    pattern.forms.toSet().size,
                    pattern.forms.size,
                    "$where: an alternate repeats another form",
                )
            }
        }
    }

    /**
     * File presence is the registry, and the pack rule cuts it one way: every ordered
     * pair of calendar languages drills dates wherever the ANSWER side has a trainer
     * pack, and a pack-less calendar serves as a prompt side only.
     */
    @Test
    fun everyOrderedPairOfCalendarLanguagesJoins() {
        for (source in catalog.dateCalendars.keys) {
            for (target in catalog.dateCalendars.keys) {
                if (source == target) continue
                val content = catalog.dateDrillContent(source, target)
                if (Trainer.supports(target)) {
                    assertNotNull(content, "$source→$target: no dates drill")
                } else {
                    assertNull(content, "$source→$target: a pack-less answer side joined anyway")
                }
            }
        }
    }

    /**
     * The Sprosse-4 ruling's own predicate: putting the day+month Sprosse on the assembled
     * (bridging) side is safe only while no language has BOTH a two-word `dayMonth`
     * reading AND a distance-1 calendar pair — measured over the drill normalizer's own
     * comparison shapes, one merged space, exactly as the refusal index sees them. The
     * language that ends that moot judgment call fails here by name, and the owner rules
     * again before its content ships.
     */
    @Test
    fun noLanguageHasATwoWordDayMonthReadingBesideADistanceOnePair() {
        for ((lang, calendar) in catalog.dateCalendars) {
            if (!Trainer.supports(lang)) continue
            val normalizer = AnswerNormalizer.drill(catalog.languages.getValue(lang))
            val shapesPerEntry = (calendar.weekdays + calendar.months).map { name ->
                (listOf(name.text) + name.synonyms + name.variants + listOfNotNull(name.dateForm))
                    .flatMap { normalizer.comparisonForms(it, verbLeniency = false) }
            }
            val confusable = buildList {
                for (i in shapesPerEntry.indices) {
                    for (j in i + 1 until shapesPerEntry.size) {
                        for (a in shapesPerEntry[i]) {
                            for (b in shapesPerEntry[j]) {
                                if (AnswerNormalizer.damerauLevenshtein(a, b) <= 1) add("$a/$b")
                            }
                        }
                    }
                }
            }
            if (confusable.isEmpty()) continue
            val pack = Trainer.pack(lang)
            val fewestWords = calendar.months.minOf { month ->
                (1..31).minOf { day ->
                    calendar.patterns.dayMonth.text
                        .replace("{day}", pack.dateDay(day).first())
                        .replace("{month}", month.dateForm ?: month.text)
                        .split(' ').count { it.isNotEmpty() }
                }
            }
            assertTrue(
                fewestWords >= 3,
                "dates/$lang.json: a $fewestWords-word dayMonth reading beside distance-1 " +
                    "pair(s) $confusable — the Sprosse-4 ruling is no longer moot, ask the owner",
            )
        }
    }

    /**
     * Documented pattern words — the `PhraseVocabAuditTests` forcing function at the
     * calendar's own size: a pattern's non-marker words are content the learner has to
     * type, so each stands here with its reason. Exact set equality, so a word can
     * neither drift in unreviewed nor linger after its pattern goes.
     */
    private val patternWords: Map<Language, Set<String>> = mapOf(
        // The article the German date reading leads with — nominative on the card,
        // accusative in the accept-only variant.
        "de" to setOf("der", "den"),
        // The formal English reading's article and joiner ("the third of March").
        "en" to setOf("the", "of"),
        // Esperanto's one invariable article, and the `de` its month hangs on.
        "eo" to setOf("la", "de"),
        // The Spanish article, the `de` before the month and once more before the
        // year, and the contracted `del` the year's accept-only variant takes.
        "es" to setOf("el", "de", "del"),
        // The article a bare French date leads with; once the weekday is named it goes.
        "fr" to setOf("le"),
        // The Italian article, and the `l'` it elides to before a vowel-initial day
        // ("l'otto marzo") — accept-only, because a pattern cannot elide by itself.
        "it" to setOf("il", "l"),
        // `tarehe` — the word a Swahili date counts from, no article in sight.
        "sw" to setOf("tarehe"),
        // Ukrainian assembles a date out of its parts alone: the genitive does the work
        // an article or a preposition does elsewhere, and there is no year Sprosse to word.
        "uk" to emptySet(),
    )

    @Test
    fun everyPatternWordIsDocumentedWithItsReason() {
        for ((lang, calendar) in catalog.dateCalendars) {
            val documented = assertNotNull(
                patternWords[lang],
                "dates/$lang.json: no documented pattern-word set — add one, even if empty",
            )
            val patterns = listOfNotNull(
                calendar.patterns.dayMonth, calendar.patterns.date, calendar.patterns.dateWithYear,
            )
            val words = patterns.flatMap { it.forms }
                .flatMap { tokens(it.replace(MARKERS, " ")) }
                .toSet()
            assertEquals(documented, words, "dates/$lang.json: pattern words drifted from the audit")
        }
    }

    /** Lowercase word tokens, joiners dropped in-word — mirrors AnswerNormalizer. */
    private fun tokens(text: String): List<String> {
        val joined = text.lowercase().filter { it != '\'' && it != '’' && it != '-' }
        return joined.map { if (it.isLetter()) it else ' ' }.joinToString("")
            .split(" ").filter { it.isNotEmpty() }
    }

    private val MARKERS = Regex("""\{(weekday|day|month|year)}""")
}
