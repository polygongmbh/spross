package net.spross.app

import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine
import net.spross.app.ui.SampleTrees
import net.spross.kern.box.GrowthHeadline
import net.spross.kern.box.TreeTransition
import net.spross.kern.box.grownArea
import net.spross.kern.box.growthHeadline
import net.spross.kern.catalog.pronunciation
import net.spross.kern.model.Card
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.emojiCue
import net.spross.kern.model.presentationRole
import net.spross.kern.model.producePrompt
import net.spross.kern.model.recognitionPromptForm
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
    val arrived: Boolean = false,
    /** Which face carries the picture; null when the word has none. */
    val emojiCue: EmojiCue?,
    val segments: List<AnswerOutcome>,
    val remaining: Int,
    /** What the round bought ([SessionRunState]'s buckets); the summary spells the non-zero parts. */
    val introduced: Int,
    val settled: Int,
    val reviewed: Int,
    /** Whether an endless refill would yield anything — what "Weiter üben" turns on. */
    val canPracticeMore: Boolean,
    /** The day streak the finish names. */
    val streakDays: Int = 0,
    /**
     * Today's recall is far enough under what the schedule expects that more reps buy
     * little — the box saying so plainly, where a round that only celebrates would be
     * contradicted by the next one.
     */
    val restSuggested: Boolean = false,
    /** The area the round worked hardest, before and after it, and what the summary may claim about it. */
    val grownArea: TreeTransition? = null,
    val headline: GrowthHeadline? = null,
)

private fun AppModel.hasArrived(cardId: String): Boolean =
    box?.let { BoxEngine.hasArrived(it, cardId) } == true

/**
 * Whether the card's own form can be heard RIGHT NOW — the one fact kern's
 * [producePrompt] cannot have. Three ways it cannot, and each keeps the source
 * prompt rather than putting up a card with nothing in it: no recording and no
 * voice, reading aloud switched off, and TalkBack, which suppresses every autoplay
 * so nothing may speak over the screen reader. A volume turned down is not one of
 * them: the screen asks for it to come up instead (`VolumeHint`).
 */
private fun AppModel.audible(card: Card): Boolean {
    if (pronouncer.muted || pronouncer.readsScreenAloud) return false
    val pronunciation = catalog?.pronunciation(card.target.lang, card.target.text) ?: return false
    return pronouncer.canPronounce(pronunciation)
}

/** What the session screen draws for [active]: its current card's turn, or the summary once drained. */
internal fun AppModel.sessionUiFor(active: SessionRunState): SessionUi {
    val state = active.box
    val card = active.currentCardId?.let { state.cards[it] }
    return if (card == null) {
        val restSuggested = BoxEngine.today(state, now(), tz()).recallStrained
        val order = catalog?.let { cat -> stats?.let { BoxBrowser.areaNames(cat, it) } }.orEmpty()
        val moved = sampleTreesAge?.let(SampleTrees::round)
            ?: grownArea(boxBeforeSession ?: state, state, active.tally.cardIds, order, now(), tz())
        val streakDays = stats?.streak ?: 0
        SessionUi(
            card = null, role = null, promptForm = null,
            emojiCue = null,
            segments = active.segments, remaining = 0,
            introduced = active.tally.introduced,
            settled = active.tally.settled,
            reviewed = active.tally.reviewed,
            // why: `DayBooked` precedes this in [dispatch], so [canPracticeExtra] was
            // taken against the box this summary is for — asking again would compose
            // the same round a second time.
            canPracticeMore = canPracticeExtra,
            // why: the day is folded and the numbers refreshed before this runs
            // (`DayBooked` precedes it in [dispatch]), so the finish names the streak
            // the answer just extended rather than the one it started with.
            streakDays = streakDays,
            restSuggested = restSuggested,
            grownArea = moved,
            headline = growthHeadline(
                moved, restSuggested,
                active.tally.introduced, active.tally.settled, active.tally.reviewed, streakDays,
            ),
        )
    } else {
        val count = state.scheduling[card.id]?.reviewCount ?: 0
        val role = presentationRole(card.id, count)
        val promptForm = recognitionPromptForm(card, count)
        val arrived = hasArrived(card.id)
        val prompt = producePrompt(card.id, count, arrived, audible(card))
        SessionUi(
            card = card,
            role = role,
            promptForm = promptForm,
            producePrompt = prompt,
            // The two facts the turn's write-out rule is decided on, read where the
            // count already is: a word being taught is written once as it is met,
            // and one past the growing bar is
            // never slowed down.
            firstExposure = count == 0,
            arrived = arrived,
            emojiCue = card.emoji?.let { emojiCue(role, arrived) },
            segments = active.segments,
            remaining = active.remaining,
            introduced = active.tally.introduced,
            settled = active.tally.settled,
            reviewed = active.tally.reviewed,
            // why: only the finished round shows this, and composing a whole round
            // to fill a field no card on screen reads is a pause between cards.
            canPracticeMore = canPracticeExtra,
        )
    }
}
