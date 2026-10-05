package net.spross.kern.box

import net.spross.kern.store.SaveScope

/**
 * What [BoxEngine.open] hands back: the live [box], and what its opening owes the disk —
 * the snapshots always, since they must show the box now on screen,
 * and the box document only where it was bootstrapped.
 */
data class OpenedBox(val box: BoxState, val save: SaveScope)
