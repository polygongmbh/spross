package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.catalog.speechKey
import net.spross.kern.model.Card
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.Match
import net.spross.kern.trainer.DrillRamp
import net.spross.kern.trainer.LetterDrill
import net.spross.kern.trainer.LetterDrillTask
import net.spross.kern.trainer.LetterStage

/** What the last answer earned. Amber ([LetterCorrection]) moves the ramp neither way. */
sealed interface LetterFeedback {
    data object Idle : LetterFeedback

    /** Correct; [correction] holds the line that also withholds ramp progress. */
    data class Correct(val correction: LetterCorrection?) : LetterFeedback

    /** The answer stands — a miss, or the learner asking to see it. */
    data class Revealed(val answer: String) : LetterFeedback
}

/** Why an answer was amber: a slip worth spelling out, or a form that did not play. */
data class LetterCorrection(val kind: Kind, val form: String) {
    enum class Kind { Typo, Heard }
}

/**
 * A letter-drill run: the state one screen shows and the rules it books answers by,
 * kept out of the composition the way kern's `SessionRun` keeps the review loop out of it.
 *
 * Everything decidable lives in kern — the ladder, the draw, the ramp step, typed
 * grading. What is left here is the run itself: which question stands, what the last
 * answer earned, and the counters the summary reads. No review is ever booked (D12):
 * the box is READ, for the pacing figures and the dictation pool, and never written.
 *
 * [silence] fires at the top of every path that ends a question, so a clip can never
 * follow the learner onto the next one — the same cut the review loop makes in
 * `answerCurrent`.
 */
class LetterDrillFlow(
    private val availability: LetterDrillAvailability,
    consolidatedCards: Int,
    /** The real cards, for dictation grading identity. */
    private val cards: Map<String, Card>,
    private val dictationGrader: CatalogAnswerGrader?,
    private val silence: () -> Unit,
    private val rng: Random = Random.Default,
) {
    /** 9 where dictation exists, else 7. */
    val maxLevel: Int = LetterDrill.maxLevel(availability.dictationAvailable)

    /** How long a rung is — kern's step function, not this class's. */
    private val winsRequired: Int = LetterDrill.winsToAdvance(consolidatedCards)

    var level by mutableStateOf(minOf(LetterDrill.entryLevel(consolidatedCards), maxLevel))
        private set
    private var winsAtLevel = 0

    /** The question on screen; null only once nothing can be asked any more. */
    var task by mutableStateOf(sample(level, null, null))
        private set

    /**
     * Bumped on every new question. What the autoplay effect keys on: two questions in a
     * row can be equal values, and a key that compares equal would not fire again.
     */
    var index by mutableStateOf(0)
        private set

    var input by mutableStateOf("")
    var feedback by mutableStateOf<LetterFeedback>(LetterFeedback.Idle)
        private set
    var chosen by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(0)
        private set
    var streak by mutableStateOf(0)
        private set
    var bestStreak by mutableStateOf(0)
        private set

    /** The run is over: the summary stands in place of a question. */
    var finished by mutableStateOf(false)
        private set

    /** One attempt per tile — a second tap after the answer is in would be a retry. */
    fun choose(glyph: String) {
        val current = task ?: return
        if (chosen != null || feedback != LetterFeedback.Idle) return
        silence()
        chosen = glyph
        feedback = if (glyph == current.display) {
            LetterFeedback.Correct(null)
        } else {
            LetterFeedback.Revealed(current.display)
        }
    }

    /** Reveal on an empty field: the answer goes INTO the field, and it books a miss. */
    fun reveal() {
        val current = task ?: return
        if (feedback != LetterFeedback.Idle) return
        silence()
        input = current.display
        feedback = LetterFeedback.Revealed(current.display)
    }

    fun submit() {
        val current = task ?: return
        val typed = input.trim()
        if (feedback != LetterFeedback.Idle || typed.isEmpty()) return
        silence()
        feedback = verdict(typed, current)
    }

    /** The one button after an answer: it books exactly what the verdict already said. */
    fun next() {
        when (val current = feedback) {
            LetterFeedback.Idle -> Unit
            is LetterFeedback.Correct -> advance(correct = true, clean = current.correction == null)
            is LetterFeedback.Revealed -> advance(correct = false, clean = true)
        }
    }

    /**
     * Books the answer, steps the rung through kern, and puts the next question up. An
     * amber answer ([clean] false) moves the rung neither way.
     */
    fun advance(correct: Boolean, clean: Boolean) {
        silence()
        val step = DrillRamp.step(level, winsAtLevel, correct, clean, maxLevel, winsRequired)
        val following = sample(
            step.level,
            task?.answerRef,
            task?.let { if (it.gapText == null) null else it.promptText },
        )
        level = step.level
        winsAtLevel = step.winsAtLevel
        if (correct) {
            streak += 1
            bestStreak = maxOf(bestStreak, streak)
        } else {
            streak = 0
        }
        done += 1
        // why: cleared with the question itself — the next one must never render a frame
        // carrying the last one's answer.
        input = ""
        feedback = LetterFeedback.Idle
        chosen = null
        task = following
        index += 1
        // Nothing left to ask: end on the summary, never on a blank card.
        if (following == null) finished = true
    }

    /**
     * Leaving mid-run. A pending answer books exactly as tapping Weiter would, so closing
     * can neither lose it nor upgrade it. False ⇒ nothing happened yet and the screen has
     * no summary to show.
     */
    fun close(): Boolean {
        silence()
        next()
        if (done == 0) return false
        finished = true
        return true
    }

    private fun verdict(typed: String, current: LetterDrillTask): LetterFeedback {
        val card = cards[current.answerRef]
        val grader = dictationGrader
        if (current.stage != LetterStage.Dictation || card == null || grader == null) {
            // Exact after normalization, no typo budget: a one-glyph answer with a slip
            // allowance grades nothing at all.
            return if (LetterDrill.gradeLetter(typed, current)) {
                LetterFeedback.Correct(null)
            } else {
                LetterFeedback.Revealed(current.display)
            }
        }
        val match = grader.grade(typed, LetterDrill.dictationGradingCard(card, current))
        if (match is Match.Exact) return LetterFeedback.Correct(null)
        // why: BEFORE the grader's own verdict — the review flow explicitly teaches these
        // forms ("auch: …"), so a synonym of the dictated word is never wrong and never
        // somebody else's word. It simply is not what played, and the line says which did.
        if (alsoAccepted(typed, card)) {
            return LetterFeedback.Correct(LetterCorrection(LetterCorrection.Kind.Heard, current.display))
        }
        return when (match) {
            is Match.Typo -> LetterFeedback.Correct(LetterCorrection(LetterCorrection.Kind.Typo, match.corrected))
            else -> LetterFeedback.Revealed(current.display)
        }
    }

    /** A form the REAL card lists as a synonym or a variant. */
    private fun alsoAccepted(input: String, card: Card): Boolean {
        val typed = speechKey(input)
        return (card.target.synonyms + card.target.variants).any { speechKey(it) == typed }
    }

    /**
     * One question at [level]: dictation draws from the box, every other stage from the
     * alphabet. [avoiding] is the previous answer and [avoidingWord] the word it gapped,
     * each of which kern resamples once.
     */
    private fun sample(level: Int, avoiding: String?, avoidingWord: String?): LetterDrillTask? {
        if (LetterDrill.stageFor(level) == LetterStage.Dictation &&
            availability.dictationCandidates.isNotEmpty()
        ) {
            return LetterDrill.sampleDictation(
                availability.dictationCandidates,
                availability.alphabet,
                level,
                avoiding,
                rng,
            )
        }
        val alphabet = availability.alphabet ?: return null
        if (availability.promptableRefs.isEmpty()) return null
        return LetterDrill.sample(
            alphabet,
            availability::examples,
            level,
            availability.promptableRefs,
            avoiding,
            avoidingWord,
            rng,
        )
    }
}

/**
 * A run, or null where this device can ask nothing at all — the hub gates on the same
 * predicate, so a null here is a closed door rather than a screen.
 */
fun AppModel.newLetterDrill(): LetterDrillFlow? {
    val availability = letterDrillAvailability() ?: return null
    if (!availability.drillAvailable) return null
    val cat = catalog ?: return null
    val state = box ?: return null
    val info = cat.languages[availability.language] ?: return null
    return LetterDrillFlow(
        availability = availability,
        consolidatedCards = stats?.consolidatedCount ?: 0,
        cards = state.cards,
        // why: the STRICT drill normalizer (no article leniency, a slip per word) with the
        // whole join in view — a per-word budget alone accepts `kufungua` for `kufunga`,
        // and only the catalog-wide grader withdraws that credit (§4.5).
        dictationGrader = CatalogAnswerGrader(
            AnswerNormalizer(info, articleLeniency = false, maxTyposPerWord = 1),
            state.cards.values.toList(),
        ),
        silence = { pronouncer.stop() },
    )
}
