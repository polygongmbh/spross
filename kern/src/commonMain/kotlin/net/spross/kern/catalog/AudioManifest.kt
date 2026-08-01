package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized

/** One bundled recording as authored in `audio/<lang>/manifest.json`. */
internal data class AudioRecording(
    /** Path relative to `audio/<lang>/`: `<slug>.mp3`, or `letters/u<codepoint>.mp3`. */
    val file: String,
    /** The exact surface form the recording speaks; null for letters (they speak a name). */
    val matches: String?,
    val licence: String,
    val licenceUrl: String?,
    val author: String,
    val source: String,
    /** Hex digest of the shipped bytes — lint re-hashes the file against it. */
    val sha256: String,
)

/**
 * One language's audio manifest. Recordings are addressed by the form they SPEAK,
 * never by the slug they were fetched for: what stands on the card is what has to
 * match, so a rotated synonym falls through to live speech instead of playing the
 * canonical word.
 *
 * Two indices are built once — the exact NFC form, then the [speechKey]; exact wins.
 * Entries whose [speechKey] collides while their bytes DIFFER index to null: with de
 * `husten` (cough / to cough) there is no right guess, so the card speaks its visible
 * form instead. Shipping that state is a build error (`CatalogAudioLintTest`); the
 * converter resolves collisions when it generates the manifest.
 */
internal class AudioManifest(
    val language: Language,
    /** slug → recording, in manifest order. */
    val words: Map<String, AudioRecording>,
    /** lowercase glyph → recording, in manifest order. */
    val letters: Map<String, AudioRecording>,
) {
    private val byExactForm: Map<String, String?> = index { nfcNormalized(it) }
    private val bySpeechKey: Map<String, String?> = index { speechKey(it) }
    private val byGlyph: Map<String, String> =
        letters.entries.associate { (glyph, recording) -> nfcNormalized(glyph) to path(recording) }

    /** Catalog-relative path of the recording that speaks [visibleForm], else null. */
    fun recordingPath(visibleForm: String): String? {
        val exact = nfcNormalized(visibleForm)
        if (exact in byExactForm) return byExactForm[exact]
        return bySpeechKey[speechKey(visibleForm)]
    }

    /** The letter's recording, NFC-folded so a decomposed glyph still resolves. */
    fun letterPath(glyph: String): String? = byGlyph[nfcNormalized(glyph)]

    /**
     * (label, recording) for the credits screen, manifest order: words labelled by the
     * form they speak, then letters by their glyph.
     */
    fun creditRows(): List<Pair<String, AudioRecording>> =
        words.values.mapNotNull { recording -> recording.matches?.let { it to recording } } +
            letters.map { (glyph, recording) -> glyph to recording }

    private fun path(recording: AudioRecording): String = "audio/$language/${recording.file}"

    /** Spoken-form key → path, or → null where entries disagree about non-identical bytes. */
    private fun index(key: (String) -> String): Map<String, String?> =
        words.values
            .mapNotNull { recording -> recording.matches?.let { key(it) to recording } }
            .groupBy({ (formKey, _) -> formKey }, { (_, recording) -> recording })
            .mapValues { (_, group) ->
                // why: one recording fetched under two slugs is the same bytes twice, so
                // either file speaks the right word; differing bytes have no right answer.
                if (group.mapTo(mutableSetOf()) { it.sha256 }.size == 1) path(group.first()) else null
            }
}

internal object AudioManifestParser {
    private val WORD_KEYS = setOf("file", "matches", "licence", "licenceUrl", "author", "source", "sha256")
    private val LETTER_KEYS = WORD_KEYS - "matches"

    fun parse(path: String, text: String, expectedLanguage: Language): AudioManifest {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("language", "words", "letters"))
        val language = root.requireString(path, "root", "language")
        if (language != expectedLanguage) {
            parseError(path, "declares language \"$language\", expected \"$expectedLanguage\"")
        }
        return AudioManifest(
            language = language,
            words = section(path, root, "words", WORD_KEYS),
            letters = section(path, root, "letters", LETTER_KEYS),
        )
    }

    /** Parses one keyed section; `matches` is required exactly where the key set allows it. */
    private fun section(
        path: String,
        root: JsonObject,
        key: String,
        known: Set<String>,
    ): Map<String, AudioRecording> {
        val section = root[key]?.obj(path, key) ?: return emptyMap()
        return section.entries.associate { (id, element) ->
            val context = "$key.$id"
            val entry = element.obj(path, context)
            entry.rejectUnknownKeys(path, context, known)
            id to AudioRecording(
                file = entry.requireNonBlank(path, context, "file"),
                matches = if ("matches" in known) entry.requireNonBlank(path, context, "matches") else null,
                licence = entry.requireNonBlank(path, context, "licence"),
                licenceUrl = entry.optionalString(path, context, "licenceUrl"),
                author = entry.requireNonBlank(path, context, "author"),
                source = entry.requireNonBlank(path, context, "source"),
                sha256 = entry.requireNonBlank(path, context, "sha256"),
            )
        }
    }

    private fun JsonObject.requireNonBlank(path: String, context: String, key: String): String =
        requireString(path, context, key).also {
            if (it.isBlank()) parseError(path, "$context: blank \"$key\"")
        }
}
