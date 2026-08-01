package net.spross.kern.catalog

import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized

/**
 * Characters a written form may carry at its edges but no one ever says —
 * sentence punctuation plus the quote marks a citation picks up. `¡`/`¿` belong
 * here for the same reason `!`/`?` do: Spanish writes them, nobody pronounces
 * them, and a recording of "hola" has to answer a card reading "¡Hola!".
 */
private const val EDGE_PUNCTUATION = "!?¡¿.,;:…\"'«»„“”‘’‹›"

/**
 * The normative speech normalization (README §11): trim whitespace, strip ONE leading
 * `-` (the adjective stem citation Swahili authors as `-zuri`), strip leading and
 * trailing sentence punctuation, NFC, lowercase.
 *
 * Applied IDENTICALLY to a manifest's `matches` when the index is built and to the
 * visible form at lookup, so a recording of "hallo" answers a card showing "Hallo!"
 * and one of "zuri" answers "-zuri" — those edges are spelling, not speech.
 * Case folding is locale-independent: no Turkish-i language is in scope.
 */
fun speechKey(form: String): String {
    val stem = form.trim().removePrefix("-")
    return nfcNormalized(stem.trim { it.isWhitespace() || it in EDGE_PUNCTUATION }).lowercase()
}

/**
 * What a synthesizer is handed for [form]: the leading stem `-` removed (synthesizers
 * vocalize it — "minus zuri"), terminal punctuation KEPT, because it carries prosody.
 * Never a normalization — what is spoken stays the form the learner sees.
 */
fun utterance(form: String): String = form.trim().removePrefix("-").trim()

/**
 * What to say for one target form, resolved against the bundled recordings.
 * Recordings are canonical; [recordingPath] null means the app speaks [utterance] live.
 */
data class Pronunciation( // data class: Swift sees value equality
    /** The form as it stands on the card — a rotated synonym prompts as itself. */
    val form: String,
    val utterance: String,
    val lang: Language,
    /** Catalog-relative path of the recording ("audio/uk/office.mp3"), null → synthesize. */
    val recordingPath: String?,
)

/** One credited recording: [label] is the form it speaks, or the letter's glyph. */
data class AudioCreditFile(
    val label: String,
    /** The original Commons filename — the credits screen links `File:<source>`. */
    val source: String,
)

/**
 * Every recording one author contributed to one language under one licence.
 * BY and BY-SA never share a notice, so the licence is part of the grouping key.
 */
data class AudioCredit(
    val language: Language,
    val author: String,
    val licence: String,
    /** Canonical deed URL; null for public-domain files, which have no deed to link. */
    val licenceUrl: String?,
    val files: List<AudioCreditFile>,
)
