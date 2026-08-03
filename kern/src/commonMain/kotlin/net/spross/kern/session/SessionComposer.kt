package net.spross.kern.session

import kotlin.math.max
import kotlin.math.min
import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.box.dayKey
import net.spross.kern.box.endOfTomorrow
import net.spross.kern.model.SessionPlan

/**
 * Session composition over [BoxState]. Pure: same state + now → same plan.
 *
 * All entries are card ids; composition is role-agnostic — production vs
 * recognition is resolved at render time from the card's log count
 * ([net.spross.kern.model.presentationRole]). Plans carry the state's join
 * stamp; the app recomposes when it goes stale.
 */
object SessionComposer {

    /**
     * Up to this many session slots are reserved so a full due queue can't starve growth.
     *
     * Also the whole of the backlog policy. At `desiredRetention` 0.8 a sitting sends far more
     * cards away on longer intervals than these few bring in, so a reserve this small cannot
     * compound into a backlog the learner never works off — it does not scale with the queue,
     * and FSRS shrinks each card's review load as it matures. A separate brake used to close
     * growth entirely once the projected backlog passed a cap; it only ever fought this reserve,
     * whose entire job is letting a busy box keep growing (`docs/growth-evidence.md`).
     */
    private const val GROWTH_RESERVE_CARDS = 5

    /**
     * A round shorter than this is not worth sitting down for: two or three cards
     * presented as the day's work read as the app having nothing to offer, so a short
     * round is filled out with reviews pulled forward instead. Also what a day has to
     * hold before it counts as worked (see [workedARound]).
     */
    const val SESSION_FLOOR_CARDS: Int = 7

    /**
     * A round introduces at most this many unseen cards — a round's worth of first
     * sights, the same size as [SESSION_FLOOR_CARDS] measures a round to be.
     * The rest is not withdrawn, only deferred — the next round offers it again.
     * This and [GROWTH_RESERVE_CARDS] are the whole of the intake policy: nothing throttles
     * on how shaky the material is, nor on how far behind the box has fallen
     * (`docs/growth-evidence.md`).
     */
    const val NEW_CARDS_PER_ROUND: Int = 7

    /**
     * How far ahead a card still counts as THIS day's work (see [returningSoon]).
     *
     * A rolling span, not a calendar edge: what makes a word today's is that it comes back
     * while the learner is still here, and midnight knows nothing about that — the same
     * two-minute step would be today's at nine in the morning and tomorrow's at five to
     * twelve. Twelve hours is the width of a waking day, so it holds every learning and
     * relearning step (the only schedules that ever land inside one, since a graduated
     * interval floors at a day) without reaching for a card that is genuinely a day out.
     */
    private const val RETURNING_SOON_MILLIS: Long = 12L * 60 * 60 * 1000

    /**
     * Today's plan: [composeRound], unless the day is over.
     *
     * The day is done once nothing is due, nothing is about to be, and the learner has worked
     * a round's worth: "nothing more right now" is a real answer, and manufacturing another
     * round would turn every visit into a treadmill. Nothing composes past that — not even
     * cards the learner packed themselves, which an explicit ask deserves an explicit round
     * for ([composeExtraSession]) rather than a half-sized one behind a finished screen.
     */
    fun composeSession(state: BoxState, nowEpochMillis: Long, tzId: String): SessionPlan {
        val dayDone = Inventory.due(state, nowEpochMillis).isEmpty() &&
            !returningSoon(state, nowEpochMillis) &&
            workedARound(state, nowEpochMillis, tzId)
        if (dayDone) {
            return SessionPlan(
                reviews = emptyList(),
                ahead = emptyList(),
                unlockedPhrases = emptyList(),
                newCards = emptyList(),
                joinStamp = state.joinStamp,
            )
        }
        return composeRound(state, nowEpochMillis, tzId)
    }

    /**
     * A round, whatever asked for it — [composeSession] when the day opens one, and the app
     * directly for the rounds the learner asks for (the extra round off the done card, each
     * endless refill). One set of rules for all three: they used to have their own, which is
     * why the asked-for ones kept arriving as a wall of first sights or a wall of cards
     * dragged forward from days out.
     *
     * Due cards oldest-first (ties by id), review slots capped at `sessionCap − growthReserve`,
     * then new candidates fill the remaining capacity — enqueued cards lead, unlocked phrases
     * next, then seed-order cards. A short round is filled out to [SESSION_FLOOR_CARDS]
     * (see [fillOut]).
     *
     * A quiet round — nothing due at all — is built rather than found: half of it is held for
     * cards that come due tomorrow, and new words take the rest. See [reservedForTomorrow].
     *
     * Its SIZE is the box's to set, not the caller's: due work carries a round when the learner
     * is behind, cards coming due inside tomorrow carry it when the box is settling, and when
     * little is coming up the reservation falls away and new words take the whole of it.
     */
    fun composeRound(state: BoxState, nowEpochMillis: Long, tzId: String): SessionPlan {
        val cap = state.config.sessionCap
        val due = Inventory.due(state, nowEpochMillis)
        val newBudget = if (due.isEmpty()) {
            min(SESSION_FLOOR_CARDS, cap) - reservedForTomorrow(state, nowEpochMillis, tzId)
        } else {
            NEW_CARDS_PER_ROUND
        }

        // Reserve headroom only for new work that will actually appear — a box with
        // nothing left to introduce hands every slot back to the review queue. Costs a
        // second candidate pass, which keeps the precedence inside [Growth.newCandidates]
        // as the one place that decides WHICH cards enter.
        val available = Growth.newCandidates(state, newBudget, capacity = cap).count
        val growthReserve = min(available, GROWTH_RESERVE_CARDS)
        val reviews = due.take(max(0, cap - growthReserve))

        val candidates = Growth.newCandidates(
            state,
            budget = newBudget,
            capacity = max(0, cap - reviews.size),
        )
        val plan = SessionPlan(
            reviews = reviews.map { it.cardId },
            ahead = emptyList(),
            unlockedPhrases = candidates.unlockedPhrases,
            newCards = candidates.newCards,
            joinStamp = state.joinStamp,
        )
        return fillOut(state, plan, nowEpochMillis)
    }

    /**
     * How much of a quiet round is held back for cards that come due tomorrow, at most half.
     *
     * Pulling tomorrow's card forward costs almost no spacing; pulling one due in three weeks
     * burns real spacing, so the reservation only counts what sits inside that horizon.
     * Nothing due tomorrow also means nothing was recently missed — then the round is new
     * words alone, which is the honest offer on a box that has caught up.
     */
    private fun reservedForTomorrow(state: BoxState, nowEpochMillis: Long, tzId: String): Int {
        val horizon = endOfTomorrow(nowEpochMillis, tzId)
        val soon = Inventory.scheduledAhead(state).count { it.due!! <= horizon }
        return min(soon, min(SESSION_FLOOR_CARDS, state.config.sessionCap) / 2)
    }

    /**
     * Whether a card comes back inside [RETURNING_SOON_MILLIS] — the day is not over while
     * one does.
     *
     * A word sent to a learning step is the day's own unfinished business: it was missed
     * minutes ago and returns in minutes, so a screen that calls the day finished in between
     * is overturned by its own scheduler. Only the QUESTION is asked here; what the round
     * then holds is left to the ordinary path, because a round carrying a returning word is
     * an ordinary round — [fillOut] tops it up with pull-aheads exactly as it does any other
     * short one, and growth resumes with it.
     */
    private fun returningSoon(state: BoxState, nowEpochMillis: Long): Boolean =
        Inventory.scheduledAhead(state).any {
            val due = it.due!!.toEpochMilliseconds()
            due > nowEpochMillis && due - nowEpochMillis <= RETURNING_SOON_MILLIS
        }

    /** A round's worth of answers is what closes a day — one tap never did. */
    private fun workedARound(state: BoxState, nowEpochMillis: Long, tzId: String): Boolean =
        (state.dailyStats[dayKey(nowEpochMillis, tzId)]?.reviews ?: 0) >=
            min(SESSION_FLOOR_CARDS, state.config.sessionCap)

    /**
     * Fill a short round out with reviews pulled forward, soonest due first — honest FSRS
     * reviews (short elapsed → small stability gain), never new words: those are already
     * capped at [NEW_CARDS_PER_ROUND] on purpose, and the floor must not talk over that.
     *
     * Soonest-first means tomorrow's cards come first on their own; reaching past them
     * happens only when there is nothing else left to fill the round with, which is the
     * exhausted-catalog case rather than an everyday one.
     */
    private fun fillOut(state: BoxState, plan: SessionPlan, nowEpochMillis: Long): SessionPlan {
        val target = min(SESSION_FLOOR_CARDS, state.config.sessionCap)
        if (plan.cardCount >= target) return plan
        val taken = plan.queue.toSet()
        val ahead = Inventory.scheduledAhead(state)
            .filter { it.cardId !in taken && it.due!!.toEpochMilliseconds() > nowEpochMillis }
            .take(target - plan.cardCount)
        return plan.copy(ahead = ahead.map { it.cardId })
    }

}
