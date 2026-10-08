package net.spross.kern.catalog

import net.spross.kern.model.Language
import net.spross.kern.model.apostropheFolded
import net.spross.kern.model.articledForm
import net.spross.kern.model.nfcNormalized
import net.spross.kern.model.shownArticle

/**
 * Characters a written form may carry at its edges but no one ever says —
 * sentence punctuation plus the quote marks a citation picks up. `¡`/`¿` belong
 * here for the same reason `!`/`?` do: Spanish writes them, nobody pronounces
 * them, and a recording of "hola" has to answer a card reading "¡Hola!".
 */
private const val EDGE_PUNCTUATION = "!?¡¿.,;:…\"'«»„“”‘’‹›"

/**
 * The normative speech normalization (`kern/docs/audio.md`): trim whitespace, strip ONE
 * leading `-` (the adjective stem citation Swahili authors as `-zuri`), strip leading and
 * trailing sentence punctuation, NFC, lowercase, and fold the inner apostrophe class
 * to U+02BC.
 *
 * Applied IDENTICALLY to a manifest's `matches` when the index is built and to the
 * visible form at lookup, so a recording of "hallo" answers a card showing "Hallo!"
 * and one of "zuri" answers "-zuri" — those edges are spelling, not speech.
 * Case folding is locale-independent: no Turkish-i language is in scope.
 */
fun speechKey(form: String): String {
    val stem = form.trim().removePrefix("-")
    val key = nfcNormalized(stem.trim { it.isWhitespace() || it in EDGE_PUNCTUATION }).lowercase()
    return apostropheFolded(key)
}

/**
 * [form] without the leading optional verb prefix it starts with (sw `kupiga simu` →
 * `piga simu`, from `LanguageInfo.optionalVerbPrefixes`), else null: the bare stem a
 * recording may say where none says the citation form the card shows.
 */
fun verbStem(form: String, prefixes: List<String>): String? {
    val trimmed = form.trim()
    return prefixes.firstOrNull { trimmed.length > it.length && trimmed.startsWith(it, ignoreCase = true) }
        ?.let { trimmed.substring(it.length) }
}

/**
 * What a synthesizer is handed for [form]: the leading stem `-` removed (synthesizers
 * vocalize it — "minus zuri"), terminal punctuation KEPT, because it carries prosody.
 * Never a normalization — what is spoken stays the form the learner sees.
 */
fun utterance(form: String): String = form.trim().removePrefix("-").trim()

/**
 * What a synthesizer is handed for a TARGET form: [utterance]'s form with the card's article
 * in front of it, or the form alone.
 *
 * Target-language speech gains its article; source-language speech does not. An article is
 * grammar the learner has to hear — in German it is half of what knowing a noun means, and a
 * word met a hundred times as a bare stem is a word learned wrong. This reverses
 * `docs/read-aloud.md`'s "only the headword is ever spoken", and it applies wherever a target
 * word is synthesized, not only in listening mode.
 *
 * It lives beside [utterance] because it is the same question — what string is this word
 * SAID as — and answering it in two places would let a prefixed form skip the stem trim.
 * The synthesizer is handed it, and a bundled ARTICLE recording is looked up by it
 * ([AudioManifest.recording]): a file that speaks "der Ausweis" answers exactly the card
 * this builds that string for. It is never applied to the bytes of a recording — re-cutting
 * one to add an article is an edit kern does not make — so a word with no article recording
 * still plays bare, and only that word sounds shorter than the voice would say it.
 *
 * [shownArticle] is what decides there is one to say — a rotated synonym may carry another
 * gender, so it gets none rather than a wrong one — and [articledForm] writes the join,
 * elision included ("l'acqua", the recording's own title `It-l'acqua.ogg`: one sound, one key).
 */
fun spokenTargetForm(article: String?, shownForm: String, targetText: String): String =
    articledForm(shownArticle(article, shownForm, targetText), utterance(shownForm))

/**
 * How a bundled recording is PLAYED — the measured half of the manifest, beside the
 * provenance half the credits screen reads. The packs come from different people on
 * different equipment and share no loudness, and the uk letters open with a second of dead
 * air before they speak; re-encoding them is an adaptation under BY-SA, so the shipped
 * bytes stay the untouched Commons transcode and the correction travels as MEASUREMENT
 * DATA applied at playback. Whether a player realizes a gain by boosting or by attenuating
 * is its own business: the number means the same either way, and 0/0 is "play as it is".
 */
interface AudioIndex {
    /** Decibels from the analysis target: positive is quiet, negative is loud. */
    val gain: Double

    /**
     * What the converter's peak ceiling held back from [gain] — 0 where the loudness number
     * stood as measured. A player under a fade may hand back as much of it as the fade has
     * already taken off (`fadedGainDb`); at full volume it is headroom that does not exist
     * and nothing reads it.
     */
    val cap: Double

    /** Dead air at the head of the file, in ms — start here and the recording speaks at once. */
    val leadMs: Long

    /**
     * The recording's own noise level plus a margin, in dBFS of the RAW decoded file before
     * any gain: a downward expander with its threshold here quiets the hiss in pauses and
     * between syllables and leaves the word alone. A player whose gate sits after its gain
     * stage asks [Playback.gateThresholdDb] for the threshold. Null means no gate — the file
     * measured as digital silence, nothing was measured, or nothing plays.
     */
    val gate: Double?
}

/**
 * What to say for one target form, resolved against the bundled recordings.
 * Recordings are canonical; [recordingPath] null means the app speaks [utterance] live,
 * and then the index is 0/0 — a synthesizer needs no correcting.
 */
data class Pronunciation( // data class: Swift sees value equality
    /** The form as it stands on the card — a rotated synonym prompts as itself. */
    val form: String,
    val utterance: String,
    val lang: Language,
    /** Catalog-relative path of the recording ("audio/de/words/hund.mp3"), null → synthesize. */
    val recordingPath: String?,
    override val gain: Double = 0.0,
    override val cap: Double = 0.0,
    override val leadMs: Long = 0,
    override val gate: Double? = null,
) : AudioIndex

/**
 * A letter's recording and how to play it — the letters' [Pronunciation], which they cannot
 * share: what is written (р) and what is said («ер») are different strings, so the manifest
 * is addressed by the glyph and the NAME belongs to the alphabet file, not to audio.
 */
data class LetterRecording(
    /** Catalog-relative path ("audio/uk/letters/u0440.mp3"). */
    val path: String,
    override val gain: Double,
    override val cap: Double = 0.0,
    override val leadMs: Long,
    override val gate: Double? = null,
) : AudioIndex

/** One credited recording: [label] is the form it speaks, or the letter's glyph. */
data class AudioCreditFile(
    val label: String,
    /** The original Commons filename — the credits screen links `File:<source>`. */
    val source: String,
    /**
     * The recording itself, so its credit row can play it: [label] as the form, the file's
     * own path and index. The credits screen plays this file and never a voice in its place.
     */
    val pronunciation: Pronunciation,
)

/**
 * Every recording one author contributed to one language under one license.
 * BY and BY-SA never share a notice, so the license is part of the grouping key.
 */
data class AudioCredit(
    val language: Language,
    val author: String,
    val license: String,
    /** Canonical deed URL; null for public-domain files, which have no deed to link. */
    val licenseUrl: String?,
    val files: List<AudioCreditFile>,
)
