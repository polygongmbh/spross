@file:OptIn(ExperimentalJsExport::class)

package net.spross.kern.web

import kotlin.random.Random
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.trainer.Trainer
import net.spross.kern.trainer.TrainerKind
import net.spross.kern.trainer.TrainerTask

/** One sampled task, narrowed to JS-clean types (no Long, no List). */
@JsExport
class WebTask internal constructor(
    val prompt: String,
    val promptDisplay: String,
    val display: String,
    val accepted: Array<String>,
    val digits: Int,
)

/** [verdict] is `exact`, `typo` (with [corrected] set), or `wrong`. */
@JsExport
class WebVerdict internal constructor(
    val verdict: String,
    val corrected: String?,
)

/**
 * Browser adapter for the numbers drill: holds the page-seeded [Random]
 * (kern never self-randomizes) and grades through the same normalizer
 * construction the iOS drill uses. Run policy — level ramping, streaks,
 * hint gating — stays page-side, mirroring the app/kern split.
 */
@JsExport
class NumbersDrill(private val language: String, seed: Int) {
    private val rng = Random(seed)
    private val normalizer = AnswerNormalizer(
        LanguageInfo(code = language, name = "", englishName = "", flag = ""),
        articleLeniency = false,
        maxTyposPerWord = 1,
    )

    fun sample(level: Int): WebTask =
        Trainer.sample(TrainerKind.Numbers, language, level, rng).web()

    fun grade(input: String, task: WebTask): WebVerdict =
        when (val match = normalizer.evaluate(input, gradingCard(task))) {
            Match.Exact -> WebVerdict("exact", null)
            is Match.Typo -> WebVerdict("typo", match.corrected)
            else -> WebVerdict("wrong", null)
        }

    /** Accepted forms wrapped as a synthetic card, like the app's drill grading. */
    private fun gradingCard(task: WebTask): Card {
        val answer = Realization(
            lang = language,
            text = task.accepted.first(),
            synonyms = task.accepted.drop(1),
        )
        return Card(
            id = "drill",
            kind = CardKind.Noun,
            area = "",
            emoji = null,
            seedIndex = 0,
            components = emptyList(),
            feminineOf = null,
            source = answer,
            target = answer,
            promptFeminineMarker = false,
        )
    }
}

/** Stateless lookups the page needs outside a run. */
@JsExport
object WebTrainer {
    fun languages(): Array<String> = Trainer.languages.toTypedArray()

    fun supports(language: String): Boolean = Trainer.supports(language)

    fun maxLevel(): Int = Trainer.maxLevel(TrainerKind.Numbers)

    /** Canonical spelled-out reading, for the generated primer tables. */
    fun spellNumber(value: Int, language: String): String =
        Trainer.number(value.toLong(), language).display

    fun placeValueHint(digits: Int, language: String): String? =
        Trainer.placeValueHint(digits, language)

    fun tensReference(language: String): Array<String>? =
        Trainer.tensReference(language)?.toTypedArray()
}

private fun TrainerTask.web(): WebTask = WebTask(
    prompt = prompt,
    promptDisplay = promptDisplay,
    display = display,
    accepted = accepted.toTypedArray(),
    digits = prompt.length,
)
