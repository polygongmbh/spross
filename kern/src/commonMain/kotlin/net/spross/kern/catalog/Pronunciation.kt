package net.spross.kern.catalog

import net.spross.kern.model.Language
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
 * Typewriter, curly, and the modifier letter — the same apostrophe class the alphabet
 * grading folds: an INNER apostrophe is part of the word (uk ім'я, fr s'habiller), but
 * which codepoint spells it is typography — Commons titles French elision with U+2019
 * while the catalog writes U+0027, and the two must key one sound.
 */
private val APOSTROPHES = setOf('\u0027', '\u2019', '\u02bc')

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
    return if (key.none { it in APOSTROPHES }) key
    else key.map { if (it in APOSTROPHES) '\u02bc' else it }.joinToString("")
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
 * It lives beside [utterance] because it is the same question — what string does the
 * synthesizer get — and answering it in two places would let a prefixed form skip the stem
 * trim. It is deliberately NOT applied to a bundled recording: a recording says what was
 * recorded, and re-cutting one to add an article is an edit to bytes kern never edits. That
 * the two branches then sound different is the accepted cost; the recording is the branch
 * falling short, not the voice overreaching.
 *
 * [shownArticle] is what decides there is one to say: a rotated synonym may carry another
 * gender, so it gets none rather than a wrong one.
 */
fun spokenTargetForm(article: String?, shownForm: String, targetText: String): String {
    val spoken = utterance(shownForm)
    val prefix = shownArticle(article, shownForm, targetText)?.trim()?.takeIf { it.isNotEmpty() }
    return if (prefix == null) spoken else "$prefix $spoken"
}

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
    /** Decibels from the full-range analysis target: positive is quiet, negative is loud. */
    val gain: Double

    /**
     * Decibels from the phone-speaker target — the same loudness through the speaker lens,
     * what a built-in phone speaker can radiate. Null means no phone plane was measured
     * (letters and texts), so the full-range [gain] stands on either route. A platform picks
     * `gainPhone ?: gain` on the phone speaker and `gain` elsewhere, never minting a number
     * of its own.
     */
    val gainPhone: Double?

    /** Dead air at the head of the file, in ms — start here and the recording speaks at once. */
    val leadMs: Long
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
    /** Catalog-relative path of the recording ("audio/uk/office.mp3"), null → synthesize. */
    val recordingPath: String?,
    override val gain: Double = 0.0,
    override val gainPhone: Double? = null,
    override val leadMs: Long = 0,
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
    override val gainPhone: Double? = null,
    override val leadMs: Long,
) : AudioIndex

/** One credited recording: [label] is the form it speaks, or the letter's glyph. */
data class AudioCreditFile(
    val label: String,
    /** The original Commons filename — the credits screen links `File:<source>`. */
    val source: String,
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
