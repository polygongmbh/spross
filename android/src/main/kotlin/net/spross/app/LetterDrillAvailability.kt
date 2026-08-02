package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.Catalog
import net.spross.kern.model.Card
import net.spross.kern.model.Language
import net.spross.kern.trainer.LetterDrill

/**
 * What the letter drill can ASK on THIS device — the app half of the drill, derived the
 * same way on both platforms (contract §5.1/§5.2; the iOS twin is
 * `LetterDrillAvailability.swift`).
 *
 * Two facts, neither of them content: which alphabet rows can be HEARD at all (a bundled
 * letter recording, or a voice for the language), and which of the words the learner
 * already holds can be dictated. Kern samples from what this reports and never asks
 * whether a device can speak.
 */
data class LetterDrillAvailability(
    val language: Language,
    /** Parsed alphabet, null where no file is authored for the language. */
    val alphabet: Alphabet?,
    /** Refs kern may sample, in file order. */
    val promptableRefs: List<String>,
    /** Consolidated, single-word, audible box cards — the dictation pool. */
    val dictationCandidates: List<Card>,
    /**
     * Ref → every word this device can say the row's gap from, known words flagged. Built
     * ONCE per run: it is a catalog sweep, and a per-question rebuild would re-audit every
     * candidate's audio for a single draw.
     */
    val gapWords: Map<String, List<LetterDrill.AlphabetExampleWord>> = emptyMap(),
) {
    /** What kern is handed for one row — empty for a letter row, which gaps nothing. */
    fun examples(entry: AlphabetEntry): List<LetterDrill.AlphabetExampleWord> =
        gapWords[entry.ref].orEmpty()

    val drillAvailable: Boolean get() = alphabet != null && promptableRefs.isNotEmpty()

    val dictationAvailable: Boolean get() = dictationCandidates.size >= DICTATION_FLOOR

    companion object {
        /**
         * Below this many candidates the resample-once rule degenerates into the same
         * word all evening: dictation does not exist yet and the ramp stops one rung
         * short of it. The drill itself still exists.
         */
        const val DICTATION_FLOOR = 5
    }
}

/**
 * Whether the letters chip belongs on Heute at all.
 *
 * OBSERVABLE by construction: every fact it reads — the catalog, the box, and the
 * synthesizer's readiness — is Compose state, so the affordance appears BY ITSELF the
 * moment `TextToSpeech` finishes binding. Asked once at first composition it would answer
 * "no voice" on every cold start and hide the platform's only trainer for the whole run.
 *
 * The box is deliberately NOT walked here: dictation decides a rung ceiling, never
 * whether the drill exists, and Heute recomposes far more often than a run opens.
 */
val AppModel.letterDrillAvailable: Boolean
    get() {
        val language = box?.joinStamp?.target ?: return false
        val cat = catalog ?: return false
        val alphabet = cat.alphabet(language) ?: return false
        val voice = pronouncer.canSpeak(language)
        // why: the sweep is lazy behind `promptable`, so the cheap letter half answers
        // first and a chip on Heute costs a catalog walk only where no letter can be said.
        return alphabet.entries.any { entry ->
            promptable(entry, language, voice, cat) {
                alphabetExampleWords(entry, language, emptySet(), voice, cat)
            }
        }
    }

/** Everything a run needs to draw from; null before a box is joined. */
fun AppModel.letterDrillAvailability(): LetterDrillAvailability? {
    val language = box?.joinStamp?.target ?: return null
    val cat = catalog ?: return null
    val alphabet = cat.alphabet(language)
    val voice = pronouncer.canSpeak(language)
    val consolidated = consolidatedCandidates()
    // why: Card.id IS the concept slug, so holding a word is a set lookup.
    val known = consolidated.map { it.id }.toSet()
    val gapWords = alphabet?.entries.orEmpty()
        .filter { it.kind != AlphabetKind.Letter && it.kind != AlphabetKind.Rule }
        .associate { it.ref to alphabetExampleWords(it, language, known, voice, cat) }
    return LetterDrillAvailability(
        language = language,
        alphabet = alphabet,
        promptableRefs = alphabet?.entries.orEmpty()
            .filter { entry -> promptable(entry, language, voice, cat) { gapWords[entry.ref].orEmpty() } }
            .map { it.ref },
        dictationCandidates = consolidated
            // why: a transcription task is ONE word — a phrase card would ask the
            // learner to type a sentence from a single hearing.
            .filter { ' ' !in it.target.text }
            .filter { audible(it.target.text, it.target.lang, voice, cat) },
        gapWords = gapWords,
    )
}

/** The words the learner already holds, by kern's rule — never re-derived here (D11). */
fun AppModel.consolidatedCandidates(): List<Card> {
    val state = box ?: return emptyList()
    return BoxEngine.consolidatedCardIds(state).mapNotNull { state.cards[it] }
}

/**
 * Every word kern may gap for an entry, WITH its provenance: a slug only where the target
 * language realizes the concept itself, so an `exampleText` escape hatch can never claim
 * that concept's recording. One definition, because availability and sampling must agree
 * on it.
 *
 * Kern's sweep decides WHICH words qualify; this decides which of them the device can
 * actually say, and which the learner already holds.
 */
fun alphabetExampleWords(
    entry: AlphabetEntry,
    language: Language,
    known: Set<String>,
    voice: Boolean,
    catalog: Catalog,
): List<LetterDrill.AlphabetExampleWord> {
    val swept = catalog.alphabetExamples(entry, language)
        .filter { audible(it.text, language, voice, catalog) }
        .map { LetterDrill.AlphabetExampleWord(it.text, it.slug, it.slug in known) }
    if (swept.isNotEmpty()) return swept
    return entry.exampleText?.let { listOf(LetterDrill.AlphabetExampleWord(it, null)) }.orEmpty()
}

/**
 * A letter is asked by its NAME (a bundled recording, or the voice), a digraph by a gap
 * WORD, of which [words] must offer at least one.
 *
 * The one predicate this cannot repeat is whether the glyph sits in that word exactly
 * once — `gapWord` is internal to kern's catalog package. Lint pins it on shipped content
 * and kern filters its own pool on the same rule, so a gap that cannot be cut costs a
 * pool entry, never a question.
 */
private fun promptable(
    entry: AlphabetEntry,
    language: Language,
    voice: Boolean,
    catalog: Catalog,
    words: () -> List<LetterDrill.AlphabetExampleWord>,
): Boolean {
    if (!entry.drill || entry.kind == AlphabetKind.Rule) return false
    if (entry.kind == AlphabetKind.Letter) {
        // why: the NAME is what is spoken — a row without one cannot be asked even where
        // its recording exists, and kern drops it too.
        if (entry.name == null) return false
        return catalog.letterRecordingPath(language, entry.glyph.lowercase()) != null || voice
    }
    return words().isNotEmpty()
}

/** Whether a form can be heard at all: a recording that speaks THIS very form, or a voice. */
private fun audible(form: String, language: Language, voice: Boolean, catalog: Catalog): Boolean =
    catalog.pronunciation(language, form).recordingPath != null || voice
