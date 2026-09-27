package net.spross.kern.trainer

import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind

/**
 * What Home offers first: the day's round, the drill [DrillSuggestion] names, or the day's rest.
 *
 * The drill leads wherever one is named ([DrillSuggestion.shown]): the reviews still due are
 * the smaller half of the day by then, and stay one step away rather than first in line.
 */
enum class DayLead {
    /** There is a round to sit down to, and it leads. */
    Round,

    /** The drill leads, under what the day has done, with a round (or one more) beneath it. */
    Drill,

    /** Nothing is due and no drill is named. */
    Done,
    ;

    companion object {
        fun of(offer: SessionOffer, pick: DrillSuggestion.Pick?): DayLead = when {
            pick != null && DrillSuggestion.shown(offer) -> Drill
            offer.kind == SessionOfferKind.Nothing -> Done
            else -> Round
        }
    }
}
