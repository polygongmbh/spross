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
        /** The playlist itself — the whole sayable join in the order a run will walk it,
         *  dealt by `listeningOrder`. */
        val candidates: List<ListeningCandidate>,
    ) {
        /**
         * Whether listening exists at all — what the entry card is shown on.
         *
         * Non-empty is the whole bar. Whatever the learner's join holds is the pool; a short
         * one simply laps, which is what a playlist does anyway.
         */
        val available: Boolean get() = candidates.isNotEmpty()
    }

    /**
     * The full pool. [hasTargetVoice] / [hasSourceVoice] are whether this device can say
     * ANYTHING in each language, answered by the platform's synthesizer at call time.
     *
     * **The pool is the whole sayable join, not a composed subset** — every joined card that
     * both halves of a turn can say, scheduled and unseen alike, suspended included. A
     * suspended word — whether hand-suspended or a shaky leech (README §5) — is exactly the
     * kind `Inventory.active` drops, and those are the words an hour of listening is for.
     * Suspension pushes a word out of the box's own queue; it was never a statement that the
     * learner should stop meeting the word.
     *
     * Unseen words are in it too, so a learner a few words in hears a STREAM of new words
     * rather than lapping the handful they hold — the mode's cheapest breadth. They enter
     * through `Growth.isIntroducible`: a phrase whose components have not landed is not ready
     * to be heard either. Hearing one does NOT introduce it — introduction is the first
     * answer, and listening answers nothing. With the whole catalog in the pool, the deal does
     * the steering: what is not sticking leads, and everything else is mixed in.
     *
     * What comes back is the PLAY ORDER, not merely a stable one — `listeningOrder` deals the
     * lanes into the sequence a run walks, so an empty box opens on the catalog's first word.
     */
    fun report(
        catalog: Catalog,
        box: BoxState,
        source: Language,
        target: Language,
        hasTargetVoice: Boolean,
        hasSourceVoice: Boolean,
    ): Report {
        val joined = Inventory.joinedCards(box)
        val sayable = joined.filter { sayable(it, catalog, source, target, hasTargetVoice, hasSourceVoice) }
        val scheduled = sayable.mapNotNull { card ->
            val scheduling = box.scheduling[card.id] ?: return@mapNotNull null
            ListeningCandidate(
                card = card,
                stability = scheduling.memory?.stability ?: 0.0,
                suspended = scheduling.suspended,
                scheduled = true,
                // Introduction dequeues (`Answer.kt`), so a scheduled card is never packed.
                queued = false,
            )
        }
        val packed = Growth.enqueuedEligible(box).toSet()
        val unseen = sayable
            .filter { box.scheduling[it.id] == null && Growth.isIntroducible(box, it) }
            .map {
                ListeningCandidate(
                    it, stability = 0.0, suspended = false, scheduled = false, queued = it.id in packed,
                )
            }
        return Report(candidates = listeningOrder(scheduled + unseen))
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
