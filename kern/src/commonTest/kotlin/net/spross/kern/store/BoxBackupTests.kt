package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.StageCounts
import net.spross.kern.box.StreakHealth
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

    /**
     * The file names the pair, not half of it: the target names the box and this names the
     * known language it was studied under, so the phone it lands on re-opens the pair the
     * progress was made in rather than whichever one it happened to be set to.
     */
    @Test
    fun anExportNamesTheKnownLanguageEachBoxWasStudiedUnder() {
        val back = BoxBackup.decode(BoxBackup.encode(boxes.with(swahili())))
        assertEquals("de", back.boxes.getValue("uk").source)
        assertEquals("de", back.boxes.getValue("sw").source)
    }

    /** A file written before the store recorded it restores exactly as it always did. */
    @Test
    fun aFileNamingNoKnownLanguageRestoresAsItAlwaysDid() {
        val json = BoxBackup.encode(boxes).replace(""","source":"de"""", "")
        assertEquals(null, BoxBackup.decode(json).boxes.getValue("uk").source)
    }

    /** One version for the file, never one per language — that is what an envelope is for. */
    @Test
    fun oneSchemaVersionCoversEveryLanguage() {
        val json = BoxBackup.encode(boxes.with(swahili()))

        assertEquals(1, Regex("\"schemaVersion\"").findAll(json).count(), json)
        assertTrue(json.startsWith("""{"boxes":{"""), json)
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

    /** The file is named for what it carries and the learner's own day, not the UTC one. */
    @Test
    fun anExportIsNamedForWhatItCarriesAndTheLocalDay() {
        val laEvening = 1_791_774_000_000L // 2026-10-12T03:00Z, still the 11th in Los Angeles
        assertEquals("Spross-sw-2026-10-11", BoxBackup.fileName("sw", laEvening, "America/Los_Angeles"))
        assertEquals("Spross-2026-10-12", BoxBackup.fileName(null, laEvening, "UTC"))
    }

    /** A box with a settled word offers its export before a reset; one still growing goes straight to the question. */
    @Test
    fun aResetOffersTheExportOnceAWordHasSettled() {
        fun stats(stages: StageCounts) = BoxStatistics(stages, 0, 0, StreakHealth.NoRun, emptyList())
        assertTrue(BoxBackup.offeredBeforeReset(stats(StageCounts(growing = 5, settled = 1))))
        assertFalse(BoxBackup.offeredBeforeReset(stats(StageCounts(fresh = 2, growing = 5))))
    }
}
