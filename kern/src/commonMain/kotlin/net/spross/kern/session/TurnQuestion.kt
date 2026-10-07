package net.spross.kern.session

import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.alternates
import net.spross.kern.model.closingNote
import net.spross.kern.model.emojiCue
import net.spross.kern.model.pluralForm
import net.spross.kern.model.shownArticle

/** Between the forms of one side said in the same breath — a meaning and what it also teaches. */
internal const val FORM_JOIN = " / "

/**
 * The review card as it stands: grammar on the target side only, and only on the form the
 * catalog cites — a rotated synonym is a different word and may carry another gender or plural.
 *
 * Its prompt and answer by role:
 * - recognize asks with the rotated target form and opens onto the meaning, with no context cue,
 *   since any cue precise enough to disambiguate would hand over the answer;
 * - produce asks with the source word (the area named where it is ambiguous) and opens onto the target;
 * - produce asked by ear asks with a sound and opens onto the meaning; the word takes the sound's
 *   place once nothing is left to withhold — the learner cannot listen, or the card has opened.
 */
val TurnState.question: Question
    get() = Question(
        key = card.id,
        ask = null,
        prompt = promptSide,
        answer = answerSide,
        emoji = card.emoji,
        emojiCue = emojiCue(role, arrived),
        opens = answerRevealed,
        closing = Question.Closing(
            // The prompt still stands above the reveal, so whatever form it put on screen is no alternative.
            alternates = alternates(card.target, listOf(if (role == PresentationRole.Recognize) promptForm else card.target.text)),
            note = closingNote(card.target, alsoMeans),
        ),
        otherWord = otherWord,
    )

private val TurnState.askedByEar: Boolean get() = prompt == ProducePrompt.Sound

private val TurnState.promptSide: Question.Side
    get() = when {
        role == PresentationRole.Recognize -> targetSide(promptForm)
        askedByEar && (promptInText || answerRevealed) -> targetSide(card.target.text)
        // The meaning is withheld on purpose, so no cue rides along with the sound either.
        askedByEar -> Question.Side(
            text = null, lang = card.target.lang, form = Question.Form.Sound,
            saying = targetSide(card.target.text).saying,
        )
        else -> Question.Side(
            text = card.source.text, lang = card.source.lang, form = Question.Form.Word,
            femMarker = card.promptFeminineMarker,
            context = card.area.takeIf { card.promptAmbiguous },
        )
    }

private val TurnState.answerSide: Question.Side
    get() = if (role == PresentationRole.Produce && !askedByEar) {
        targetSide(card.target.text)
    } else {
        Question.Side(
            text = (listOf(card.source.text) + card.source.teaches).joinToString(FORM_JOIN),
            lang = card.source.lang, form = Question.Form.Word,
            femMarker = card.promptFeminineMarker,
        )
    }

/** [form] on the target side: its article and plural only where it is the cited form, and the speaker that says it. */
private fun TurnState.targetSide(form: String): Question.Side {
    val article = shownArticle(card.target.grammar["gender"], form, card.target.text)
    return Question.Side(
        text = form, lang = card.target.lang, form = Question.Form.Word,
        article = article,
        plural = pluralForm(card.target).takeIf { form == card.target.text },
        saying = Saying(form, card.target.lang, article),
    )
}
