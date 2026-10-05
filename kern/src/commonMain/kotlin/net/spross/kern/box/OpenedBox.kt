package net.spross.kern.box

/** What [BoxEngine.open] hands back: the live [box], and whether the disk is still owed it. */
data class OpenedBox(val box: BoxState, val needsSave: Boolean)
