package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/** How the unseen words past the basics are dealt: shuffled, leaning toward the earlier ones. */
class ListeningNewWordOrderTests {

    private val deep = 1_000..1_999

    /** A box of unseen words only, every one of them well past the basics. */
    private fun unseen(): List<ListeningCandidate> = deep.map {
        ListeningCandidate(
            card = Box.word(it),
            growing = false,
            suspended = false,
            scheduled = false,
            queued = false,
            packedRank = 0,
        )
    }

    private fun dealt(seed: Long): List<Int> = listeningOrder(unseen(), seed).map { it.card.seedIndex }

    /**
     * RULE: past the basics, earlier words come up sooner on average, over any seed.
     * WHY: the earlier a word sits in the catalog the more everyday it is, so a stream of new
     * words should lean on those — without turning back into a queue in catalog order.
     */
    @Test
    fun earlierWordsLeadOnAverage() {
        val midpoint = deep.first + deep.count() / 2
        for (seed in 1L..20L) {
            val opening = dealt(seed).take(100)
            val early = opening.count { it < midpoint }
            assertTrue(early > opening.size - early, "seed $seed opened on $early early words of ${opening.size}")
        }
    }

    /**
     * RULE: the lean is soft — the deepest words still turn up early now and then, and the
     * order is still a shuffle rather than catalog order.
     * WHY: listening is for breadth; a word far down the catalog that could never be heard
     * until everything before it had been would make the mode a slow walk through the list.
     */
    @Test
    fun deepWordsStillComeUpEarly() {
        val lastQuarter = deep.last - deep.count() / 4
        val openings = (1L..20L).map { dealt(it).take(100) }

        assertTrue(openings.any { opening -> opening.any { it > lastQuarter } }, "no deep word ever opened a run")
        assertTrue(openings.all { it != it.sorted() }, "a run dealt its words in catalog order")
    }
}
