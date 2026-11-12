package net.spross.app

import net.spross.kern.box.BriefWord
import net.spross.kern.box.Briefing
import net.spross.kern.box.Briefings
import net.spross.kern.box.Harvest
import net.spross.kern.box.HarvestWord
import net.spross.kern.box.WordListFile

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

/** Whether there is a box to brief — every box is, the unopened one as a beginner's talk. */
val AppModel.hasBriefing: Boolean
    get() = box != null

/**
 * Every word a pasted conversation brought home, each against what the box already has of
 * it ([net.spross.kern.box.HarvestKind]), grouped new-first.
 */
fun AppModel.harvest(pasted: String): List<HarvestWord> =
    box?.let { Harvest.read(pasted, it) }.orEmpty()

/** Take the kept ones in as own words, queued, in one write ([Harvest.keep]). */
fun AppModel.keepHarvested(words: List<BriefWord>) {
    if (words.isEmpty()) return
    val stamp = now()
    updateBox { state -> Harvest.keep(state, words, stamp) }
}

/** Every pair a word-list file holds, each against the box ([WordListFile]). */
fun AppModel.harvestFile(text: String): List<HarvestWord> =
    box?.let { WordListFile.read(text, it) }.orEmpty()
