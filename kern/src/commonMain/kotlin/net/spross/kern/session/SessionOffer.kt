package net.spross.kern.session

import kotlin.math.max
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.DayPart
import net.spross.kern.box.Growth
import net.spross.kern.box.chromePart
import net.spross.kern.box.dayKey
import net.spross.kern.box.streakHealth
import net.spross.kern.model.fnv1a64

/**
 * What carries the round — whichever side of it outweighs the other.
 * Due work, a light warm-up, and an offer of new words read very differently to a learner,
 * so the day names which one it is instead of calling all three "a session".
 */
enum class SessionOfferKind { Reviews, WarmUp, FreshSet, Nothing }

/**
 * Which line the day's card leads with. Three of these name what the round HOLDS — one per
 * [SessionOfferKind] that carries words — and the fourth names what the day still OWES: a run
 * today has not renewed outranks the round's own words, because the round is still there
 * tomorrow and the run is not.
 */
enum class HeadlineKind { Reviews, WarmUp, FreshSet, StreakReminder }

/**
 * Which headline names this round: the kind that owns the words, plus which of its
 * [SessionOffer.HEADLINE_VARIANTS] phrasings this round takes. The words themselves are the
 * platform's string table — kern names the rule, never the rendering.
 */
data class SessionHeadline(
    val kind: HeadlineKind,
    val variant: Int,
)

/**
 * One composition, everything the day's screen needs from it.
 * Every count is in cards.
 */
data class SessionOffer(
    val kind: SessionOfferKind,
    /** Reviews this round actually takes (capped), not the whole backlog. */
    val reviews: Int,
    /** Due cards the session cap holds back for a later round. */
    val dueHeldBack: Int,
    /** Cards pulled forward to fill a short round out (the session floor). */
    val ahead: Int,
    /** Entries the learner has never answered. */
    val fresh: Int,
    /**
     * Cards a short round would hand over instead, or 0 where this round has nothing to
     * shorten — [SessionComposer.shortRoundSize] draws the line.
     */
    val shortRound: Int,
    /**
     * Reviews this box has already recorded today — what makes the second round of a day
     * read differently from the first.
     */
    val doneToday: Int = 0,
    /**
     * Whether THIS language's run still owes today's work
     * ([net.spross.kern.box.StreakHealth.isExposed]).
     *
     * Read off one box on purpose, never the merged run: the daily rep is per language, so a
     * learner who has already had their exposure in another language is still owed one here,
     * and a card that went quiet about it would be the only place that never said so.
     */
    val streakExposed: Boolean = false,
) {
    /**
     * Which line names this round right now, and which of its phrasings it takes.
     *
     * A run today has not renewed takes the card over from late morning on: the round's own
     * words say the same thing every day, and on the day the streak is actually exposed that is
     * the one thing they fail to say. Mornings stay quiet — a day that has barely started is
     * owed nothing yet — and a run that is safe or absent never nags at all.
     */
    fun headline(nowEpochMillis: Long, tzId: String): SessionHeadline =
        SessionHeadline(
            kind = headlineKind(nowEpochMillis, tzId),
            variant = variant(HEADLINE_VARIANTS),
        )

    private fun headlineKind(nowEpochMillis: Long, tzId: String): HeadlineKind {
        if (streakExposed && chromePart(nowEpochMillis, tzId) != DayPart.Morning) {
            return HeadlineKind.StreakReminder
        }
        return when (kind) {
            SessionOfferKind.Reviews -> HeadlineKind.Reviews
            SessionOfferKind.WarmUp -> HeadlineKind.WarmUp
            // The done state speaks for an empty round, so `Nothing` has no words of its own;
            // folding it onto FreshSet keeps every path off a missing phrasing.
            SessionOfferKind.FreshSet, SessionOfferKind.Nothing -> HeadlineKind.FreshSet
        }
    }

    /**
     * What the day's card SAYS this round holds — which counts it names, in reading order.
     *
     * Words already met and words never seen read differently, so those stay apart.
     * A pulled-forward card is the same act of recalling as a due one,
     * so it counts INTO the repetitions rather than standing as its own pile;
     * only when it carries the round alone does it get named as what it is
     * ([OfferPartKind.Ahead] — the freshening-up round).
     *
     * Empty where the round names nothing at all — a surface says so in one plain phrase
     * instead of printing zeros, the same contract [net.spross.kern.box.TodayReport.tallyParts]
     * keeps. The words, the plurals and the separator between them are the platform's.
     */
    fun summaryParts(): List<OfferPart> {
        val parts = mutableListOf<OfferPart>()
        if (reviews > 0) {
            parts += OfferPart(OfferPartKind.Reviews, reviews + ahead)
        } else if (ahead > 0) {
            parts += OfferPart(OfferPartKind.Ahead, ahead)
        }
        if (fresh > 0) parts += OfferPart(OfferPartKind.Fresh, fresh)
        return parts
    }

    /**
     * The variant turns on the round's SHAPE and on the day's work so far, never on the clock.
     * The shape alone left the line standing after a finished round — a second round of the same
     * make headlines identically, and a screen that never moved stops being read — while a clock
     * term would re-roll it between renders, which reads as a glitch, since the offer is
     * recomposed on every read. [doneToday] moves only when a round actually lands.
     *
     * FNV-1a over those counts, never a runtime-seeded hash: Swift seeds `hashValue` per process
     * and Kotlin's `hashCode` is no contract either, so the same round would headline differently
     * on the next launch — or differently on the two platforms.
     */
    private fun variant(count: Int): Int {
        var hash = fnv1a64("$reviews:$ahead:$fresh:$doneToday")
        // why: FNV leaves its low bits barely mixed, and the modulo reads exactly those.
        hash = hash xor (hash shr 33)
        return (hash % count.toULong()).toInt()
    }

    companion object {
        /** Fewer due cards than this and recall is a warm-up, never the round's headline. */
        const val REVIEWS_LEAD_FROM: Int = 3

        /** How many phrasings each kind offers. */
        const val HEADLINE_VARIANTS: Int = 3
    }
}

/**
 * Which count a spelled-out offer part names.
 * The kinds are the rule; the words and their plurals are the platform's.
 */
enum class OfferPartKind {
    /** Cards to recall — due work with any pull-ahead already folded in. */
    Reviews,

    /** Pull-aheads carrying the round on their own: nothing is due, this is a freshening-up. */
    Ahead,

    /** Entries the learner has never answered. */
    Fresh,
}

/** One part of the day's offer: which count, and how many. */
data class OfferPart(val kind: OfferPartKind, val count: Int)

/** Round classification and the "is there anything to do" questions, over one live box. */
object SessionOffers {

    /**
     * Classify today's round: first sights outnumbering everything to recall make it a fresh set,
     * recall with enough behind it leads, and anything less is a warm-up.
     */
    fun offer(state: BoxState, nowEpochMillis: Long, tzId: String): SessionOffer {
        val plan = SessionComposer.composeSession(state, nowEpochMillis, tzId)
        val reviews = plan.reviews.size
        val ahead = plan.ahead.size
        val fresh = plan.freshCount
        val kind = when {
            plan.isEmpty -> SessionOfferKind.Nothing
            fresh > reviews + ahead -> SessionOfferKind.FreshSet
            reviews >= SessionOffer.REVIEWS_LEAD_FROM -> SessionOfferKind.Reviews
            else -> SessionOfferKind.WarmUp
        }
        return SessionOffer(
            kind = kind,
            reviews = reviews,
            dueHeldBack = max(0, BoxEngine.dueCount(state, nowEpochMillis) - reviews),
            ahead = ahead,
            fresh = fresh,
            shortRound = SessionComposer.shortRoundSize(plan),
            doneToday = state.dailyStats[dayKey(nowEpochMillis, tzId)]?.reviews ?: 0,
            streakExposed = streakHealth(state.dailyStats, nowEpochMillis, tzId).isExposed,
        )
    }

    /**
     * Whether there is a round to sit down to. Due work counts even when the composed round
     * does not carry it: a cap that holds every due card back still leaves work waiting, and a
     * day that answers "nothing" while cards are due is lying.
     */
    fun sessionAvailable(state: BoxState, nowEpochMillis: Long, tzId: String): Boolean =
        !SessionComposer.composeSession(state, nowEpochMillis, tzId).isEmpty ||
            BoxEngine.dueCount(state, nowEpochMillis) > 0

    /**
     * Whether a round the learner asks for would yield anything — what both the summary's
     * "keep practicing" and the done card's extra round turn on, since both open the same
     * [SessionComposer.composeRound]. It counts pull-aheads, so it holds in every done state
     * with active cards; only a box with nothing left at all answers no.
     */
    fun canPracticeMore(state: BoxState, nowEpochMillis: Long, tzId: String): Boolean =
        !SessionComposer.composeRound(state, nowEpochMillis, tzId).isEmpty

    /**
     * Whether words the learner packed could enter the next round — a locked phrase does not
     * count, since it waits on its components rather than on the learner.
     *
     * A finished day composes nothing, so packing on one leaves the learner looking at a card
     * that says the day is over; this is what lets that card say what the next round holds
     * instead of leaving the pack unaccounted for.
     */
    fun packedWordsPending(state: BoxState): Boolean =
        Growth.enqueuedEligible(state).isNotEmpty()
}
