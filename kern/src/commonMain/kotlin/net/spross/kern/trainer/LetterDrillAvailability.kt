package net.spross.kern.trainer

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.alphabet
import net.spross.kern.catalog.alphabetExamples
import net.spross.kern.catalog.audible
import net.spross.kern.catalog.letterRecordingPath
import net.spross.kern.model.Language

/**
 * What the letter drill can ASK on THIS device.
 *
 * Two facts, neither of them content: which alphabet rows can be HEARD at all (a bundled
 * letter recording, or a voice for the language), and which of the words the learner already
 * holds can be dictated. [LetterDrill] samples from what this reports and never asks whether
 * a device can speak.
 *
 * The only platform fact the whole ladder consults is `hasVoice` — recording presence is
 * kern's own [Catalog], the arrived pool and its schedule figures are kern's own
 * [BoxState]. So the audio-capability port collapses to one boolean, named by the rule
 * ("can this device say anything in this language") rather than by any synthesizer.
 *
 * Nothing here is cached: a voice may be installed in Settings while the app sleeps, so the
 * REBUILD TRIGGER stays the platform's (a foreground notification, a recomposition on the
 * synthesizer's readiness) and this answers freshly every time it is asked.
 */
object LetterDrillAvailability {

    /**
     * Below this many candidates the resample-once rule degenerates into the same word all
     * evening: dictation does not exist yet and the ramp stops one Sprosse short of it. The
     * drill itself still exists.
     */
    const val DICTATION_FLOOR: Int = 5

    /**
     * Everything a run draws from, built ONCE per run: it is a catalog sweep, and a
     * per-question rebuild would re-audit every candidate's audio for a single draw.
     */
    data class Report(
        val language: Language,
        /** The parsed alphabet; null where no file is authored — file presence IS the registry. */
        val alphabet: Alphabet?,
        /** Refs kern may sample, in file order. */
        val promptableRefs: List<String>,
        /** Arrived, single-word, audible box cards, each carrying the figures the draw weighs. */
        val dictationCandidates: List<LetterDrill.DictationCandidate>,
        /** Ref → every word this device can say the row's gap from, arrived words flagged. */
        val gapWords: Map<String, List<LetterDrill.AlphabetExampleWord>>,
    ) {
        val drillAvailable: Boolean get() = alphabet != null && promptableRefs.isNotEmpty()

        val dictationAvailable: Boolean get() = dictationCandidates.size >= DICTATION_FLOOR

        /** The Sprosse ceiling: 9 where dictation exists, else 7. */
        val maxSprosse: Int get() = LetterDrill.maxSprosse(dictationAvailable)

        /**
         * Which Sprosse a run OPENS on: the lowest one no run has answered out
         * ([NumbersMode.entrySprosse] over [cleared], the store's mask) — the drill's own
         * progress and nothing else. Derived
         * here rather than at the run, so the overview marking the format and the run that
         * starts there read one number.
         */
        fun openingSprosse(cleared: Set<Int>): Int =
            NumbersMode.entrySprosse(cleared, maxSprosse)

        /** The format that Sprosse lands in — what the overview marks. */
        fun openingFormat(cleared: Set<Int>): LetterFormat = LetterDrill.formatFor(openingSprosse(cleared))

        /** Whether some run answered out every Sprosse of [format] — never dictation's. */
        fun formatCleared(format: LetterFormat, cleared: Set<Int>): Boolean =
            format != LetterFormat.Dictation && LetterDrill.sprossen(format).all { it in cleared }

        /** What kern is handed for one row — empty for a letter row, which gaps nothing. */
        fun examples(entry: AlphabetEntry): List<LetterDrill.AlphabetExampleWord> =
            gapWords[entry.ref].orEmpty()
    }

    /**
     * The full report. [hasVoice] is whether this device can say ANYTHING in [language],
     * answered by the platform's synthesizer at call time — the one device fact kern cannot
     * know, and a snapshot in time: a voice installed mid-run is only seen on the next build.
     */
    fun report(catalog: Catalog, box: BoxState, language: Language, hasVoice: Boolean): Report {
        val alphabet = catalog.alphabet(language)
        val arrived = BoxEngine.arrivedCardIds(box).mapNotNull { box.cards[it] }
        // why: Card.id IS the concept slug, so holding a word is a set lookup.
        val arrivedIds = arrived.map { it.id }.toSet()
        val gapWords = alphabet?.entries.orEmpty()
            .filter { it.kind != AlphabetKind.Letter && it.kind != AlphabetKind.Rule }
            .associate { it.ref to exampleWords(it, catalog, language, arrivedIds, hasVoice) }
        return Report(
            language = language,
            alphabet = alphabet,
            promptableRefs = alphabet?.entries.orEmpty()
                .filter { entry ->
                    promptable(entry, catalog, language, hasVoice) { gapWords[entry.ref].orEmpty() }
                }
                .map { it.ref },
            dictationCandidates = arrived
                // why: a transcription task is ONE word — a phrase card would ask the learner
                // to type a sentence from a single hearing.
                .filter { ' ' !in it.target.text }
                .filter { audible(it.target.text, it.target.lang, catalog, hasVoice) }
                .map { card ->
                    val scheduling = box.scheduling[card.id]
                    LetterDrill.DictationCandidate(
                        card = card,
                        difficulty = scheduling?.memory?.difficulty ?: 0.0,
                    )
                },
            gapWords = gapWords,
        )
    }

    /**
     * Whether the drill exists at all — the hub-chip predicate, and the only question a card
     * on a list that recomposes constantly should have to ask.
     *
     * The box is deliberately NOT walked: dictation decides a Sprosse ceiling, never whether the
     * drill exists. The sweep stays lazy behind [promptable], so the cheap letter half answers
     * first and a catalog walk happens only where no letter of the language can be said.
     */
    fun drillExists(catalog: Catalog, language: Language, hasVoice: Boolean): Boolean {
        val alphabet = catalog.alphabet(language) ?: return false
        return alphabet.entries.any { entry ->
            promptable(entry, catalog, language, hasVoice) {
                exampleWords(entry, catalog, language, emptySet(), hasVoice)
            }
        }
    }

    /**
     * Every word kern may gap for an entry, WITH its provenance: a slug only where the target
     * language realizes the concept itself, so an `exampleText` escape hatch can never claim
     * that concept's recording. The escape hatch is held to the same hearing test as the
     * swept words: a row nothing on this device can say is not asked.
     */
    fun exampleWords(
        entry: AlphabetEntry,
        catalog: Catalog,
        language: Language,
        arrivedIds: Set<String>,
        hasVoice: Boolean,
    ): List<LetterDrill.AlphabetExampleWord> {
        val swept = catalog.alphabetExamples(entry, language)
            .filter { audible(it.text, language, catalog, hasVoice) }
            .map { LetterDrill.AlphabetExampleWord(it.text, it.slug, it.slug in arrivedIds) }
        if (swept.isNotEmpty()) return swept
        return entry.exampleText
            ?.takeIf { audible(it, language, catalog, hasVoice) }
            ?.let { listOf(LetterDrill.AlphabetExampleWord(it, null, false)) }
            .orEmpty()
    }

    /**
     * A letter is asked by its NAME (a bundled recording, or the voice), a gap row by one of
     * its example WORDS, of which at least one must have survived.
     *
     * The one predicate this cannot repeat is whether the glyph sits in that word exactly
     * once — `gapWord` is internal to the catalog package. Lint pins it on shipped content and
     * [LetterDrill.sample] filters the pool on the same rule, so a gap that cannot be cut
     * costs a pool entry, never a question.
     */
    private fun promptable(
        entry: AlphabetEntry,
        catalog: Catalog,
        language: Language,
        hasVoice: Boolean,
        words: () -> List<LetterDrill.AlphabetExampleWord>,
    ): Boolean {
        if (!entry.drill || entry.kind == AlphabetKind.Rule) return false
        if (entry.kind == AlphabetKind.Letter) {
            // why: the NAME is what is spoken — a row without one cannot be asked even where
            // its recording exists, and kern's own sweep drops it too.
            if (entry.name == null) return false
            return catalog.letterRecordingPath(language, entry.glyph.lowercase()) != null || hasVoice
        }
        return words().isNotEmpty()
    }
}
