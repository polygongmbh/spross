package net.spross.kern.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The attribution half of [CatalogAudioLintTest] over the REAL `catalog/audio/`:
 * every recording is credited to somebody, reaches the credits screen,
 * and ships as the untouched bytes its manifest names.
 */
class CatalogAudioProvenanceTest {
    private val catalog get() = RealCatalog.catalog
    private val audioRoot = File(RealCatalog.root, "audio")

    /** Authorship values that name nobody — a BY/BY-SA file carrying one cannot ship. */
    private val junkAuthors = setOf("own work", "myself", "")

    /**
     * Commons' wording for a file whose authorship nobody recorded: the name is the
     * uploader, inferred from the copyright tag. It reads like a credit and is a guess,
     * which is the one thing a BY/BY-SA notice may not be.
     */
    private val assumedAuthor = Regex("assumed \\(based on copyright claims\\)", RegexOption.IGNORE_CASE)

    private fun forEachEntry(action: (lang: String, id: String, recording: AudioRecording) -> Unit) {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) action(lang, slug, recording)
            for ((glyph, recording) in manifest.letters) action(lang, glyph, recording)
            for ((form, recording) in manifest.texts) action(lang, form, recording)
            for ((slug, recording) in manifest.articles) action(lang, "$slug (article)", recording)
            for ((form, recording) in manifest.calendar) action(lang, form, recording)
            for ((form, recording) in manifest.countries) action(lang, form, recording)
        }
    }

    /** Every field the credits screen renders is present, and BY/BY-SA link their deed. */
    @Test
    fun audioEntryFieldsAreWellFormed() {
        forEachEntry { lang, id, recording ->
            val where = "audio/$lang/manifest.json $id"
            val required = mapOf(
                "file" to recording.file,
                "license" to recording.license,
                "author" to recording.author,
                "source" to recording.source,
                "sha256" to recording.sha256,
            )
            for ((field, value) in required) assertTrue(value.isNotBlank(), "$where: blank $field")
            if (recording.license != "Public domain") {
                assertTrue(!recording.licenseUrl.isNullOrBlank(), "$where: ${recording.license} needs a deed URL")
            }
        }
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) {
                assertTrue(!recording.matches.isNullOrBlank(), "audio/$lang/$slug: blank matches")
            }
            for ((glyph, recording) in manifest.letters) {
                assertEquals(null, recording.matches, "audio/$lang letter \"$glyph\": letters speak a name")
            }
            for ((form, recording) in manifest.texts) {
                // why: the key IS the spoken form for a text entry — it has no slug to be
                // keyed by, and the two disagreeing would index the recording under a word
                // it does not say.
                assertEquals(form, recording.matches, "audio/$lang text \"$form\": key is not what it speaks")
            }
            for ((form, recording) in manifest.calendar) {
                // why: `texts`' rule for `texts`' reason — the key IS the spoken form, there
                // being no slug for a weekday to be keyed by.
                assertEquals(form, recording.matches,
                             "audio/$lang calendar \"$form\": key is not what it speaks")
            }
            for ((form, recording) in manifest.countries) {
                assertEquals(form, recording.matches,
                             "audio/$lang country \"$form\": key is not what it speaks")
            }
        }
    }

    /**
     * The root credit maps describe the pack and nothing else.
     *
     * A manifest carries a license per AUTHOR and a deed per LICENSE rather than both per
     * file, so those two maps are the only place a credit is authored — and a row nobody
     * records under is a speaker or a license the pack no longer has. Harmless to render
     * (the credits screen walks the recordings), and exactly how the maps drift out of
     * step with the mp3s they are supposed to describe. The parser holds the other
     * direction: an entry whose author or license has no row cannot load at all.
     */
    @Test
    fun everyCreditRowIsUsedByARecording() {
        for (lang in catalog.audio.keys) {
            val root = Json.parseToJsonElement(File(audioRoot, "$lang/manifest.json").readText()).jsonObject
            val used = mutableSetOf<String>()
            val licensed = mutableSetOf<String>()
            forEachEntry { entryLang, _, recording ->
                if (entryLang == lang) {
                    used += recording.author
                    licensed += recording.license
                }
            }
            assertEquals(
                used.sorted(),
                root.getValue("authors").jsonObject.keys.sorted(),
                "audio/$lang: \"authors\" credits somebody the pack does not record",
            )
            assertEquals(
                licensed.sorted(),
                root.getValue("licenses").jsonObject.keys.sorted(),
                "audio/$lang: \"licenses\" deeds a license the pack does not use",
            )
        }
    }

    /**
     * BY and BY-SA both require naming the author, so a placeholder is a compliance hole,
     * not a cosmetic one. The converter resolves these against Commons and drops the rest.
     */
    @Test
    fun noAudioAuthorIsUnattributable() {
        forEachEntry { lang, id, recording ->
            assertTrue(
                recording.author.trim().lowercase() !in junkAuthors &&
                    !assumedAuthor.containsMatchIn(recording.author),
                "audio/$lang/$id: unattributable author \"${recording.author}\"",
            )
        }
    }

    /**
     * Both directions: a manifest entry without its file is a silent card, and an mp3 no
     * entry references is uncredited weight in every install (both platforms bundle the
     * whole tree). An audio directory for an undeclared language fails here too — nothing
     * reads it, so nothing may ship it.
     */
    @Test
    fun everyAudioFileShipsAndIsReferencedExactlyOnce() {
        val referenced = mutableListOf<String>()
        for ((lang, manifest) in catalog.audio) {
            for (recording in manifest.words.values + manifest.letters.values +
                manifest.texts.values + manifest.articles.values + manifest.calendar.values +
                manifest.countries.values) {
                val relative = "$lang/${recording.file}"
                assertTrue(File(audioRoot, relative).isFile, "audio/$relative: missing on disk")
                referenced += relative
            }
        }
        assertEquals(referenced.toSet().size, referenced.size, "an mp3 is referenced twice")
        val onDisk = audioRoot.walkTopDown()
            .filter { it.isFile && it.extension == "mp3" }
            .map { it.relativeTo(audioRoot).invariantSeparatorsPath }
        assertEquals(onDisk.toSortedSet(), referenced.toSortedSet())
    }

    /**
     * The letters half of the attribution gate: the credits screen renders
     * [Catalog.audioCredits] and nothing else, so a pack the grouping never reaches is a
     * BY-SA notice no user can read — and bundling the files discharges nothing. A rule,
     * not a roster: every letter recording has to find its own (language, author,
     * license) group carrying its glyph and its Commons filename. The uk pack is asserted
     * present first, so the rule can never pass by having nothing to check.
     */
    @Test
    fun everyLetterRecordingReachesTheCreditsSurface() {
        assertTrue(catalog.audio["uk"]?.letters?.isNotEmpty() == true, "uk ships no letter recordings")
        val credits = catalog.audioCredits()
        for ((lang, manifest) in catalog.audio) {
            for ((glyph, recording) in manifest.letters) {
                val where = "audio/$lang letter \"$glyph\""
                val group = assertNotNull(
                    credits.singleOrNull {
                        it.language == lang && it.author == recording.author && it.license == recording.license
                    },
                    "$where: no single ${recording.license} group for ${recording.author}",
                )
                assertTrue(
                    AudioCreditFile(glyph, recording.source) in group.files,
                    "$where: absent from its own credit group",
                )
            }
        }
    }

    /**
     * The untouched-transcodes gate: Commons files ship byte-identical because
     * re-encoding is an adaptation under BY-SA. The converter writes the digest it
     * verified; this re-hashes what is actually committed.
     */
    @Test
    fun audioFilesMatchTheirManifestHashes() {
        for ((lang, manifest) in catalog.audio) {
            for (entry in manifest.words.entries + manifest.letters.entries +
                manifest.texts.entries + manifest.articles.entries + manifest.calendar.entries +
                manifest.countries.entries) {
                val file = File(audioRoot, "$lang/${entry.value.file}")
                if (!file.isFile) continue // reported by everyAudioFileShipsAndIsReferencedExactlyOnce
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                assertEquals(entry.value.sha256, digest, "audio/$lang/${entry.value.file}: bytes differ (re-encoded?)")
            }
        }
    }
}
