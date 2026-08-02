package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized

/** One bundled recording as authored in `audio/<lang>/manifest.json`. */
internal data class AudioRecording(
    /** Path relative to `audio/<lang>/`: `<slug>.mp3`, or `letters/u<codepoint>….mp3`
     *  (one `u<cp>` per codepoint — a named row may be a digraph). */
    val file: String,
    /** The exact surface form the recording speaks; null for letters (they speak a name). */
    val matches: String?,
    val licence: String,
    val licenceUrl: String?,
    val author: String,
    val source: String,
    /** Hex digest of the shipped bytes — lint re-hashes the file against it. */
    val sha256: String,
    /**
     * The ANALYSIS INDEX: dB from the catalog's analysis target, and dead air at the head
     * in ms. Both are MEASUREMENTS of the shipped bytes, never edits to them — the packs
     * were recorded by different people and differ by up to 20 dB, and re-encoding is an
     * adaptation under BY-SA. Absent in the manifest means 0, i.e. nothing to correct.
     */
    val gain: Double,
    val leadMs: Long,
    /**
     * Peak minus noise floor in dB — how far the word stands above the hiss under it.
     * A MEASUREMENT like the other two, but one nothing plays: it exists so lint can see
     * the shape of a pack and refuse a rebuild that quietly reintroduces noise a previous
     * one removed. 0.0 where the converter recorded none.
     */
    val snr: Double,
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
    /**
     * form → recording for the alphabet's `exampleText` words, in manifest order. They
     * carry no slug — that is what an `exampleText` IS — so they are keyed and matched by
     * the form they speak, and share [words]' form index: the lookup a card does and the
     * lookup the sheet does are the same lookup, and neither can play a recording over a
     * word it does not say.
     */
    val texts: Map<String, AudioRecording>,
) {
    private val byExactForm: Map<String, AudioRecording?> = index { nfcNormalized(it) }
    private val bySpeechKey: Map<String, AudioRecording?> = index { speechKey(it) }
    private val byGlyph: Map<String, AudioRecording> =
        letters.entries.associate { (glyph, recording) -> nfcNormalized(glyph) to recording }

    /** The recording that speaks [visibleForm], else null. */
    fun recording(visibleForm: String): AudioRecording? {
        val exact = nfcNormalized(visibleForm)
        if (exact in byExactForm) return byExactForm[exact]
        return bySpeechKey[speechKey(visibleForm)]
    }

    /** The letter's recording, NFC-folded so a decomposed glyph still resolves. */
    fun letterRecording(glyph: String): AudioRecording? = byGlyph[nfcNormalized(glyph)]

    /** Catalog-relative path of one of this manifest's recordings ("audio/uk/office.mp3"). */
    fun path(recording: AudioRecording): String = "audio/$language/${recording.file}"

    /**
     * (label, recording) for the credits screen, manifest order: words labelled by the
     * form they speak, then letters by their glyph, then the alphabet's own example words.
     */
    fun creditRows(): List<Pair<String, AudioRecording>> =
        words.values.mapNotNull { recording -> recording.matches?.let { it to recording } } +
            letters.map { (glyph, recording) -> glyph to recording } +
            texts.values.mapNotNull { recording -> recording.matches?.let { it to recording } }

    /** Spoken-form key → recording, or → null where entries disagree about non-identical bytes. */
    private fun index(key: (String) -> String): Map<String, AudioRecording?> =
        (words.values + texts.values)
            .mapNotNull { recording -> recording.matches?.let { key(it) to recording } }
            .groupBy({ (formKey, _) -> formKey }, { (_, recording) -> recording })
            .mapValues { (_, group) ->
                // why: one recording fetched under two slugs is the same bytes twice, so
                // either file speaks the right word; differing bytes have no right answer.
                if (group.mapTo(mutableSetOf()) { it.sha256 }.size == 1) group.first() else null
            }
}

internal object AudioManifestParser {
    private val WORD_KEYS =
        setOf("file", "matches", "licence", "licenceUrl", "author", "source", "sha256",
              "gain", "lead", "snr")
    private val LETTER_KEYS = WORD_KEYS - "matches"

    /** The converter clamps here: past 10× amplitude the index is likelier wrong than the file. */
    private const val GAIN_LIMIT_DB = 20.0

    /** Five seconds of dead air is not a lead-in, it is the wrong recording. */
    private const val LEAD_LIMIT_MS = 5_000L

    fun parse(path: String, text: String, expectedLanguage: Language): AudioManifest {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("language", "words", "letters", "texts"))
        val language = root.requireString(path, "root", "language")
        if (language != expectedLanguage) {
            parseError(path, "declares language \"$language\", expected \"$expectedLanguage\"")
        }
        return AudioManifest(
            language = language,
            words = section(path, root, "words", WORD_KEYS),
            letters = section(path, root, "letters", LETTER_KEYS),
            texts = section(path, root, "texts", WORD_KEYS),
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
                gain = entry.gain(path, context),
                leadMs = entry.leadMs(path, context),
                snr = entry.optionalDouble(path, context, "snr") ?: 0.0,
            )
        }
    }

    private fun JsonObject.requireNonBlank(path: String, context: String, key: String): String =
        requireString(path, context, key).also {
            if (it.isBlank()) parseError(path, "$context: blank \"$key\"")
        }

    /** Absent means the recording already sits at the analysis target. */
    private fun JsonObject.gain(path: String, context: String): Double {
        val gain = optionalDouble(path, context, "gain") ?: return 0.0
        if (gain !in -GAIN_LIMIT_DB..GAIN_LIMIT_DB) {
            parseError(path, "$context: gain $gain dB is outside ±$GAIN_LIMIT_DB")
        }
        return gain
    }

    /** Absent means the recording starts speaking at once. */
    private fun JsonObject.leadMs(path: String, context: String): Long {
        val lead = optionalLong(path, context, "lead") ?: return 0
        if (lead !in 0..LEAD_LIMIT_MS) {
            parseError(path, "$context: lead $lead ms is outside 0..$LEAD_LIMIT_MS")
        }
        return lead
    }
}
