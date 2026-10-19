package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.model.ClosingNote
import net.spross.kern.model.emojiCue
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.Question
import net.spross.kern.session.QuestionAsk
import net.spross.kern.session.Saying

/**
 * The atlas drill's run: the shared pair drill reducer ([PairDrillRun]) over the atlas.
 * What it can ask is [CountryDrill]; what the run is fixed to is [CountryDrillRunConfig].
 */
object CountryDrillRun : PairDrillRun<CountryDrillTask>() {

    /**
     * Grade [input] against every form the task accepts, the way a drill grades: no
     * article forgiven — the atlas authors "die Schweiz" and the
     * bare form beside it, so leniency would accept an article the learner never wrote.
     *
     * [Match.OtherWord] where the forgiven slip is really ANOTHER entry's name in the same
     * kind ([CountryNameIndex]): `Ĉilio` for `Ĉinio` is Chile, not a typo of China, and the
     * refusal carries which.
     */
    fun grade(input: String, task: CountryDrillTask, config: CountryDrillRunConfig): Match {
        val match = gradeDrillAnswer(
            input = input,
            accepted = task.accepted,
            display = task.display,
            language = config.answerLanguage,
            cardId = "atlas",
            normalizer = config.normalizer,
        )
        if (match == Match.Exact) return match
        return config.nameIndex?.otherName(task.kind, input) ?: match
    }
}

/**
 * Everything one atlas run is fixed to ([PairDrillRunConfig]): the joined atlas, handed over
 * once by the page that opened the run, and how its questions are drawn, graded and shown.
 */
class CountryDrillRunConfig(
    val content: CountryDrillContent,
    reverse: Boolean,
    fast: Boolean,
    normalizer: AnswerNormalizer?,
    standingRecord: Int = 0,
    cleared: Set<Int> = emptySet(),
) : PairDrillRunConfig<CountryDrillTask>(
    content.source, content.target, reverse, fast, normalizer, standingRecord, cleared,
) {
    /** The atlas turned around for the refusal check — null exactly where [normalizer] is. */
    internal val nameIndex: CountryNameIndex? by lazy {
        normalizer?.let { CountryNameIndex(content, reverse, it) }
    }

    override val drill: Drill get() = Drill.Countries

    override val storageKey: String get() = CountryDrill.storageKey(content.source, content.target)

    override val top: Int get() = CountryDrill.MAX_SPROSSE

    override fun draw(
        sprosse: Int,
        avoid: CountryDrillTask?,
        solved: Set<String>,
        rng: Random,
        arriving: Boolean,
    ): DrillLadder.Sprosse<CountryDrillTask> =
        CountryDrill.draw(content, sprosse, reverse, avoid?.id, solved, rng, arriving)
            .let { DrillLadder.Sprosse(it.task, it.sprosse) }

    override fun grade(input: String, task: CountryDrillTask): Match = CountryDrillRun.grade(input, task, this)

    override fun solvedKey(task: CountryDrillTask): String = DrillSolved.key(task)

    override fun answeredOut(solved: Set<String>): Set<Int> = CountryDrill.cleared(content, reverse, solved)

    /** A flag is written in no language, so a question it alone asks carries none; the giveaway flag waits for the reveal. */
    override fun question(run: PairDrillRunState<CountryDrillTask>): Question {
        val task = run.task
        return Question(
            key = run.index.toString(),
            ask = QuestionAsk.Country(task.kind),
            prompt = Question.Side(
                task.promptText, promptLanguage.takeIf { task.promptText != null }, Question.Form.Name,
                saying = run.promptSaying,
            ),
            answer = Question.Side(task.display, answerLanguage, Question.Form.Name, saying = Saying(task.display, answerLanguage)),
            emoji = task.promptEmoji,
            emojiCue = emojiCue(givesAnswerAway = task.emojiIsGiveaway),
            emojiIsQuestion = task.kind == CountryTaskKind.FlagCountry,
            opens = run.showsAnswer,
            closing = Question.Closing(note = task.gloss?.let { ClosingNote.Own(it) }),
            otherWord = run.otherWord,
        )
    }

    /** Every name is written, in the language it is owed in. */
    override fun controls(run: PairDrillRunState<CountryDrillTask>): AnswerControls =
        run.answerControls(run.typedSlot(answerLanguage))
}
