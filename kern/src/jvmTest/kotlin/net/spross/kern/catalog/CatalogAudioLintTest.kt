package net.spross.kern.catalog

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The audio half of [CatalogLintTest] — permanent rules over the REAL `catalog/audio/`,
 * a sibling file only because both stay inside the ~300-line budget that way.
 * Rules, never totals: every check iterates the shipped entries, so the suite is
 * vacuously green until the converter generates the manifests.
 *
 * `catalog/audio/` holds no `concepts.json`, so it stays invisible to
 * [CatalogLintTest.everyAreaFolderIsRegisteredInTheManifest].
 */
class CatalogAudioLintTest {
    private val catalog get() = RealCatalog.catalog
    private val audioRoot = File(RealCatalog.root, "audio")

    /** Authorship values that name nobody — a BY/BY-SA file carrying one cannot ship. */
    private val junkAuthors = setOf("own work", "myself", "")
    private val letterFileName = Regex("^letters/u[0-9a-f]{4}\\.mp3$")

    private fun forEachEntry(action: (lang: String, id: String, recording: AudioRecording) -> Unit) {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) action(lang, slug, recording)
            for ((glyph, recording) in manifest.letters) action(lang, glyph, recording)
        }
    }

    private fun realization(lang: String, slug: String): RawRealization? =
        catalog.areas.firstNotNullOfOrNull { it.realizations[lang]?.get(slug) }

    /**
     * An entry keyed by a slug the language does not realize is left over from a content
     * edit: it can never be reached, and its mp3 is dead weight. Regenerate the manifest.
     */
    @Test
    fun everyWordEntryNamesASlugItsLanguageRealizes() {
        for ((lang, manifest) in catalog.audio) {
            for (slug in manifest.words.keys) {
                assertTrue(realization(lang, slug) != null, "audio/$lang: \"$slug\" is not realized in $lang")
            }
        }
    }

    /**
     * The lookup is keyed by what the learner SEES, so an entry whose spoken form matches
     * no surface form of its realization ships bytes that can never play — the sw `ku-`
     * verb shape, which the converter drops instead.
     */
    @Test
    fun everyMatchesFormIsReachable() {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) {
                val raw = realization(lang, slug) ?: continue // reported by the rule above
                val forms = (listOf(raw.text) + raw.synonyms + raw.variants).map { speechKey(it) }
                assertTrue(
                    speechKey(checkNotNull(recording.matches)) in forms,
                    "audio/$lang/$slug: \"${recording.matches}\" reaches none of $forms",
                )
            }
        }
    }

    /**
     * Two entries may share a speech key only when their bytes are identical (one
     * recording fetched under two slugs). Differing bytes have no right answer, so the
     * runtime plays nothing — a silent card the converter must resolve at generation time.
     */
    @Test
    fun noAmbiguousMatchedForm() {
        for ((lang, manifest) in catalog.audio) {
            val bySpeechKey = manifest.words.entries.groupBy { speechKey(checkNotNull(it.value.matches)) }
            for ((key, group) in bySpeechKey) {
                val digests = group.mapTo(mutableSetOf()) { it.value.sha256 }
                assertEquals(1, digests.size, "audio/$lang: \"$key\" is claimed by ${group.map { it.key }}")
            }
        }
    }

    /**
     * Word files are slug-named; letter files are codepoint-named, never glyph-named —
     * `й`/`ї` decompose under NFD on APFS and Unicode filenames cross four toolchains.
     */
    @Test
    fun audioFileNamesFollowTheNamingRules() {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) {
                assertEquals("$slug.mp3", recording.file, "audio/$lang/$slug: file is not slug-named")
            }
            for ((glyph, recording) in manifest.letters) {
                val where = "audio/$lang letter \"$glyph\""
                assertEquals(1, glyph.codePointCount(0, glyph.length), "$where: not a single glyph")
                assertTrue(letterFileName.matches(recording.file), "$where: bad file \"${recording.file}\"")
                assertEquals(
                    "letters/u%04x.mp3".format(glyph.codePointAt(0)),
                    recording.file,
                    "$where: file does not name the glyph's codepoint",
                )
            }
        }
    }

    /** Every field the credits screen renders is present, and BY/BY-SA link their deed. */
    @Test
    fun audioEntryFieldsAreWellFormed() {
        forEachEntry { lang, id, recording ->
            val where = "audio/$lang/manifest.json $id"
            val required = mapOf(
                "file" to recording.file,
                "licence" to recording.licence,
                "author" to recording.author,
                "source" to recording.source,
                "sha256" to recording.sha256,
            )
            for ((field, value) in required) assertTrue(value.isNotBlank(), "$where: blank $field")
            if (recording.licence != "Public domain") {
                assertTrue(!recording.licenceUrl.isNullOrBlank(), "$where: ${recording.licence} needs a deed URL")
            }
        }
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) {
                assertTrue(!recording.matches.isNullOrBlank(), "audio/$lang/$slug: blank matches")
            }
            for ((glyph, recording) in manifest.letters) {
                assertEquals(null, recording.matches, "audio/$lang letter \"$glyph\": letters speak a name")
            }
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
                recording.author.trim().lowercase() !in junkAuthors,
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
            for (recording in manifest.words.values + manifest.letters.values) {
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
     * licence) group carrying its glyph and its Commons filename. The uk pack is asserted
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
                        it.language == lang && it.author == recording.author && it.licence == recording.licence
                    },
                    "$where: no single ${recording.licence} group for ${recording.author}",
                )
                assertTrue(
                    AudioCreditFile(glyph, recording.source) in group.files,
                    "$where: absent from its own credit group",
                )
            }
        }
    }

    /**
     * The index's RANGE needs no rule here: `AudioManifestParser` refuses a gain past ±20 dB
     * or a lead past 5 s outright, so a manifest carrying one never loads at all. What is
     * left to check is whether the numbers say what they were measured to say.
     *
     * The index exists BECAUSE the uk letters are quiet and start late (user ruling
     * 2026-08-01) — but its SIGN is a property of the pack it measured, never a rule: uk's
     * letters take a boost and a long lead skip, while de's letter names come out of the
     * same ordinary word recordings as its vocabulary and are ATTENUATED instead. What no
     * real pack produces is a whole alphabet measuring exactly 0 dB / 0 ms; that is the
     * analysis stage having been skipped, and the drill goes back to whispering a second
     * too late — which is exactly what nobody notices in a diff.
     */
    @Test
    fun everyLettersPackCarriesItsPlaybackIndex() {
        assertTrue(catalog.audio["uk"]?.letters?.isNotEmpty() == true, "uk ships no letter recordings")
        for ((lang, manifest) in catalog.audio) {
            if (manifest.letters.isEmpty()) continue
            assertTrue(
                manifest.letters.values.any { it.gain != 0.0 || it.leadMs > 0 },
                "audio/$lang: every letter measures 0 dB / 0 ms — the analysis stage did not run",
            )
        }
    }

    /**
     * The target IS the word packs' own median loudness, so half the word entries sit above
     * it and half below and the median gain is zero. A median that has drifted means the
     * manifests were generated against some other target than the one `ANALYSIS` records —
     * every pack would then be corrected toward a level no one chose.
     */
    @Test
    fun theWordPacksStayCentredOnTheAnalysisTarget() {
        val gains = catalog.audio.values.flatMap { manifest -> manifest.words.values.map { it.gain } }.sorted()
        assertTrue(gains.isNotEmpty(), "no word recordings ship")
        val median = gains[gains.size / 2]
        assertTrue(median in -1.0..1.0, "word gains centre on $median dB, not on the analysis target")
    }

    /**
     * The untouched-transcodes gate: Commons files ship byte-identical because
     * re-encoding is an adaptation under BY-SA. The converter writes the digest it
     * verified; this re-hashes what is actually committed.
     */
    @Test
    fun audioFilesMatchTheirManifestHashes() {
        for ((lang, manifest) in catalog.audio) {
            for (entry in manifest.words.entries + manifest.letters.entries) {
                val file = File(audioRoot, "$lang/${entry.value.file}")
                if (!file.isFile) continue // reported by everyAudioFileShipsAndIsReferencedExactlyOnce
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                assertEquals(entry.value.sha256, digest, "audio/$lang/${entry.value.file}: bytes differ (re-encoded?)")
            }
        }
    }
}
