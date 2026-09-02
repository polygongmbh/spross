package net.spross.kern.trainer

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.audible
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
 * kern's own [Catalog], the consolidated pool and its schedule figures are kern's own
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
        /** Consolidated, single-word, audible box cards, each carrying the figures the draw weighs. */
        val dictationCandidates: List<LetterDrill.DictationCandidate>,
        /** Ref → every word this device can say the row's gap from, known words flagged. */
        val gapWords: Map<String, List<LetterDrill.AlphabetExampleWord>>,
        /** The learner's whole consolidated vocabulary — what paces the entry Sprosse and its length. */
        val consolidatedCards: Int,
    ) {
        val drillAvailable: Boolean get() = alphabet != null && promptableRefs.isNotEmpty()

        val dictationAvailable: Boolean get() = dictationCandidates.size >= DICTATION_FLOOR

        /** The Sprosse ceiling: 9 where dictation exists, else 7. */
        val maxLevel: Int get() = LetterDrill.maxLevel(dictationAvailable)

        /**
         * Which Sprosse a run OPENS on — kern's step from the words the learner already holds,
         * capped by [maxLevel]. Derived here rather than at the run, so the overview naming
         * the stage and the run that starts there read one number.
         */
        val entryLevel: Int get() = minOf(LetterDrill.entryLevel(consolidatedCards), maxLevel)

        /** The stage that Sprosse lands in — what the overview marks. */
        val entryStage: LetterStage get() = LetterDrill.stageFor(entryLevel)

        /** How long a Sprosse is for this learner. */
        val winsToAdvance: Int get() = LetterDrill.winsToAdvance(consolidatedCards)

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
        val consolidated = BoxEngine.consolidatedCardIds(box).mapNotNull { box.cards[it] }
        // why: Card.id IS the concept slug, so holding a word is a set lookup.
        val known = consolidated.map { it.id }.toSet()
        val gapWords = alphabet?.entries.orEmpty()
            .filter { it.kind != AlphabetKind.Letter && it.kind != AlphabetKind.Rule }
            .associate { it.ref to exampleWords(it, catalog, language, known, hasVoice) }
        return Report(
            language = language,
            alphabet = alphabet,
            promptableRefs = alphabet?.entries.orEmpty()
                .filter { entry ->
                    promptable(entry, catalog, language, hasVoice) { gapWords[entry.ref].orEmpty() }
                }
                .map { it.ref },
            dictationCandidates = consolidated
                // why: a transcription task is ONE word — a phrase card would ask the learner
                // to type a sentence from a single hearing.
                .filter { ' ' !in it.target.text }
                .filter { audible(it.target.text, it.target.lang, catalog, hasVoice) }
                .map { card ->
                    val scheduling = box.scheduling[card.id]
                    LetterDrill.DictationCandidate(
                        card = card,
                        difficulty = scheduling?.memory?.difficulty ?: 0.0,
                        lapses = scheduling?.lapses ?: 0,
                    )
                },
            gapWords = gapWords,
            consolidatedCards = consolidated.size,
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
     * that concept's recording.
     *
     * QUIRK, shared by both platforms and kept deliberately: the `exampleText` fallback is NOT
     * audibility-filtered. An inaudible escape-hatch row stays promptable, and the drill then
     * shows a dead speaker. Filed in `docs/backlog.md` rather than fixed inside a port.
     */
    fun exampleWords(
        entry: AlphabetEntry,
        catalog: Catalog,
        language: Language,
        known: Set<String>,
        hasVoice: Boolean,
    ): List<LetterDrill.AlphabetExampleWord> {
        val swept = catalog.alphabetExamples(entry, language)
            .filter { audible(it.text, language, catalog, hasVoice) }
            .map { LetterDrill.AlphabetExampleWord(it.text, it.slug, it.slug in known) }
        if (swept.isNotEmpty()) return swept
        return entry.exampleText
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
