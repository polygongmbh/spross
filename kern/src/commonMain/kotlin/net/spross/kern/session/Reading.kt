package net.spross.kern.session

import net.spross.kern.model.Language
import net.spross.kern.model.PronunciationCue
import net.spross.kern.model.meaningCue
import net.spross.kern.model.pronunciationCue
import net.spross.kern.model.shownArticle

/**
 * One form said out loud, in the language it is written in. [article] is the one the voice
 * puts in front of a target-side canonical word (`shownArticle`); null everywhere else.
 */
data class Saying(val form: String, val lang: Language, val article: String? = null)

/**
 * What one question on screen says — a review card and a drill task alike: its [prompt] as it
 * goes up, and its [answer] once it has settled. Each side is said at most once per [key],
 * and only from the moment it stops being null; both apps' one reader fires it
 * (`docs/read-aloud.md`), so no screen decides on its own what is heard.
 */
data class Reading(val key: String, val prompt: Saying?, val answer: Saying?)

/**
 * The card has stopped asking: its answer is out, or it came back clean.
 * The moment the side the prompt did not say is heard.
 */
val TurnState.settled: Boolean
    get() = answerOut || feedback == TurnFeedback.Correct

/** What this card says, keyed on the card. [saysMeaning] lets the learner's own side be heard. */
fun TurnState.reading(saysMeaning: Boolean): Reading =
    Reading(card.id, promptSaying(saysMeaning), answerSaying(saysMeaning))

/**
 * What the card says as it goes up — the side its cue lets be heard from frame one.
 * The prompted form on the target side, so a rotated synonym is heard as itself;
 * the meaning only where [saysMeaning], the learner's own side being opt-out.
 */
fun TurnState.promptSaying(saysMeaning: Boolean): Saying? = when {
    pronunciationCue(role, prompt) == PronunciationCue.Upfront -> targetSaying(promptForm)
    saysMeaning && meaningCue(role, prompt) == PronunciationCue.Upfront -> Saying(card.source.text, card.source.lang)
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
fun TurnState.answerSaying(saysMeaning: Boolean): Saying? {
    if (!settled) return null
    val correction = (feedback as? TurnFeedback.Almost)?.correctForm
    return when {
        pronunciationCue(role, prompt) == PronunciationCue.OnReveal -> targetSaying(correction ?: card.target.text)
        saysMeaning -> Saying(correction ?: card.source.text, card.source.lang)
        else -> null
    }
}

private fun TurnState.targetSaying(form: String): Saying =
    Saying(form, card.target.lang, shownArticle(card.target.grammar["gender"], form, card.target.text))
