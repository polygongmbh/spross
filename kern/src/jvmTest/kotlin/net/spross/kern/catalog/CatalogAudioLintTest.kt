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

    /**
     * Commons' wording for a file whose authorship nobody recorded: the name is the
     * uploader, inferred from the copyright tag. It reads like a credit and is a guess,
     * which is the one thing a BY/BY-SA notice may not be.
     */
    private val assumedAuthor = Regex("assumed \\(based on copyright claims\\)", RegexOption.IGNORE_CASE)
    private val letterFileName = Regex("^letters/(u[0-9a-f]{4})+\\.mp3$")

    private fun forEachEntry(action: (lang: String, id: String, recording: AudioRecording) -> Unit) {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.words) action(lang, slug, recording)
            for ((glyph, recording) in manifest.letters) action(lang, glyph, recording)
            for ((form, recording) in manifest.texts) action(lang, form, recording)
            for ((slug, recording) in manifest.articles) action(lang, "$slug (article)", recording)
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
        }
    }

    /** The converter's file-stem rule: [a-z0-9-] survives, everything else becomes `u<hex>`. */
    private fun asciiStem(form: String): String = buildString {
        for (ch in form.trim().lowercase()) {
            if (ch.code < 128 && (ch.isLetterOrDigit() || ch == '-')) append(ch)
            else append("u%04x".format(ch.code))
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
                manifest.texts.values + manifest.articles.values) {
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
     * How clean a pack is, as a DISTRIBUTION rather than a floor per file.
     *
     * A per-file minimum cannot be written honestly: twelve German rows sit under 30 dB
     * because Commons has nothing cleaner for those words, and a rule that fails the build
     * over an unimprovable file is a rule that gets suppressed. What a rebuild must not do
     * is quietly undo the sweep that removed the hiss — a whole pack sliding down, or the
     * bad tail growing. Both are visible in the shape and neither goes stale as content
     * grows. Today: medians de 85, es 57, sw 51, uk 45; worst tail de at 3.7%.
     *
     * `snr` changes no playback. It is carried purely so this can be asserted.
     */
    @Test
    fun noPackLosesItsRecordingQuality() {
        for ((lang, manifest) in catalog.audio) {
            val measured = (manifest.words.values + manifest.letters.values +
                manifest.texts.values + manifest.articles.values)
                .map { it.snr }.filter { it != 0.0 }
            assertTrue(measured.size > 10, "audio/$lang: only ${measured.size} entries carry an snr")
            val median = measured.sorted()[measured.size / 2]
            assertTrue(median >= 40.0, "audio/$lang: median snr $median dB has fallen below 40")
            val hissy = measured.count { it < 30.0 }
            assertTrue(
                hissy * 100 <= measured.size * 5,
                "audio/$lang: $hissy of ${measured.size} entries are under 30 dB — over 5%",
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
    fun theWordPacksStayCenteredOnTheAnalysisTarget() {
        val gains = catalog.audio.values.flatMap { manifest -> manifest.words.values.map { it.gain } }.sorted()
        assertTrue(gains.isNotEmpty(), "no word recordings ship")
        val median = gains[gains.size / 2]
        assertTrue(median in -1.0..1.0, "word gains center on $median dB, not on the analysis target")
    }

    /**
     * The phone-plane twin of [theWordPacksStayCenteredOnTheAnalysisTarget]: `speaker_lufs`
     * is the packs' own median loudness through the lens, so the `gainPhone` figures center
     * on zero the same way. Every word carries one (the generator writes 0.0 rather than
     * omitting it — a player must be able to tell "no phone correction" from "no phone
     * plane", which is the letters/texts case), so a missing field is itself the drift.
     */
    @Test
    fun theWordPacksStayCenteredOnThePhoneTarget() {
        val phoneGains = catalog.audio.values
            .flatMap { manifest -> manifest.words.values.map { it.gainPhone } }
        assertTrue(phoneGains.isNotEmpty(), "no word recordings ship")
        assertTrue(phoneGains.none { it == null }, "a word ships without its phone-plane gain")
        val sorted = phoneGains.filterNotNull().sorted()
        val median = sorted[sorted.size / 2]
        assertTrue(median in -1.0..1.0, "phone gains center on $median dB, not on the speaker target")
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
                manifest.texts.entries + manifest.articles.entries) {
                val file = File(audioRoot, "$lang/${entry.value.file}")
                if (!file.isFile) continue // reported by everyAudioFileShipsAndIsReferencedExactlyOnce
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                assertEquals(entry.value.sha256, digest, "audio/$lang/${entry.value.file}: bytes differ (re-encoded?)")
            }
        }
    }
}
