package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BriefWord
import net.spross.kern.box.Briefing
import net.spross.kern.box.Briefings
import net.spross.kern.box.Harvest

/**
 * Handing the box to a conversation the app does not host, and reading back what that
 * conversation turned up.
 *
 * The rules — which words a brief may name, how it reads, what a pasted answer means —
 * are kern's ([Briefing], [Harvest]); this layer carries the clock and the clipboard.
 */

/**
 * What the box would tell an assistant about this learner right now, or null before it
 * has loaded. A function rather than a value: it walks every card, so a composable that
 * read it as state would rebuild the whole brief on each recomposition.
 */
fun AppModel.briefing(): Briefing? {
    val state = box ?: return null
    val catalog = catalog ?: return null
    return Briefings.of(state, catalog, learnerName)
}

/** Whether there is a conversation to be briefed at all — what hides the offer. */
val AppModel.hasBriefing: Boolean
    get() = box?.scheduling?.values?.any { !it.suspended } == true

/** The words a pasted conversation brought home that the box does not already hold. */
fun AppModel.harvest(pasted: String): List<BriefWord> =
    box?.let { Harvest.read(pasted, it) }.orEmpty()

/**
 * Take the kept ones in as own words, in one write.
 *
 * One at a time off the state the last one returned, never in a batch against the state
 * this started from: the id is minted against the ids already taken, and two words that
 * fold alike would otherwise mint the same one ([Harvest.ownWord]).
 */
fun AppModel.keepHarvested(words: List<BriefWord>) {
    if (words.isEmpty()) return
    val stamp = now()
    updateBox { state ->
        words.fold(state) { carried, word ->
            BoxEngine.addOwnWord(carried, Harvest.ownWord(carried, word), stamp)
        }
    }
}
