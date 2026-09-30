package net.spross.app

import net.spross.app.audio.Pronouncer
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.pronunciation
import net.spross.kern.model.shownArticle
import net.spross.kern.session.TurnSaying

/**
 * The review loop's audio glue, kept beside the model rather than in it: what a card
 * says at each moment is kern's (`TurnState.promptSaying` / `answerSaying`), whether it
 * may be heard is [Pronouncer], and all that is left — which transition fires it — is
 * the session screen's.
 *
 * The iOS twin is `SessionView+Audio.swift`; the firing table both follow is
 * docs/read-aloud.md.
 */

/**
 * How long an answer's saying waits after its transition. The correct/wrong/reveal chime
 * is never ducked or shortened for the word, so the word steps around it instead of
 * talking over its own first syllable.
 */
const val CHIME_CLEARANCE_MS = 300L

/**
 * Says one of the turn's sayings as autoplay. The target side takes the card's article
 * where the form is its canonical word; the learner's own side never does. [onFinish]
 * fires once the saying is over, or at once where nothing sounds.
 */
fun AppModel.say(saying: TurnSaying, onFinish: (() -> Unit)? = null) {
    val article = spokenArticle(saying.form).takeIf { saying.lang == sessionUi?.card?.target?.lang }
    val pronunciation = catalog?.pronunciation(saying.lang, saying.form, article)
    if (pronunciation == null) {
        onFinish?.invoke()
        return
    }
    pronouncer.pronounce(pronunciation, Pronouncer.Trigger.AUTO, article, onFinish = onFinish)
}

/** Says [form] of the card in play on a tap, which is a request and passes both mutes. */
fun AppModel.pronounceTarget(form: String) {
    val pronunciation = pronunciationOf(form) ?: return
    pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP, spokenArticle(form))
}

/**
 * Tap-to-replay for [form], null where the device can neither play nor speak it —
 * a word that cannot be heard grows no gesture that does nothing. The hit area on
 * the card stands either way.
 */
fun AppModel.pronounceAction(form: String): (() -> Unit)? {
    val pronunciation = pronunciationOf(form) ?: return null
    if (!pronouncer.canPronounce(pronunciation)) return null
    // why: a tap speaks even while reading aloud is switched off — mute has to stay
    // usable as the accessibility affordance, and the About row's hint says so.
    return { pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP, spokenArticle(form)) }
}

/**
 * The article the VOICE says in front of [form] — "das Brot", never a bare stem, because an
 * article is half of what knowing a noun means and a word only ever heard bare is a word
 * never heard right (`docs/read-aloud.md`).
 *
 * Kern decides whether there is one to say: a rotated synonym may carry another gender, so
 * [shownArticle] keeps it off anything but the canonical form. Only the synthesized branch
 * ever uses it — a bundled recording says the bare word it was recorded as.
 */
private fun AppModel.spokenArticle(form: String): String? {
    val target = sessionUi?.card?.target ?: return null
    return shownArticle(CardDisplay.article(target), form, target.text)
}

private fun AppModel.pronunciationOf(form: String): Pronunciation? {
    val lang = sessionUi?.card?.target?.lang ?: return null
    // why: the card's own article, so a word the pack recorded WITH one is heard with it —
    // the same ruling that is handed to the voice a line later, asked once here.
    return catalog?.pronunciation(lang, form, spokenArticle(form))
}
