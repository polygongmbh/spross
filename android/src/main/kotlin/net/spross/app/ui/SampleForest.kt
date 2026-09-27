package net.spross.app.ui

import net.spross.kern.box.AreaTree
import net.spross.kern.box.TreeTransition

/**
 * A box at any age, without months of reviews behind it — what a debug launch with
 * `--ef forestAge <age>` stands on Home and on a round's summary, like iOS's
 * `-uitest-orchard`. Catalog areas, so the forest labels them with the catalog's emoji.
 */
internal object SampleForest {
    private val areas = listOf(
        "greetings" to 27, "people" to 62, "connectors" to 15, "questions" to 10, "kitchen" to 41,
        "living" to 36, "bath" to 39, "bedroom" to 37, "desk" to 39, "hall" to 40, "nature" to 41,
        "school" to 33, "organization" to 21, "admin" to 38, "doctor" to 36, "work" to 38, "food" to 4,
    )

    /** Every area at [age], 0…1 — areas fill in catalog order, the way a box grows. */
    fun trees(age: Double): List<AreaTree> = areas.mapIndexed { index, area ->
        tree(area, (age * areas.size - index).coerceIn(0.0, 1.0), index)
    }

    /** The kitchen as a round at [age] leaves it. */
    fun round(age: Double): TreeTransition {
        val kitchen = areas[4]
        return TreeTransition(
            tree(kitchen, (age - 0.08).coerceAtLeast(0.0), 4),
            tree(kitchen, age, 4, tended = true),
        )
    }

    private fun tree(area: Pair<String, Int>, reached: Double, index: Int, tended: Boolean? = null): AreaTree {
        val (id, total) = area
        val started = (total * minOf(1.0, reached * 1.3)).toInt()
        val settled = (started * maxOf(0.0, reached - 0.25)).toInt()
        val blossoms = (settled * maxOf(0.0, reached - 0.55)).toInt()
        val fruit = (blossoms * maxOf(0.0, reached - 0.8)).toInt()
        return AreaTree(
            area = id,
            arriving = started - settled,
            growing = settled - blossoms,
            matured = blossoms - fruit,
            longHeld = fruit,
            queued = 0,
            lapsed = if (reached > 0.3 && index % 3 == 0) 2 else 0,
            mass = settled * 0.35 + blossoms * 0.6 + fruit,
            answeredToday = tended ?: (index % 5 == 2 && reached > 0),
            reaches = List(started) { rank -> maxOf(0.0, reached - rank.toDouble() / maxOf(started, 1) * 0.6) },
        )
    }
}
