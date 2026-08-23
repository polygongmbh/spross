package net.spross.kern.catalog

import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized

/** One bundled recording as authored in `audio/<lang>/manifest.json`. */
internal data class AudioRecording(
    /** Path relative to `audio/<lang>/`: `<slug>.mp3`, or `letters/u<codepoint>….mp3`
     *  (one `u<cp>` per codepoint — a named row may be a digraph). */
    val file: String,
    /** The exact surface form the recording speaks; null for letters (they speak a name). */
    val matches: String?,
    /**
     * For an `articles{}` entry, the BARE form inside what it speaks — "Ausweis" out of
     * "der Ausweis"; null everywhere else, where [matches] is already bare.
     *
     * Carried rather than derived: stripping a leading article is a guess about where the
     * word starts, and an elided one ("l'acqua") has no space to cut at. It is what lets
     * one file answer both the card that asks with its article and the card that asks
     * without one.
     */
    val word: String?,
    /**
     * RESOLVED, never as authored: a manifest carries a license per AUTHOR and its deed
     * per license, and an entry names one only where it departs from its own author's
     * (`catalog/audio/README.md`). [licenseUrl] is null exactly where the license has no
     * deed to link — a public-domain file.
     */
    val license: String,
    val licenseUrl: String?,
    val author: String,
    val source: String,
    /** Hex digest of the shipped bytes — lint re-hashes the file against it. */
    val sha256: String,
    /**
     * The ANALYSIS INDEX: dB from the catalog's analysis targets, and dead air at the head
     * in ms. Both are MEASUREMENTS of the shipped bytes, never edits to them — the packs
     * were recorded by different people and differ by up to 20 dB, and re-encoding is an
     * adaptation under BY-SA. `gain` is the full-range plane; `gainPhone` the built-in phone
     * speaker's, null where no phone plane was measured (letters and texts) — see
     * [AudioIndex] for who picks which. Absent `gain` means 0, i.e. nothing to correct.
     */
    val gain: Double,
    val gainPhone: Double?,
    /**
     * What the converter's peak ceiling held back from [gain] — 0 where the loudness number
     * stood as measured. The cap is only true at FULL VOLUME, so a player that has already
     * attenuated may hand as much of this back as it has taken off (`fadedGainDb`); nothing
     * outside a fade ever reads it. [capPhone] is [gainPhone]'s own, and null in the same
     * places [gainPhone] is.
     */
    val cap: Double,
    val capPhone: Double?,
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
    /**
     * slug → a recording that speaks an ARTICLE and then the word, in manifest order.
     *
     * It is indexed twice: by the whole spoken form ([AudioRecording.matches], "der
     * Ausweis") which is the string [spokenTargetForm] builds for a card showing that
     * article, and by the bare word inside it ([AudioRecording.word], "Ausweis") so the
     * same file still answers a card that asks without one rather than leaving it silent.
     * The bare-word route is the LAST thing tried, so a recording of exactly what the card
     * shows always wins where the pack has one.
     *
     * A section of its own rather than a second `words` entry because both files ship for
     * one slug: the bare recording stays what the source side reads, where the article is
     * not what is being taught.
     */
    val articles: Map<String, AudioRecording>,
) {
    private val byExactForm: Map<String, AudioRecording?> = index { nfcNormalized(it) }
    private val bySpeechKey: Map<String, AudioRecording?> = index { speechKey(it) }
    private val articlesBySpeechKey: Map<String, AudioRecording?> =
        index(articles.values) { speechKey(it) }
    private val articlesByWord: Map<String, AudioRecording?> =
        articles.values
            .mapNotNull { recording -> recording.word?.let { speechKey(it) to recording } }
            .groupBy({ (key, _) -> key }, { (_, recording) -> recording })
            .mapValues { (_, group) ->
                if (group.mapTo(mutableSetOf()) { it.sha256 }.size == 1) group.first() else null
            }
    private val byGlyph: Map<String, AudioRecording> =
        letters.entries.associate { (glyph, recording) -> nfcNormalized(glyph) to recording }

    /** The recording that speaks [visibleForm], else null. */
    fun recording(visibleForm: String): AudioRecording? {
        val exact = nfcNormalized(visibleForm)
        if (exact in byExactForm) return byExactForm[exact]
        return bySpeechKey[speechKey(visibleForm)]
    }

    /**
     * The recording for [visibleForm] on the TARGET side, where [article] is the article the
     * card shows in front of it (null on the source side, and on anything a card rotated in).
     *
     * An article recording is preferred where one speaks that pair, because the article is
     * half of what knowing the noun means; where none does, the bare recording still answers
     * and the word is simply heard without it — never a bare recording dressed up as one, and
     * never an article said over a card not showing it.
     */
    fun recording(visibleForm: String, article: String?): AudioRecording? {
        if (!article.isNullOrBlank()) {
            val spoken = spokenTargetForm(article, visibleForm, visibleForm)
            articlesBySpeechKey[speechKey(spoken)]?.let { return it }
        }
        recording(visibleForm)?.let { return it }
        // Whatever is available: a file that says "der Ausweis" is still a recording OF
        // "Ausweis", so it answers a card asking for the bare word rather than leaving one
        // silent — last, because a recording of exactly what the card shows is the better
        // answer wherever the pack has one.
        return articlesByWord[speechKey(visibleForm)]
    }

    /** The letter's recording, NFC-folded so a decomposed glyph still resolves. */
    fun letterRecording(glyph: String): AudioRecording? = byGlyph[nfcNormalized(glyph)]

    /** Catalog-relative path of one of this manifest's recordings ("audio/uk/office.mp3"). */
    fun path(recording: AudioRecording): String = "audio/$language/${recording.file}"

    /**
     * (label, recording) for the credits screen, manifest order: words labeled by the
     * form they speak, then letters by their glyph, then the alphabet's own example words.
     */
    fun creditRows(): List<Pair<String, AudioRecording>> =
        words.values.mapNotNull { recording -> recording.matches?.let { it to recording } } +
            letters.map { (glyph, recording) -> glyph to recording } +
            texts.values.mapNotNull { recording -> recording.matches?.let { it to recording } } +
            articles.values.mapNotNull { recording -> recording.matches?.let { it to recording } }

    /** Spoken-form key → recording, or → null where entries disagree about non-identical bytes. */
    private fun index(
        recordings: Collection<AudioRecording> = words.values + texts.values,
        key: (String) -> String,
    ): Map<String, AudioRecording?> =
        recordings
            .mapNotNull { recording -> recording.matches?.let { key(it) to recording } }
            .groupBy({ (formKey, _) -> formKey }, { (_, recording) -> recording })
            .mapValues { (_, group) ->
                // why: one recording fetched under two slugs is the same bytes twice, so
                // either file speaks the right word; differing bytes have no right answer.
                if (group.mapTo(mutableSetOf()) { it.sha256 }.size == 1) group.first() else null
            }
}
