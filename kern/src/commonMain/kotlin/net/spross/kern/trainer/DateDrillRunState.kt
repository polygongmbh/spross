package net.spross.kern.trainer

import net.spross.kern.session.typableOnNumberPad
import net.spross.kern.session.AnswerControls.Slot
import net.spross.kern.session.AnswerControls
import net.spross.kern.model.EmojiCue
import net.spross.kern.session.Question
import net.spross.kern.session.QuestionAsk
import net.spross.kern.session.QuestionHint
import net.spross.kern.session.Saying
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * What the learner does to a dates run. Writing the reading out IS the answer, so a
 * keystroke is an intent of its own — the atlas run's vocabulary, unchanged.
 */
sealed class DateDrillIntent {
    /** A live keystroke: a reading finished exactly right needs no check tap. */
    data class InputChanged(val text: String) : DateDrillIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : DateDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : DateDrillIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : DateDrillIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : DateDrillIntent()

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : DateDrillIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class DateDrillReduction(val state: DateDrillRunState, val effects: List<DrillEffect>)

/**
 * What a closed dates run leaves behind: the figures for the page that started it, and the
 * furthest Sprosse it stood on for that page to file.
 */
data class DateDrillClose(
    val state: DateDrillRunState,
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /**
     * The Sprosse the run REACHED, not the one it ends on — the ramp drops back on a miss,
     * and the ladder rewards standing on a Sprosse rather than finishing there.
     */
    val bestSprosse: Int,
    /**
     * The Sprossen this run answered OUT before its first slip ([DrillSprossen]), for the page
     * to add to what it holds — the next run opens above them.
     * Unfiltered: unlike [bestSprosse] there is no standing value to beat.
     */
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
)

/**
 * Everything one dates run is fixed to, resolved when it opens and never per question:
 * the joined calendars, which way round they ask, how long a Sprosse is, and the grader.
 */
class DateDrillRunConfig(
    /** The joined calendars, handed over once by the page that opened the run. */
    val content: DateDrillContent,
    /** Which side prompts: forward asks in the language the learner knows. */
    val reverse: Boolean,
    /**
     * Whether a Sprosse falls on ONE clean win instead of three. Its price is
     * [DateDrill.fastUnlocked]'s and the page that opened the run has already paid it.
     */
    val fast: Boolean,
    /**
     * The STRICT drill grader for the language the answer is owed in — which is the
     * learner's OWN on a reversed run. Null (a preview with no language info) grades plainly.
     */
    val normalizer: AnswerNormalizer?,
    /**
     * The answer-streak record the platform's store holds for this page — beating it is what a pause
     * for improving names ([DrillPacing]); 0 where none stood yet.
     */
    val standingRecord: Int = 0,
    /**
     * The Sprossen earlier runs answered out in THIS run's direction, as the platform's store
     * holds them ([NumbersMode.clearedKey]) — answering out one it does not hold is what a
     * pause for improving names ([DrillPacing]).
     */
    val cleared: Set<Int> = emptySet(),
) {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = DateDrill.answerLanguage(content, reverse)

    /** The calendar turned around for the refusal check — null exactly where [normalizer] is. */
    internal val nameIndex: DateNameIndex? by lazy {
        normalizer?.let { DateNameIndex(content, reverse, it) }
    }

    /** The language the prompt is written in — the other side of the same pair. */
    val promptLanguage: Language get() = DateDrill.promptLanguage(content, reverse)
}

/**
 * One dates run, whole and immutable: the question on screen, what the answers have done
 * to the ladder, and the tallies the close reports.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the
 * focus, and hands text in through [DateDrillIntent]. What is in here is every rule that
 * decides what the text means.
 *
 * No FSRS and no box at all: the material is the catalog's calendars, not the learner's
 * own words, so nothing is scheduled and nothing is read. The one thing that outlives a
 * run is [bestSprosse], which the page that started it files.
 */
data class DateDrillRunState(
    val config: DateDrillRunConfig,
    /** The question on screen. A fresh calendar always has one — a Sprosse with none is none. */
    val task: DateDrillTask,
    /** Bumped per question — what the card's identity and an autoplay effect key on. */
    override val index: Int,
    val sprosse: Int,
    val bestSprosse: Int,
    val winsAtSprosse: Int,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    override val core: DrillRunCore,
    override val feedback: TurnFeedback,
    /** What a refused answer actually NAMED (Juli is July) — only beside a Revealed miss. */
    val otherWord: Match.OtherWord? = null,
    /** Assembled kinds already introduced with their pattern word; each is shown once. */
    val seenKinds: Set<DateTaskKind> = emptySet(),
    override val finished: Boolean,
) : DrillRunProgress {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = config.answerLanguage

    /**
     * The language the prompt is written in. Nothing on screen names it; it tags the
     * prompt for a screen reader, which is the reading such a user gets in place of autoplay.
     */
    val promptLanguage: Language get() = config.promptLanguage

    /**
     * The word this language adds to assemble the question on screen, the first time it is
     * asked and never again — the numbers drill's first-sight hint ([Numbers.placeValueHint]),
     * for a pattern instead of a length, and always a word in the language being LEARNED.
     *
     * Nothing REVERSED: the card then carries the reading, which says the word already, and
     * the answer owed is digits.
     */
    val patternWord: String?
        get() {
            if (config.reverse || task.kind in seenKinds) return null
            return DateDrill.patternWord(config.content, task.kind)
        }

    /** The Sprossen this run answered out that the store did not hold — what a pause for improving names. */
    internal val newSprossen: Int
        get() = (DateDrill.cleared(config.content, config.reverse, core.solvedClean) - config.cleared).size

    /** The card may open: the almost hold and the miss each put a reading worth seeing whole. */
    val showsAnswer: Boolean
        get() = feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed

    /** A reversed run's prompt, which is then the form in the language being learned; a forward one says nothing until the reveal. */
    override val promptSaying: Saying?
        get() = if (config.reverse) task.promptText?.let { Saying(it, promptLanguage) } else null

    /** The reading, where it is owed in the language being learned; a reversed run answers in the learner's own. */
    override val answerSaying: Saying?
        get() = if (config.reverse) null else Saying(task.display, answerLanguage)

    /** No picture; a date owed in digits is a numeral, and the pattern word is the first-sight hint. */
    override val question: Question
        get() = Question(
            key = index.toString(),
            ask = QuestionAsk.Date(task.kind),
            prompt = Question.Side(task.promptText, promptLanguage, Question.Form.Name, saying = promptSaying),
            answer = Question.Side(
                task.display, answerLanguage,
                if (task.digits) Question.Form.Numeral else Question.Form.Name,
                saying = Saying(task.display, answerLanguage),
            ),
            emoji = null,
            emojiCue = EmojiCue.Upfront,
            hint = patternWord?.let { QuestionHint.NewWord(it) },
            opens = showsAnswer,
            otherWord = otherWord,
        )

    /** The warm-up Sprosse's names are picked off tiles; every other date is written, a date owed in digits on the number pad where it fits. */
    override val controls: AnswerControls
        get() = answerControls(
            task.choices?.let { Slot.Choices(it, task.display) }
                ?: typedSlot(answerLanguage, digits = task.digits, numberPad = task.digits && typableOnNumberPad(task.accepted)),
        )
}
