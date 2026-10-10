package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The audio half of [CatalogLintTest] — permanent rules over the REAL `catalog/audio/`:
 * every entry reaches a form its language states, and every file is named by the rule.
 * Credits and file integrity are [CatalogAudioProvenanceTest], the playback index
 * [CatalogAudioPlaybackTest].
 *
 * `catalog/audio/` holds no `concepts.json`, so it stays invisible to
 * [CatalogLintTest.everyAreaFolderIsRegisteredInTheManifest].
 */
class CatalogAudioLintTest {
    private val catalog get() = RealCatalog.catalog
    private val letterFileName = Regex("^letters/(u[0-9a-f]{4})+\\.mp3$")

    /**
     * The lookup is keyed by what the learner SEES, so a word entry whose spoken form no card
     * in its language shows — no `text`, `teaches`, `accepts` or tagged form of any
     * realization, nor such a form's bare verb stem ([verbStem]) — ships bytes that can never
     * play. Which concept the recording was fetched for is not asked: a merge or a move
     * keeps the form on some card, and the recording with it.
     */
    @Test
    fun everyWordEntrySpeaksAFormSomeCardShows() {
        for ((lang, manifest) in catalog.audio) {
            val shown = catalog.shownForms(lang).mapTo(mutableSetOf()) { speechKey(it) }
            for (form in manifest.words.keys) {
                assertTrue(speechKey(form) in shown, "audio/$lang word \"$form\": no card in $lang shows it")
            }
        }
    }

    /**
     * Two entries may share a speech key only when their bytes are identical (one
     * recording shipped in two sections, a word and an alphabet text). Differing bytes have
     * no right answer, so the runtime plays nothing — a silent card the converter must
     * resolve at generation time.
     */
    @Test
    fun noAmbiguousMatchedForm() {
        for ((lang, manifest) in catalog.audio) {
            // why: every section the runtime's form index spans, not `words` alone — the
            // index is built over all three, so a calendar name colliding with a word
            // silences BOTH, and adding a section could mute a word that used to play.
            val spoken = manifest.words.entries.map { "words/${it.key}" to it.value } +
                manifest.texts.entries.map { "texts/${it.key}" to it.value } +
                manifest.calendar.entries.map { "calendar/${it.key}" to it.value } +
                manifest.countries.entries.map { "countries/${it.key}" to it.value }
            val bySpeechKey = spoken.groupBy { speechKey(checkNotNull(it.second.matches)) }
            for ((key, group) in bySpeechKey) {
                val digests = group.mapTo(mutableSetOf()) { it.second.sha256 }
                assertEquals(1, digests.size,
                             "audio/$lang: \"$key\" is claimed by ${group.map { it.first }}")
            }
        }
    }

    /**
     * Every form-keyed file is its section's folder plus the form's ASCII stem; letter files
     * are codepoint-named, never glyph-named — `й`/`ї` decompose under NFD on APFS and
     * Unicode filenames cross four toolchains.
     */
    @Test
    fun audioFileNamesFollowTheNamingRules() {
        for ((lang, manifest) in catalog.audio) {
            for ((form, recording) in manifest.words) {
                assertEquals(
                    "words/${asciiStem(form)}.mp3",
                    recording.file,
                    "audio/$lang word \"$form\": file is not the form's ASCII stem",
                )
            }
            for ((form, recording) in manifest.articles) {
                assertEquals(
                    "articles/${asciiStem(form)}.mp3",
                    recording.file,
                    "audio/$lang article \"$form\": file is not the form's ASCII stem",
                )
            }
            for ((glyph, recording) in manifest.letters) {
                val where = "audio/$lang letter \"$glyph\""
                assertTrue(glyph.isNotBlank(), "$where: blank glyph")
                assertTrue(letterFileName.matches(recording.file), "$where: bad file \"${recording.file}\"")
                // why: one `u<cp>` per codepoint — a named row may be a digraph (es `ch`),
                // and a single codepoint would make `ch` and `c` the same file.
                assertEquals(
                    "letters/${glyph.codePoints().toArray().joinToString("") { "u%04x".format(it) }}.mp3",
                    recording.file,
                    "$where: file does not name the glyph's codepoints",
                )
            }
            for ((form, recording) in manifest.texts) {
                val where = "audio/$lang text \"$form\""
                // why: same reason the letters are codepoint-named — `pingüino.mp3` cannot
                // be looked up by the string a manifest stores once APFS has normalised it.
                assertEquals(
                    "texts/${asciiStem(form)}.mp3",
                    recording.file,
                    "$where: file is not the form's ASCII stem",
                )
            }
            for ((form, recording) in manifest.calendar) {
                val where = "audio/$lang calendar \"$form\""
                assertEquals(
                    "calendar/${asciiStem(form)}.mp3",
                    recording.file,
                    "$where: file is not the form's ASCII stem",
                )
            }
            for ((form, recording) in manifest.countries) {
                val where = "audio/$lang country \"$form\""
                assertEquals(
                    "countries/${asciiStem(form)}.mp3",
                    recording.file,
                    "$where: file is not the form's ASCII stem",
                )
            }
        }
    }

    /** The converter's file-stem rule: [a-z0-9-] survives, everything else becomes `u<hex>`. */
    private fun asciiStem(form: String): String = buildString {
        for (ch in form.trim().lowercase()) {
            if (ch.code < 128 && (ch.isLetterOrDigit() || ch == '-')) append(ch)
            else append("u%04x".format(ch.code))
        }
    }

    /**
     * A `texts{}` entry exists only to voice an alphabet row's `exampleText`. One that
     * matches no row is a stale fetch: it can never be reached, and its mp3 ships for
     * nothing. (The converse is fine — a form Commons has no recording for stays synthesized.)
     */
    @Test
    fun everyTextEntryVoicesAnAlphabetExampleText() {
        for ((lang, manifest) in catalog.audio) {
            if (manifest.texts.isEmpty()) continue
            val alphabet = assertNotNull(
                catalog.alphabet(lang),
                "audio/$lang ships text recordings but no alphabet is authored",
            )
            val authored = alphabet.entries.mapNotNull { it.exampleText }.toSet()
            for (form in manifest.texts.keys) {
                assertTrue(form in authored, "audio/$lang text \"$form\": no alphabet row cites it")
            }
        }
    }

    /**
     * [everyTextEntryVoicesAnAlphabetExampleText]'s rule for the calendar: a `calendar{}`
     * entry voices a weekday or month name the language actually states, `dateForm` and
     * the `teaches` beside it included — a card may show any of those. `abbr` is NOT among
     * them: it is a written short form the prompt wears and nothing ever says it, so a
     * recording keyed by one would ship for a string no lookup can reach.
     */
    @Test
    fun everyCalendarEntryVoicesAnAuthoredDateName() {
        for ((lang, manifest) in catalog.audio) {
            if (manifest.calendar.isEmpty()) continue
            val calendar = assertNotNull(
                catalog.dateCalendars[lang],
                "audio/$lang ships calendar recordings but no dates file is authored",
            )
            val authored = (calendar.weekdays + calendar.months)
                .flatMap { listOf(it.text) + it.teaches + it.accepts + listOfNotNull(it.dateForm) }
                .mapTo(mutableSetOf()) { speechKey(it) }
            for (form in manifest.calendar.keys) {
                assertTrue(speechKey(form) in authored,
                           "audio/$lang calendar \"$form\": no weekday or month is called that")
            }
        }
    }

    /**
     * [everyCalendarEntryVoicesAnAuthoredDateName]'s rule for the atlas: a `countries{}`
     * entry voices a country or nationality name some row actually states. `accepts` are
     * allowed for the same reason the calendar's are — permissive is safe here, since a
     * form nothing displays simply never gets asked for.
     */
    @Test
    fun everyCountryEntryVoicesAnAuthoredAtlasName() {
        for ((lang, manifest) in catalog.audio) {
            if (manifest.countries.isEmpty()) continue
            val names = assertNotNull(
                catalog.countryNames[lang],
                "audio/$lang ships country recordings but no atlas file is authored",
            )
            val authored = mutableSetOf<String>()
            for (row in names.values) {
                authored += (listOf(row.text) + row.accepts).map { speechKey(it) }
                authored += (listOf(row.nationality.text) + row.nationality.accepts)
                    .map { speechKey(it) }
            }
            for (form in manifest.countries.keys) {
                assertTrue(speechKey(form) in authored,
                           "audio/$lang country \"$form\": no atlas row is called that")
            }
        }
    }
}
