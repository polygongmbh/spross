package net.spross.app

import net.spross.kern.box.AnswerStatus
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Rating
import net.spross.kern.model.SessionPlan
import net.spross.kern.session.SessionComposer

enum class AnswerTone { Right, Tough, Wrong }

/**
 * On-demand extra round: endless composition FIRST (due + new vocab within the
 * pool budget and health gate); when that is empty, fall back to kern's
 * review-ahead extra round — mirrors iOS (design.md "Counts & sessions").
 */
fun extraSessionPlan(state: BoxState, nowEpochMillis: Long): SessionPlan {
    val endless = SessionComposer.composeEndless(state, nowEpochMillis)
    if (!endless.isEmpty) return endless
    return SessionComposer.composeExtraSession(state, nowEpochMillis)
}

/**
 * Pure session drain loop over the kern facades (no Android imports):
 * queue = reviews + unlockedPhrases + newCards; on empty pull dueNow;
 * on empty + endless refill via composeEndless; else finished (kern README §6).
 */
class SessionFlow(initial: BoxState, plan: SessionPlan) {

    var box: BoxState = initial
        private set
    private val queue = ArrayDeque(plan.reviews + plan.unlockedPhrases + plan.newCards)
    private var endless = false
    private var finished = queue.isEmpty()

    val segments = mutableListOf<AnswerTone>()
    var answered = 0
        private set
    var introduced = 0
        private set
    var strengthened = 0
        private set

    val isFinished: Boolean get() = finished
    val remaining: Int get() = queue.size
    val currentCardId: String? get() = if (finished) null else queue.firstOrNull()

    fun currentCard(): Card? = currentCardId?.let { box.cards[it] }

    fun reviewCount(cardId: String): Int = box.scheduling[cardId]?.reviewCount ?: 0

    fun phase(cardId: String): CardPhase = box.scheduling[cardId]?.phase ?: CardPhase.New

    /** Has this word settled? Unscheduled cards certainly have not. */
    fun isSettled(cardId: String): Boolean = BoxEngine.isSettled(box, cardId)

    fun answer(rating: Rating, nowEpochMillis: Long, tzId: String): AnswerStatus? {
        val id = currentCardId ?: return null
        val wasNew = phase(id) == CardPhase.New
        val outcome = BoxEngine.answer(box, id, rating, nowEpochMillis, tzId)
        box = outcome.state
        if (outcome.status == AnswerStatus.Applied) {
            answered += 1
            if (wasNew) introduced += 1
            if (rating != Rating.Again) strengthened += 1
            segments += when (rating) {
                Rating.Again -> AnswerTone.Wrong
                Rating.Hard -> AnswerTone.Tough
                else -> AnswerTone.Right
            }
        }
        queue.removeFirst()
        refill(nowEpochMillis)
        return outcome.status
    }

    fun continueEndless(nowEpochMillis: Long) {
        endless = true
        finished = false
        refill(nowEpochMillis)
    }

    /** answered / total; the denominator grows when the drain pulls more work. */
    fun progress(): Float {
        val total = answered + queue.size
        return if (total == 0) 1f else answered.toFloat() / total
    }

    fun finish(nowEpochMillis: Long, tzId: String): BoxState {
        box = BoxEngine.endSession(box, answered, nowEpochMillis, tzId)
        return box
    }

    private fun refill(nowEpochMillis: Long) {
        if (queue.isNotEmpty()) return
        queue.addAll(BoxEngine.dueNow(box, nowEpochMillis))
        if (queue.isEmpty() && endless) {
            val extra = SessionComposer.composeEndless(box, nowEpochMillis)
            queue.addAll(extra.reviews + extra.unlockedPhrases + extra.newCards)
        }
        if (queue.isEmpty()) finished = true
    }
}
