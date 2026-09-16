package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.app.ChromeDe
import net.spross.app.ChromeEn
import net.spross.kern.trainer.Drill

/**
 * The hub card offers kern's roster and nothing else — every [Drill] reaches a chip of its
 * own, and the chips stand in the roster's order. A seventh drill that the card forgot is
 * what this catches; forgetting its face or its name is a compile error rather than a
 * failure here (`docs/drills.md`).
 */
class HubRosterTest {

    private val everything = hubChips(ChromeEn, offered = { true }, open = {})

    @Test
    fun everyDrillOnTheRosterReachesAChipOfItsOwn() {
        assertEquals(Drill.entries.toList(),everything.map { it.drill })
        assertEquals(everything.size, everything.map { it.emoji }.toSet().size)
        assertEquals(everything.size, everything.map { it.title }.toSet().size)
        for (chrome in listOf(ChromeEn, ChromeDe)) {
            assertTrue(
                hubChips(chrome, offered = { true }, open = {}).none { it.title.isBlank() },
                "a chip with no name",
            )
        }
    }

    @Test
    fun aChipIsUpOnlyWhileItsOwnEntryIsOffered() {
        assertEquals(emptyList(), hubChips(ChromeEn, offered = { false }, open = {}))
        assertEquals(
            listOf(Drill.Dates),
            hubChips(ChromeEn, offered = { it == Drill.Dates }, open = {}).map { it.drill },
        )
    }

    /** Each chip opens ITS drill — the wiring a roster of parallel lists used to cross. */
    @Test
    fun eachChipOpensItsOwnDrill() {
        val opened = mutableListOf<Drill>()
        hubChips(ChromeEn, offered = { true }, open = { opened += it }).forEach { it.open() }
        assertEquals(Drill.entries.toList(),opened)
    }
}
