package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.box.DayTally
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating

/**
 * The export file: the languages worth carrying in ONE document under a single schema
 * version, what it leaves behind, and what it refuses.
 */
class BoxBackupTests {

    private val state = StoreFixture.state()
    private val boxes = StoredBoxes.EMPTY.with(state)

    /** A second language with something in it — one word answered once. */
    private fun swahili(): BoxState {
        val word = Box.word(1)
        val opened = Box.state(listOf(word)).copy(joinStamp = JoinStamp("de", "sw", "fixture"))
        return Box.answered(opened, word.id, Rating.Good, Box.day1)
    }

    @Test
    fun anExportReadsBackAsTheBoxItCarried() {
        val back = BoxBackup.decode(BoxBackup.encode(boxes))
        assertEquals(state, back.boxes.getValue("uk").join(StoreFixture.cards, StoreFixture.stamp))
    }

    /** One version for the file, never one per language — that is what an envelope is for. */
    @Test
    fun oneSchemaVersionCoversEveryLanguage() {
        val json = BoxBackup.encode(boxes.with(swahili()))

        assertEquals(1, Regex("\"schemaVersion\"").findAll(json).count(), json)
        assertTrue(json.startsWith("""{"boxes":{"""), json)
    }

    /** The day's crossings belong to one device; the box an export lands in has its own day. */
    @Test
    fun anExportLeavesTodaysCrossingsBehind() {
        val crossed = StoredBoxes.EMPTY.with(state.copy(consolidatedToday = DayTally("2026-07-01", 2)))
        val json = BoxBackup.encode(crossed)

        assertFalse("today" in json, json)
        assertEquals(null, BoxBackup.decode(json).boxes.getValue("uk").consolidatedToday)
    }

    @Test
    fun aRestoreReplacesWhatItCarriesAndKeepsTheRest() {
        val sw = swahili()
        val held = boxes.with(sw)
        val imported = BoxBackup.decode(BoxBackup.encode(StoredBoxes.EMPTY.with(state)))

        val restored = held.restoring(imported)
        assertEquals(setOf("uk", "sw"), restored.boxes.keys)
        assertEquals(state.scheduling, restored.boxes.getValue("uk").scheduling)
        assertEquals(sw.scheduling, restored.boxes.getValue("sw").scheduling)
    }

    /** A language only ever opened would land as an emptiness over a real box. */
    @Test
    fun anUntouchedBoxIsNotCarried() {
        val held = StoredBoxes(boxes.boxes + ("sw" to StoredBox()))

        assertEquals(listOf("uk"), BoxBackup.carried(held))
        assertEquals(setOf("uk"), BoxBackup.decode(BoxBackup.encode(held)).boxes.keys)
    }

    @Test
    fun anExportOfOneLanguageCarriesOnlyThatOne() {
        val held = boxes.with(swahili())

        assertEquals(setOf("sw"), BoxBackup.decode(BoxBackup.encode(held, only = "sw")).boxes.keys)
    }

    @Test
    fun oneUnreadableBoxRefusesTheWholeFile() {
        // one box reads, the other carries a rating outside the four
        val broken = """{"boxes":{"uk":{"cards":{"w1":[100,[[50,3]]]}},""" +
            """"sw":{"cards":{"w2":[100,[[50,9]]]}}},"schemaVersion":2}"""
        assertFailsWith<StoreFormatException> { BoxBackup.decode(broken) }
        assertFailsWith<StoreFormatException> { BoxBackup.decode("not json") }
    }

    @Test
    fun aTargetThatIsNoLanguageCodeIsRefused() {
        val shouty = """{"boxes":{"UK":{"cards":{}}},"schemaVersion":2}"""
        assertFailsWith<StoreFormatException> { BoxBackup.decode(shouty) }
    }
}
