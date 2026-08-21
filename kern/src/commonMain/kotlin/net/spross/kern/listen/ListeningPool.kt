package net.spross.kern.listen

import net.spross.kern.box.BoxState
import net.spross.kern.box.Growth
import net.spross.kern.box.Inventory
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.audible
import net.spross.kern.model.Card
import net.spross.kern.model.Language

/**
 * What listening can PLAY on THIS device — the pool a run draws its turns from.
 *
 * A listening turn says the target word, then its meaning in the learner's own language,
 * then the target again, so BOTH halves have to be sayable: a turn that plays a word and
 * then silence teaches nothing. That is the only filter beyond the join.
 *
 * The only platform facts are the two `hasVoice` booleans — one per side, because a profile
 * routinely has a voice for one language and not the other (a `sw` learner on iOS has
 * recordings but no synthesizer). Everything else is kern's own: recording presence is the
 * [Catalog], the schedules are the [BoxState].
 *
 * Nothing here is cached: a voice may be installed in Settings while the app sleeps, so the
 * REBUILD TRIGGER stays the platform's and this answers freshly every time it is asked.
 */
object ListeningPool {

    /**
     * Everything a run draws from, built ONCE per run: it is a catalog sweep, and a
     * per-turn rebuild would re-audit every candidate's audio for a single draw.
     */
    data class Report(
        /** What may be played, scheduled words in seed order first, then any top-up. */
        val candidates: List<ListeningCandidate>,
    ) {
        /**
         * Whether listening exists at all — what the entry card is shown on.
         *
         * Non-empty is the whole bar. The top-up already grows a thin pool as far as the
         * content allows, so whatever is left IS all there is to hear, and a short pool
         * simply laps — which is what a playlist does anyway.
         */
        val available: Boolean get() = candidates.isNotEmpty()
    }

    /**
     * The full pool. [hasTargetVoice] / [hasSourceVoice] are whether this device can say
     * ANYTHING in each language, answered by the platform's synthesizer at call time.
     *
     * The pool is every joined card carrying a schedule, **suspended included**. The leech
     * rule auto-suspends at two lapses (README §5), so the words that stick worst are exactly
     * the ones `Inventory.active` drops — and those are the words an hour of listening is for.
     * Suspension pushes a word out of the box's own queue; it was never a statement that the
     * learner should stop meeting the word.
     *
     * Unseen words top the pool up, in seed order, only while the scheduled pool sits under
     * [LISTENING_POOL_FLOOR] — the "fill a thin round out" move `SessionComposer.fillOut`
     * makes, and what answers the ask for new words once what is in learning runs dry.
     * `Growth.isIntroducible` decides which may come: a phrase whose components have not
     * landed is not ready to be heard either. Hearing one does NOT introduce it — introduction
     * is the first answer, and listening answers nothing.
     */
    fun report(
        catalog: Catalog,
        box: BoxState,
        source: Language,
        target: Language,
        hasTargetVoice: Boolean,
        hasSourceVoice: Boolean,
    ): Report {
        // why: seed order, not the schedule sort — the top-up appends to it, and a pool the
        // draw reorders anyway only needs an order that is STABLE, which seedIndex is.
        val joined = Inventory.joinedCards(box)
        val sayable = joined.filter { sayable(it, catalog, source, target, hasTargetVoice, hasSourceVoice) }
        val scheduled = sayable.mapNotNull { card ->
            val scheduling = box.scheduling[card.id] ?: return@mapNotNull null
            ListeningCandidate(
                card = card,
                difficulty = scheduling.memory?.difficulty ?: 0.0,
                lapses = scheduling.lapses,
                suspended = scheduling.suspended,
                scheduled = true,
            )
        }
        val shortfall = LISTENING_POOL_FLOOR - scheduled.size
        val unseen = if (shortfall <= 0) {
            emptyList()
        } else {
            sayable
                .filter { box.scheduling[it.id] == null && Growth.isIntroducible(box, it) }
                .take(shortfall)
                .map { ListeningCandidate(it, difficulty = 0.0, lapses = 0, suspended = false, scheduled = false) }
        }
        return Report(candidates = scheduled + unseen)
    }

    /** Both halves of the turn heard, or the card is not a candidate. */
    private fun sayable(
        card: Card,
        catalog: Catalog,
        source: Language,
        target: Language,
        hasTargetVoice: Boolean,
        hasSourceVoice: Boolean,
    ): Boolean = audible(card.target.text, target, catalog, hasTargetVoice) &&
        audible(card.source.text, source, catalog, hasSourceVoice)
}
