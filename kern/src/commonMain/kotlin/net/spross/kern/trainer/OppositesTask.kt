package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * One opposites question. Pure data: the app shows [prompt], takes a typed answer, and
 * reveals every one of [answers] (plus [gloss]) once the answer is in.
 */
data class OppositesTask(
    /** The prompt's card id — what the drill has answered, never a schedule it writes. */
    val cardId: String,
    /** The language both the prompt and the answer are written in: the one being learned. */
    val language: Language,
    /** The band the prompt stands in ([OppositesAvailability.Prompt.sprosse]). */
    val sprosse: Int,
    /** The word as the box writes it. */
    val prompt: String,
    /** Shown on the reveal only — what the prompt means, every merged sense of it. */
    val gloss: String,
    /** Every right answer, each with its own gloss; more than one where the prompt merges senses. */
    val answers: List<OppositesAnswer>,
    /** What grades correct: every form of every answer ([OppositesRun.grade]). */
    val accepted: List<String>,
    /** The prompt's own forms — typing the word back is a miss however close it lands. */
    val promptForms: List<String>,
)

/** One right answer as the reveal shows it. */
data class OppositesAnswer(val text: String, val gloss: String)
