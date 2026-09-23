package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.pronunciation
import net.spross.kern.model.Card
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.PronunciationCue
import net.spross.kern.model.emojiCue
import net.spross.kern.model.presentationRole
import net.spross.kern.model.producePrompt
import net.spross.kern.model.pronunciationCue
import net.spross.kern.model.recognitionPromptForm
import net.spross.kern.model.shownArticle
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.SessionRun
import net.spross.kern.session.SessionRunState

data class SessionUi(
    val card: Card?,               // null ⇒ drained: show the summary
    val role: PresentationRole?,
    val promptForm: String?,       // rotated recognition prompt
    /** Whether a produce turn asks by meaning or by ear; [ProducePrompt.Source] elsewhere. */
    // layer-ok: the drained branch has no produce turn — every real one carries kern's cue
    val producePrompt: ProducePrompt = ProducePrompt.Source,
    /** `reviewCount == 0` — the word is being taught, so a miss is still written out. */
    val firstExposure: Boolean = false,
    /** A word that already sticks is never slowed down by a write-out. */
    val growing: Boolean = false,
    /** Which face carries the picture; null when the word has none. */
    val emojiCue: EmojiCue?,
    /**
     * What the prompt says out loud, or null where the card owes that very form —
     * non-null ⇔ kern's cue puts the target on screen from frame one.
     */
    val promptPronunciation: Pronunciation?,
    val segments: List<AnswerOutcome>,
    val remaining: Int,
    /** What the round bought ([SessionRunState]'s buckets); the summary spells the non-zero parts. */
    val introduced: Int,
    val strengthened: Int,
    val reviewed: Int,
    /** Whether an endless refill would yield anything — what "Weiter üben" turns on. */
    val canPracticeMore: Boolean,
    /** The day streak the finish names, and whether it stands at its all-time best. */
    val streakDays: Int = 0,
    val streakIsRecord: Boolean = false,
    /**
     * Today's recall is far enough under what the schedule expects that more reps buy
     * little — the box saying so plainly, where a round that only celebrates would be
     * contradicted by the next one.
     */
    val restSuggested: Boolean = false,
)

private fun AppModel.isGrowing(cardId: String): Boolean =
    box?.let { BoxEngine.isGrowing(it, cardId) } == true

/**
 * Whether the card's own form can be heard RIGHT NOW — the one fact kern's
 * [producePrompt] cannot have. Four ways it cannot, and each keeps the source
 * prompt rather than putting up a card with nothing in it: no recording and no
 * voice, reading aloud switched off, a device turned down or muted by its own
 * volume, and TalkBack, which suppresses every autoplay so nothing may speak over
 * the screen reader.
 */
private fun AppModel.audible(card: Card): Boolean {
    if (pronouncer.muted || pronouncer.readsScreenAloud || pronouncer.deviceSilenced) return false
    val pronunciation = catalog?.pronunciation(card.target.lang, card.target.text) ?: return false
    return pronouncer.canPronounce(pronunciation)
}

/** What the session screen draws for [active]: its current card's turn, or the summary once drained. */
internal fun AppModel.sessionUiFor(active: SessionRunState): SessionUi {
    val state = active.box
    val card = active.currentCardId?.let { state.cards[it] }
    return if (card == null) {
        SessionUi(
            card = null, role = null, promptForm = null,
            emojiCue = null, promptPronunciation = null,
            segments = active.segments, remaining = 0,
            introduced = active.newCards, strengthened = active.graduated,
            reviewed = active.reviews,
            // why: `DayBooked` precedes this in [dispatch], so [canPracticeExtra] was
            // taken against the box this summary is for — asking again would compose
            // the same round a second time.
            canPracticeMore = canPracticeExtra,
            // why: the day is folded and the numbers refreshed before this runs
            // (`DayBooked` precedes it in [dispatch]), so the finish names the streak
            // the answer just extended rather than the one it started with.
            streakDays = stats?.streak ?: 0,
            streakIsRecord = stats?.let { SessionRun.streakIsRecord(it) } == true,
            restSuggested = BoxEngine.today(state, now(), tz()).recallStrained,
        )
    } else {
        val count = state.scheduling[card.id]?.reviewCount ?: 0
        val role = presentationRole(card.id, count)
        val promptForm = recognitionPromptForm(card, count)
        val growing = isGrowing(card.id)
        val prompt = producePrompt(card.id, count, growing, audible(card))
        SessionUi(
            card = card,
            role = role,
            promptForm = promptForm,
            producePrompt = prompt,
            // The two facts the turn's write-out rule is decided on, read where the
            // count already is: a word being taught is written once as it is met,
            // and one that already sticks — growing, the one landed bar — is
            // never slowed down.
            firstExposure = count == 0,
            growing = growing,
            emojiCue = card.emoji?.let { emojiCue(role, growing) },
            // why: the KERN cue, never `role == Recognize` — one rule, consumed by
            // both apps. The PROMPTED form, so a rotated synonym is heard as itself.
            promptPronunciation = catalog
                ?.takeIf { pronunciationCue(role, prompt) == PronunciationCue.Upfront }
                // why: a sound-prompted produce has NOTHING on screen, so what plays
                // is the very form it grades against, not the recognition rotation.
                ?.pronunciation(
                    card.target.lang,
                    if (prompt == ProducePrompt.Sound) card.target.text else promptForm,
                    // why: the prompted form's own article — `shownArticle` withholds it
                    // from a rotated synonym, so only the canonical form hears one.
                    shownArticle(
                        CardDisplay.article(card.target),
                        if (prompt == ProducePrompt.Sound) card.target.text else promptForm,
                        card.target.text,
                    ),
                ),
            segments = active.segments,
            remaining = active.remaining,
            introduced = active.newCards,
            strengthened = active.graduated,
            reviewed = active.reviews,
            // why: only the finished round shows this, and composing a whole round
            // to fill a field no card on screen reads is a pause between cards.
            canPracticeMore = canPracticeExtra,
        )
    }
}
