package net.spross.kern.session

import kotlin.math.max
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.fnv1a64

/**
 * What carries the round — whichever side of it outweighs the other.
 * Due work, a light warm-up, and an offer of new words read very differently to a learner,
 * so the day names which one it is instead of calling all three "a session".
 */
enum class SessionOfferKind { Reviews, WarmUp, FreshSet, Nothing }

/**
 * Which headline names this round: the kind that owns the words, plus which of its
 * [SessionOffer.HEADLINE_VARIANTS] phrasings this round takes. The words themselves are the
 * platform's string table — kern names the rule, never the rendering.
 */
data class SessionHeadline(
    val kind: SessionOfferKind,
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
) {
    /**
     * The variant turns on the round's SHAPE, never on the clock: a learner does several rounds
     * in a day, and one repeated line reads as a screen that never moved while a line re-rolling
     * between renders reads as a glitch — and the offer is recomposed on every read.
     */
    val headline: SessionHeadline
        get() = SessionHeadline(
            // The done state speaks for an empty round, so `Nothing` has no words of its own;
            // naming it anyway keeps every path off a missing key.
            kind = if (kind == SessionOfferKind.Nothing) SessionOfferKind.FreshSet else kind,
            variant = variant(HEADLINE_VARIANTS),
        )

    /**
     * FNV-1a over the counts, never a runtime-seeded hash: Swift seeds `hashValue` per process
     * and Kotlin's `hashCode` is no contract either, so the same round would headline differently
     * on the next launch — or differently on the two platforms.
     */
    private fun variant(count: Int): Int {
        var hash = fnv1a64("$reviews:$ahead:$fresh")
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
            dueHeldBack = max(0, BoxEngine.dueNow(state, nowEpochMillis).size - reviews),
            ahead = ahead,
            fresh = fresh,
        )
    }

    /**
     * Whether there is a round to sit down to. Due work counts even when the composed round
     * does not carry it: a cap that holds every due card back still leaves work waiting, and a
     * day that answers "nothing" while cards are due is lying.
     */
    fun sessionAvailable(state: BoxState, nowEpochMillis: Long, tzId: String): Boolean =
        !SessionComposer.composeSession(state, nowEpochMillis, tzId).isEmpty ||
            BoxEngine.dueNow(state, nowEpochMillis).isNotEmpty()

    /**
     * Whether an endless refill would yield anything right now — what the summary's
     * "keep practising" turns on. Nothing is pulled ahead of its due time here.
     */
    fun canPracticeMore(state: BoxState, nowEpochMillis: Long): Boolean =
        !SessionComposer.composeEndless(state, nowEpochMillis).isEmpty

    /**
     * Whether an extra round would yield anything. Unlike [canPracticeMore] this counts
     * pull-aheads too, so it holds in every done state with active cards.
     */
    fun canPracticeExtra(state: BoxState, nowEpochMillis: Long): Boolean =
        !SessionComposer.composeExtraSession(state, nowEpochMillis).isEmpty
}
