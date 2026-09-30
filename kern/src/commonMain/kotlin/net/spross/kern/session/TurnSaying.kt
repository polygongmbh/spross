package net.spross.kern.session

import net.spross.kern.model.Language
import net.spross.kern.model.PronunciationCue
import net.spross.kern.model.meaningCue
import net.spross.kern.model.pronunciationCue

/** One form a review card says out loud, in the language it is written in. */
data class TurnSaying(val form: String, val lang: Language)

/**
 * The card has stopped asking: its answer is out, or it came back clean.
 * The moment the side the prompt did not say is heard.
 */
val TurnState.settled: Boolean
    get() = answerOut || feedback == TurnFeedback.Correct

/**
 * What the card says as it goes up — the side its cue lets be heard from frame one.
 * The prompted form on the target side, so a rotated synonym is heard as itself;
 * the meaning only where [saysMeaning], the learner's own side being opt-out.
 */
fun TurnState.promptSaying(saysMeaning: Boolean): TurnSaying? = when {
    pronunciationCue(role, prompt) == PronunciationCue.Upfront -> TurnSaying(promptForm, card.target.lang)
    saysMeaning && meaningCue(role, prompt) == PronunciationCue.Upfront -> TurnSaying(card.source.text, card.source.lang)
    else -> null
}

/**
 * What the card says once it has [settled] — the side the prompt held back, so every card
 * pairs the word with its meaning. Null while it is still asking.
 *
 * A hold says the form its correction carries, which is the answer's own side;
 * everything else says that side's bare text — never the article-carrying citation,
 * whose article is the voice's to add (`spokenTargetForm`).
 */
fun TurnState.answerSaying(saysMeaning: Boolean): TurnSaying? {
    if (!settled) return null
    val correction = (feedback as? TurnFeedback.Almost)?.correctForm
    return when {
        pronunciationCue(role, prompt) == PronunciationCue.OnReveal ->
            TurnSaying(correction ?: card.target.text, card.target.lang)
        saysMeaning -> TurnSaying(correction ?: card.source.text, card.source.lang)
        else -> null
    }
}
