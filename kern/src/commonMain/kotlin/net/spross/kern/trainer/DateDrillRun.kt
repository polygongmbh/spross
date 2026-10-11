package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.model.EmojiCue
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerControls.Slot
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.Question
import net.spross.kern.session.QuestionAsk
import net.spross.kern.session.QuestionHint
import net.spross.kern.session.Saying
import net.spross.kern.session.typableOnNumberPad

/**
 * The dates drill's run: the shared pair drill reducer ([PairDrillRun]) over the calendars.
 * What it can ask is [DateDrill]; what the run is fixed to is [DateDrillRunConfig].
 */
object DateDrillRun : PairDrillRun<DateDrillTask>() {

    /**
     * Grade [input] against every reading the task accepts, the way a drill grades: no
     * article forgiven — the pattern authors its own
     * article, and its `accepts` are what admit the accusative.
     *
     * The Sprossen whose answer is a numeral carry the numbers drill's value check
     * ([NumberReadingIndex]): a day that names another day (`vierte` for `dritte`) is
     * refused and named, never forgiven. Nothing is inherited — the bare-name Sprossen have
     * no numeral to check, so they pass no index.
     *
     * The bare-name Sprossen carry the calendar's own instead ([DateNameIndex], on a miss
     * only): where the whole answer is the name, `Juli` typed for `Juni` is July and the
     * refusal says so. An assembled date keeps its bridge — a month slip inside one is a
     * typo by the owner's ruling, so the index is never consulted above the bare Sprossen.
     * The warm-up Sprosse consults neither: a tapped tile is a name the learner READ, so a
     * miss is nothing but the wrong one of four and the reveal already stands on the card.
     */
    fun grade(input: String, task: DateDrillTask, config: DateDrillRunConfig): Match {
        val match = gradeDrillAnswer(
            input = input,
            accepted = task.accepted,
            display = task.display,
            language = config.answerLanguage,
            cardId = "dates",
            normalizer = config.normalizer,
            index = numberIndex(task, config),
        )
        if (match == Match.Exact) return match
        return when (task.kind) {
            DateTaskKind.Weekday, DateTaskKind.Month ->
                config.nameIndex?.otherName(task, input) ?: match
            else -> match
        }
    }

    /**
     * The value check, on the Sprossen whose answer is a NUMERAL — a reading like `dritte`,
     * never a date like `3.6.`. Nothing is inherited: `gradeDrillAnswer` defaults to none,
     * and the bare-name and parsed Sprossen stay without one.
     */
    private fun numberIndex(task: DateDrillTask, config: DateDrillRunConfig): NumberReadingIndex? =
        when {
            // A date written in digits is graded exact-only by the normalizer itself, and an
            // index of NUMBER WORDS has nothing to say about `3.6.` either way.
            task.digits -> null
            task.kind == DateTaskKind.NameChoice -> null
            task.kind == DateTaskKind.Weekday || task.kind == DateTaskKind.Month -> null
            else -> config.normalizer?.let { NumberReadingIndex.of(config.answerLanguage, it) }
        }
}

/**
 * Everything one dates run is fixed to ([PairDrillRunConfig]): the joined calendars, handed
 * over once by the page that opened the run, and how its questions are drawn, graded and shown.
 */
class DateDrillRunConfig(
    val content: DateDrillContent,
    reverse: Boolean,
    fast: Boolean,
    normalizer: AnswerNormalizer?,
    standingRecord: Int = 0,
    cleared: Set<Int> = emptySet(),
) : PairDrillRunConfig<DateDrillTask>(
    content.source, content.target, reverse, fast, normalizer, standingRecord, cleared,
) {
    /** The calendar turned around for the refusal check — null exactly where [normalizer] is. */
    internal val nameIndex: DateNameIndex? by lazy {
        normalizer?.let { DateNameIndex(content, reverse, it) }
    }

    override val drill: Drill get() = Drill.Dates

    override val storageKey: String get() = DateDrill.storageKey(content.source, content.target)

    override val top: Int get() = DateDrill.maxSprosse(content, reverse)

    override fun draw(
        sprosse: Int,
        avoid: DateDrillTask?,
        solved: Set<String>,
        rng: Random,
        arriving: Boolean,
    ): DrillLadder.Sprosse<DateDrillTask> =
        DateDrill.draw(content, sprosse, reverse, avoid?.let { DrillSolved.key(it) }, solved, rng, arriving)
            .let { DrillLadder.Sprosse(it.task, it.sprosse) }

    override fun grade(input: String, task: DateDrillTask): Match = DateDrillRun.grade(input, task, this)

    override fun solvedKey(task: DateDrillTask): String = DrillSolved.key(task)

    override fun answeredOut(solved: Set<String>): Set<Int> = DateDrill.cleared(content, reverse, solved)

    /** Each assembled kind is introduced with its pattern word once. */
    override fun firstSight(task: DateDrillTask): String = task.kind.name

    /**
     * The word this language adds to assemble the question on screen, the first time it is
     * asked and never again — the numbers drill's first-sight hint ([Numbers.placeValueHint]),
     * for a pattern instead of a length, and always a word in the language being LEARNED.
     *
     * Nothing REVERSED: the card then carries the reading, which says the word already, and
     * the answer owed is digits.
     */
    private fun patternWord(run: PairDrillRunState<DateDrillTask>): String? {
        if (reverse || firstSight(run.task) in run.hinted) return null
        return DateDrill.patternWord(content, run.task.kind)
    }

    /** No picture; a date owed in digits is a numeral, and the pattern word is the first-sight hint. */
    override fun question(run: PairDrillRunState<DateDrillTask>): Question {
        val task = run.task
        return Question(
            key = run.index.toString(),
            ask = QuestionAsk.Date(task.kind),
            prompt = Question.Side(task.promptText, promptLanguage, Question.Form.Name, saying = run.promptSaying),
            answer = Question.Side(
                task.display, answerLanguage,
                if (task.digits) Question.Form.Numeral else Question.Form.Name,
                saying = Saying(task.display, answerLanguage),
            ),
            emoji = null,
            emojiCue = EmojiCue.Upfront,
            hint = patternWord(run)?.let { QuestionHint.NewWord(it) },
            opens = run.showsAnswer,
            otherWord = run.otherWord,
        )
    }

    /** The warm-up Sprosse's names are picked off tiles; every other date is written, a date owed in digits on the number pad where it fits. */
    override fun controls(run: PairDrillRunState<DateDrillTask>): AnswerControls {
        val task = run.task
        return run.answerControls(
            task.choices?.let { Slot.Choices(it, task.display) }
                ?: run.typedSlot(answerLanguage, digits = task.digits, numberPad = task.digits && typableOnNumberPad(task.accepted)),
        )
    }
}
