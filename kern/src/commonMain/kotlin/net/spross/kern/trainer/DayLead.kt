package net.spross.kern.trainer

import net.spross.kern.box.chromeStretch
import net.spross.kern.session.SessionOffer

/**
 * What Home offers first: the day's round, one of the ways in beside it, or the day's rest.
 *
 * Once the reviews still due are the smaller half of the day ([DrillSuggestion.shown]), the
 * day's card leads with the drill [DrillSuggestion] names, listening or the conversation, and
 * the round stays one step away rather than first in line. They take turns, one per stretch
 * of the day the greeting turns with, among those Home offers at all.
 */
enum class DayLead {
    /** There is a round to sit down to, and it leads. */
    Round,

    /** The drill leads, under what the day has done, with a round (or one more) beneath it. */
    Drill,

    /** Listening leads, the same way. */
    Listening,

    /** The conversation leads, the same way. */
    Conversation,

    /** Nothing is due and nothing else is offered. */
    Done,
    ;

    companion object {
        /** [listening] and [conversation] say whether Home offers either card at all. */
        fun of(
            offer: SessionOffer,
            pick: DrillSuggestion.Pick?,
            listening: Boolean,
            conversation: Boolean,
            nowEpochMillis: Long,
            tzId: String,
        ): DayLead {
            val turns = listOfNotNull(
                Drill.takeIf { pick != null },
                Listening.takeIf { listening },
                Conversation.takeIf { conversation },
            )
            return when {
                turns.isNotEmpty() && DrillSuggestion.shown(offer) ->
                    turns[(chromeStretch(nowEpochMillis, tzId) % turns.size).toInt()]
                !offer.hasRound -> Done
                else -> Round
            }
        }
    }
}
