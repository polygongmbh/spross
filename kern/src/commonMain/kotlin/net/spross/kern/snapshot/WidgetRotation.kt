package net.spross.kern.snapshot

/**
 * Which of a widget's exposure rows stand on the tile at a moment.
 *
 * The head walks kern's attention ranking one row per step, and its position is read off the
 * clock itself, epoch-aligned — so a tile that reloads, or a host that rebuilds its timeline,
 * picks the rotation up where it stands instead of starting it over at the first row.
 * How long a step lasts and how many rows a tile shows are the platform's:
 * each host schedules redraws at its own shortest period, and each tile family holds its own count.
 */
object WidgetRotation {

    /**
     * Indices into [rowCount] rows, head first, in ranking order from the head on:
     * at most [count] of them and never one twice, so a short box does not repeat a word in one tile.
     */
    fun window(rowCount: Int, nowEpochMillis: Long, count: Int, stepMillis: Long): List<Int> {
        if (rowCount <= 0 || count <= 0) return emptyList()
        val head = nowEpochMillis.floorDiv(stepMillis).mod(rowCount.toLong()).toInt()
        return (0 until minOf(count, rowCount)).map { (head + it) % rowCount }
    }
}
