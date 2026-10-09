package net.spross.kern.session

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.TodayReport
import net.spross.kern.box.TomorrowNote
import net.spross.kern.box.endOfTomorrow
import net.spross.kern.box.tomorrowNote
import net.spross.kern.trainer.DayLead
import net.spross.kern.trainer.DrillSuggestion

/**
 * Every answer Home puts on the page, taken at one instant.
 *
 * Each of these is a walk of the box and two of them compose a whole round,
 * so a screen that asked for them one at a time composed the day's session again on every redraw.
 * The headline is not among them: it turns on the clock ([SessionOffer.headline]),
 * so a surface reads it off [offer] when it draws.
 */
data class HomeStanding(
    /** Today's round as kern classified it; whether there is one at all is [SessionOffer.hasRound]. */
    val offer: SessionOffer,
    /** What the learner did today. */
    val today: TodayReport,
    /** What a done day says about the next one. */
    val tomorrow: TomorrowNote,
    /** What falls due inside tomorrow — kern's horizon, never a local-midnight rederivation. */
    val tomorrowDue: Int,
    /** Whether a round the learner ASKS for would yield anything ([SessionOffers.canPracticeMore]). */
    val canPracticeMore: Boolean,
) {
    /** What leads Home under the drill the platform's stores name ([DrillSuggestion.suggest]). */
    fun lead(pick: DrillSuggestion.Pick?): DayLead = DayLead.of(offer, pick)

    /** The words-instead button under a drill lead; null where the box can compose no round at all. */
    val roundInstead: RoundStart? get() = when {
        offer.hasRound -> RoundStart.Due
        canPracticeMore -> RoundStart.Extra
        else -> null
    }

    companion object {
        /**
         * [otherLanguagesAnswerDays] reaches the offer for its streak warning alone: the run
         * is one commitment across every box, so the line and the flame beside it agree.
         */
        fun of(
            state: BoxState,
            nowEpochMillis: Long,
            tzId: String,
            otherLanguagesAnswerDays: Map<String, Int>,
        ): HomeStanding {
            // why: the SIZE of the pile, so nothing composes its order.
            val due = BoxEngine.dueCount(state, endOfTomorrow(nowEpochMillis, tzId).toEpochMilliseconds())
            return HomeStanding(
                offer = SessionOffers.offer(state, nowEpochMillis, tzId, otherLanguagesAnswerDays),
                today = BoxEngine.today(state, nowEpochMillis, tzId),
                tomorrow = tomorrowNote(SessionOffers.queuedWordsPending(state), due),
                tomorrowDue = due,
                canPracticeMore = SessionOffers.canPracticeMore(state, nowEpochMillis, tzId),
            )
        }
    }
}
