package net.spross.kern.listen

import net.spross.kern.model.Card
import net.spross.kern.session.Question

/**
 * The listening card as a [Question], drawn by the same card component as a review:
 * the target form stands from the first frame, since a run with nothing to answer has nothing to
 * give away, and the meaning grows with its reading — [opens] is the beat that reveals it.
 * Neither side carries a speaker: the run itself is the sound.
 * [card] is the box's card behind the turn, null where the box no longer holds it.
 */
fun ListeningTurn.question(card: Card?, opens: Boolean): Question = Question(
    key = cardId,
    ask = null,
    prompt = Question.Side(targetForm, card?.target?.lang, Question.Form.Word, article = spokenArticle),
    answer = Question.Side(sourceForm, card?.source?.lang, Question.Form.Word),
    emoji = card?.emoji,
    emojiCue = LISTENING_EMOJI_CUE,
    opens = opens,
)
