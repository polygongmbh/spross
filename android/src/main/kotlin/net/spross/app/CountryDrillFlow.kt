package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.Realization
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerTone
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.CountryDrill
import net.spross.kern.trainer.CountryDrillTask
import net.spross.kern.trainer.DrillEffect
import net.spross.kern.trainer.DrillRunSummary

/**
 * One atlas run as this platform holds it — the third sibling of [TrainerFlow] and
 * [LetterDrillFlow], kept out of the composition for the same reason: a run a test can
 * drive without a device.
 *
 * Every DECISION is kern's [CountryDrill] — which question a rung may ask, which row is
 * drawn, how far one answer moves the ramp, what counts as an accepted form, when Fast is
 * on offer. What is left here is the platform's half, and it is the same half the iOS twin
 * (`CountryDrillView`) holds: the text in the field, the beat that is armed, and the
 * tallies a closing run hands back.
 *
 * Stateless like both its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's atlas, not the learner's own words. The one thing
 * that outlives a run is the furthest rung it stood on, which the page that started it
 * files ([TrainerStore.bookRung]).
 */
class CountryDrillFlow(
    /** The joined atlas, handed over once by the page that opened the run. */
    private val content: CountryDrillContent,
    /** Which way round the questions are asked, settled before a task is built. */
    val reverse: Boolean,
    /**
     * Whether a rung falls on ONE clean win instead of three. Its price is kern's
     * ([CountryDrill.fastUnlocked]) and the page has already paid it; this only obeys.
     */
    private val fast: Boolean,
    /** Kern's strict drill grader for the language answered in; null (previews) grades plainly. */
    private val normalizer: AnswerNormalizer?,
    private val rng: Random,
    /**
     * The rung the run OPENS on. Every run opens at 1 however far the learner has climbed —
     * what the record buys is the page, never a head start — so this exists for tests and
     * screenshot drivers, which have no other way to reach the outer tiers.
     */
    startLevel: Int = 1,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) {
    private val beat = DrillBeat(screenReaderOn)
    private val acts = DrillActs(beat, onTone, onReleaseFocus, onSilence)

    /** The question on screen. */
    var task by mutableStateOf(CountryDrill.sample(content, startLevel, reverse, null, rng))
        private set

    /** Which question this is, counted from zero — what the card's identity is keyed on. */
    var index by mutableStateOf(0)
        private set

    /** The rung the run stands on right now; the ramp drops it back on a miss. */
    var level by mutableStateOf(startLevel.coerceIn(1, CountryDrill.MAX_LEVEL))
        private set

    /**
     * The furthest rung the run REACHED — what the record books, since the rung it ends on
     * is not the one it climbed to.
     */
    var bestLevel by mutableStateOf(level)
        private set

    var streak by mutableStateOf(0)
        private set

    var bestStreak by mutableStateOf(0)
        private set

    var done by mutableStateOf(0)
        private set

    /** Per-question results, for the segmented bar the endless chrome draws. */
    var outcomes by mutableStateOf(emptyList<AnswerTone>())
        private set

    var feedback by mutableStateOf<TurnFeedback>(TurnFeedback.Neutral)
        private set

    /** The learner's answer text — kern owns what it means, the field is ours. */
    var input by mutableStateOf("")
        private set

    private var winsAtLevel = 0

    /**
     * Misses already booked in a row — the one on screen is not among them, so 1 while a
     * miss stands means this is the second in a row.
     */
    private var missRun = 0

    val armedBeat get() = beat.tier

    val beatToken get() = beat.token

    /** The beat became a tap: render the explicit "Weiter", which books the same answer. */
    val awaitsConfirm get() = beat.awaitsConfirm

    /** Nothing decided yet — the answer is still the learner's to produce. */
    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    /** The card may open: both amber holds and a miss put the answer on it. */
    val showsAnswer: Boolean
        get() = feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed

    /** Clean hits, for the counter an endless run keeps instead of a total. */
    val cleanCount: Int get() = outcomes.count { it == AnswerTone.Right }

    /**
     * The way out, offered where it is wanted rather than on a schedule: under the button
     * that goes on, on the SECOND miss in a row — the rule both sibling drills follow.
     */
    val offersFinish: Boolean get() = feedback == TurnFeedback.Revealed && missRun >= 1

    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = if (reverse) content.source else content.target

    /**
     * The language the prompt is written in. Nothing on screen names it; it tags the name
     * for TalkBack, which is the reading a screen-reader user gets in place of autoplay.
     */
    val promptLanguage: Language get() = if (reverse) content.target else content.source

    /**
     * A live keystroke. Writing the name out exactly IS the answer, with no check tap — the
     * review loop's rule ([net.spross.kern.session.TurnMachine]), and the one a learner
     * arrives here already knowing.
     *
     * EXACT only, where an explicit check still forgives a slip: the typo budget would fire
     * a letter early and grade the name before it was finished, and a real slip has to
     * pause on its correction anyway. Backing out of a finished name takes the green with
     * it, so typing PAST the answer never books it.
     */
    fun type(text: String) {
        input = text
        if (feedback != TurnFeedback.Neutral && feedback != TurnFeedback.Correct) return
        if (grade(text) != Match.Exact) {
            if (feedback != TurnFeedback.Correct) return
            feedback = TurnFeedback.Neutral
            acts.carryOut(listOf(DrillEffect.CancelAdvance))
            return
        }
        val cue = if (feedback == TurnFeedback.Correct) emptyList() else listOf(DrillEffect.Tone(ToneKind.Correct))
        feedback = TurnFeedback.Correct
        acts.carryOut(cue + DrillEffect.ArmAdvance(AdvanceTier.Live))
    }

    /** The ONE primary action: an empty field reveals (and books a miss), a typed one checks. */
    fun primary() {
        if (input.isBlank()) reveal() else submit()
    }

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter() {
        if (owesAnswer) primary() else confirm()
    }

    /** The tap that books whatever the feedback already said — and the beat's stand-in. */
    fun confirm() {
        when (feedback) {
            TurnFeedback.Correct -> advance(correct = true, clean = true)
            // The amber hold: accepted, but the pause showed a spelling, so the rung stays.
            is TurnFeedback.Almost -> advance(correct = true, clean = false)
            // why: no "I knew it" in a drill — the questions are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> advance(correct = false, clean = true)
            TurnFeedback.Neutral -> Unit
        }
    }

    fun advanceElapsed() {
        beat.spend()
        confirm()
    }

    /**
     * Leaving, from the corner or from "Fertig". A pending accepted answer books first,
     * exactly as the tap would — closing may neither lose it nor upgrade it — and an
     * untouched run leaves nothing to report.
     */
    fun close(standingRecord: Int): CountryDrillClose {
        beat.cancel()
        if (feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost) confirm()
        input = ""
        acts.carryOut(listOf(DrillEffect.Silence))
        val summary = if (done == 0) {
            null
        } else {
            DrillRunSummary(done = done, bestStreak = bestStreak, newRecord = bestStreak > standingRecord)
        }
        return CountryDrillClose(summary = summary, bestLevel = bestLevel)
    }

    // MARK: - Grading

    /**
     * "Aufdecken" on an empty field: the CARD carries the answer and the question books as a
     * miss. The field stays empty — it has nothing of the learner's to show, and typing the
     * answer in for them would put the same word on screen twice.
     */
    private fun reveal() {
        feedback = TurnFeedback.Revealed
        acts.carryOut(listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)))
    }

    private fun submit() {
        if (!owesAnswer || input.isBlank()) return
        when (val match = grade(input)) {
            Match.Exact -> {
                feedback = TurnFeedback.Correct
                acts.carryOut(
                    listOf(
                        DrillEffect.Silence,
                        DrillEffect.Tone(ToneKind.Correct),
                        DrillEffect.ArmAdvance(AdvanceTier.Explicit),
                    ),
                )
            }
            // why: no beat on a slip — the pause shows the proper spelling and waits for the
            // tap that books it amber, so the keyboard has to give the button back.
            is Match.Typo -> {
                feedback = TurnFeedback.Almost(match.corrected, AlmostReason.Typo)
                acts.carryOut(
                    listOf(
                        DrillEffect.Silence,
                        DrillEffect.Tone(ToneKind.Correct),
                        DrillEffect.ReleaseFocus,
                    ),
                )
            }
            else -> {
                feedback = TurnFeedback.Revealed
                acts.carryOut(listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)))
            }
        }
    }

    /**
     * Grade against every form kern accepts, through the STRICT drill normalizer — no
     * article leniency (the atlas authors "die Schweiz" and the bare form beside it), one
     * slip per word. Never [Match.OtherWord]: the accepted set is wrapped as one synthetic
     * card, so there is no catalog for another concept's word to come out of.
     */
    private fun grade(text: String): Match {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Match.Wrong
        val words = normalizer ?: return plainVerdict(trimmed)
        return when (val match = words.evaluate(trimmed, gradingCard())) {
            Match.Exact -> Match.Exact
            is Match.Typo -> match
            is Match.OtherWord, Match.Wrong -> Match.Wrong
        }
    }

    /** The accepted forms wrapped as one synthetic card, exactly as the slot run wraps its own. */
    private fun gradingCard(): Card {
        val side = Realization(
            lang = answerLanguage,
            text = task.accepted.firstOrNull() ?: task.display,
            synonyms = task.accepted.drop(1),
        )
        return Card(
            id = "atlas",
            kind = CardKind.Noun,
            area = "atlas",
            emoji = null,
            seedIndex = 0,
            components = emptyList(),
            feminineOf = null,
            baseAccepted = emptyList(),
            source = side,
            target = side,
            promptFeminineMarker = false,
        )
    }

    /** No language info (previews): a plain case- and punctuation-insensitive comparison. */
    private fun plainVerdict(trimmed: String): Match {
        val typed = plainForm(trimmed)
        return if (task.accepted.any { plainForm(it) == typed }) Match.Exact else Match.Wrong
    }

    private fun plainForm(raw: String): String = raw.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    // MARK: - The ramp

    /**
     * Books the answer, steps the rung through kern, and puts the next question up.
     * [clean] false is the amber hold: it moves the rung neither way.
     */
    private fun advance(correct: Boolean, clean: Boolean) {
        val step = CountryDrill.step(level, winsAtLevel, correct, clean, fast)
        // why: sampled BEFORE the id it must avoid is replaced — kern resamples once so a
        // repeat needs two unlucky draws rather than one.
        val next = CountryDrill.sample(content, step.level, reverse, task.id, rng)
        level = step.level
        bestLevel = maxOf(bestLevel, step.level)
        winsAtLevel = step.winsAtLevel
        if (correct) {
            streak += 1
            bestStreak = maxOf(bestStreak, streak)
            missRun = 0
        } else {
            streak = 0
            missRun += 1
        }
        outcomes = outcomes + if (correct) {
            if (clean) AnswerTone.Right else AnswerTone.Tough
        } else {
            AnswerTone.Wrong
        }
        done += 1
        // why: cleared in the SAME transaction as the question — the next card must never
        // render one frame carrying the last one's answer.
        input = ""
        feedback = TurnFeedback.Neutral
        task = next
        index += 1
        acts.carryOut(listOf(DrillEffect.CancelAdvance, DrillEffect.Silence))
    }
}

/** What a closed atlas run hands back to the page that started it. */
data class CountryDrillClose(
    /** The figures, or null where nothing was answered — then the run simply closes. */
    val summary: DrillRunSummary?,
    /** The furthest rung the run stood on; the store keeps the higher of it and what stands. */
    val bestLevel: Int,
)

/**
 * The run the atlas page opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one, built for the language the answer is owed in —
 * which is the learner's OWN language on a reversed run, so the direction is settled here
 * rather than assumed to be the target.
 */
fun AppModel.newCountryDrill(
    reverse: Boolean,
    fast: Boolean,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): CountryDrillFlow? {
    val content = atlas ?: return null
    val answerLanguage = if (reverse) content.source else content.target
    val info = catalog?.languages?.get(answerLanguage) ?: return null
    return CountryDrillFlow(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = AnswerNormalizer.drill(info),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}
