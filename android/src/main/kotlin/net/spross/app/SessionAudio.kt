package net.spross.app

import net.spross.app.audio.Pronouncer
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.pronunciation
import net.spross.kern.model.shownArticle
import net.spross.kern.session.Saying

/**
 * The review loop's audio glue, kept beside the model rather than in it: what a card or a
 * drill says at each moment is kern's ([net.spross.kern.session.Reading]), when it is
 * said is [net.spross.app.ui.rememberReadAloud]'s, and whether it may be heard is [Pronouncer].
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
 * Says one saying as autoplay, with the article kern put on it. [onFinish] fires once the
 * saying is over, or at once where nothing sounds.
 */
fun AppModel.say(saying: Saying, onFinish: (() -> Unit)? = null) {
    val pronunciation = catalog?.pronunciation(saying.lang, saying.form, saying.article)
    if (pronunciation == null) {
        onFinish?.invoke()
        return
    }
    pronouncer.pronounce(pronunciation, Pronouncer.Trigger.AUTO, saying.article, onFinish = onFinish)
}

/**
 * The tap on a card's speaker: [saying] with the article kern put on it, heard past both mutes;
 * null where the device can neither play nor say it, which drops the speaker.
 */
fun AppModel.sayOnTap(saying: Saying): (() -> Unit)? {
    val pronunciation = catalog?.pronunciation(saying.lang, saying.form, saying.article) ?: return null
    if (!pronouncer.canPronounce(pronunciation)) return null
    return { pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP, saying.article) }
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
