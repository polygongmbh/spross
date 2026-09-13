package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.DayTally

/** The v2 store document: what it carries, what it refuses, and what it leaves out. */
class StoreDocumentTests {

    private val state = StoreFixture.state()
    private val boxes = StoredBoxes.EMPTY.with(state)

    private fun encoded(boxes: StoredBoxes): String =
        StoreJson.encodeSorted(StoreDocument.serializer(), storeDocument(boxes))

    private fun decoded(json: String): StoredBoxes =
        StoreJson.json.decodeFromString(StoreDocument.serializer(), json).toStored()

    /** Hand-built document, so a refusal test says which one thing is wrong. */
    private fun doc(
        cards: String = """"w1":[100,[[50,3]]]""",
        schemaVersion: Int = STORE_SCHEMA_VERSION,
        target: String = "uk",
        extra: String = "",
    ): String = """{"boxes":{"$target":{"cards":{$cards}$extra}},"schemaVersion":$schemaVersion}"""

    @Test
    fun aStoreRoundTripsBackToTheBoxItHeld() {
        val back = decoded(encoded(boxes))
        assertEquals(setOf("uk"), back.boxes.keys)
        assertEquals(state, back.boxes.getValue("uk").join(StoreFixture.cards, StoreFixture.stamp))
    }

    @Test
    fun aCardIsItsDueAndItsLogAndNothingElse() {
        val sched = state.scheduling.getValue("fixture-verb")
        val entry = sched.log.single()
        assertTrue(
            """"fixture-verb":[${sched.due!!.epochSeconds},[[${entry.date.epochSeconds},${entry.rating.value}]]]"""
                in encoded(boxes),
            encoded(boxes),
        )
    }

    @Test
    fun encodingDoesNotDependOnInsertionOrder() {
        val reversed = StoredBox.of(state).let { box ->
            StoredBoxes(mapOf("uk" to box.copy(scheduling = box.scheduling.entries.reversed().associate { it.toPair() })))
        }
        assertEquals(encoded(boxes), encoded(reversed))
    }

    @Test
    fun aSuspensionWithNoAnswersIsAnIdAndNothingMore() {
        val suspended = BoxEngine.setSuspended(state, "fixture-phrase", true, Box.day1)
        val json = encoded(StoredBoxes.EMPTY.with(suspended))
        assertTrue(""""suspended":["fixture-phrase"]""" in json, json)
        assertFalse(""""fixture-phrase":[""" in json, json) // no card entry of its own

        val sched = decoded(json).boxes.getValue("uk").scheduling.getValue("fixture-phrase")
        assertTrue(sched.suspended)
        assertTrue(sched.log.isEmpty())
    }

    @Test
    fun anAnsweredCardThatIsSuspendedStandsInBothPlaces() {
        val suspended = BoxEngine.setSuspended(state, "fixture-verb", true, Box.day1)
        val json = encoded(StoredBoxes.EMPTY.with(suspended))
        assertTrue(""""suspended":["fixture-verb"]""" in json, json)

        val sched = decoded(json).boxes.getValue("uk").scheduling.getValue("fixture-verb")
        assertTrue(sched.suspended)
        assertEquals(1, sched.log.size)
    }

    @Test
    fun todaysCrossingsSurviveAndAnAbsentOneIsNone() {
        val crossed = state.copy(consolidatedToday = DayTally("2026-07-01", 2))
        val back = decoded(encoded(StoredBoxes.EMPTY.with(crossed)))
        assertEquals(DayTally("2026-07-01", 2), back.boxes.getValue("uk").consolidatedToday)

        assertEquals(null, decoded(encoded(boxes)).boxes.getValue("uk").consolidatedToday)
    }

    @Test
    fun aDocumentFromAnotherSchemaIsRefused() {
        assertFailsWith<StoreFormatException> { decoded(doc(schemaVersion = 1)) }
        assertFailsWith<StoreFormatException> { decoded(doc(schemaVersion = 3)) }
    }

    @Test
    fun aTargetThatIsNoLanguageCodeIsRefused() {
        assertFailsWith<StoreFormatException> { decoded(doc(target = "UK")) }
        assertFailsWith<StoreFormatException> { decoded(doc(target = "ukrainian")) }
    }

    @Test
    fun aCardTheBuildCannotReadRefusesTheWholeFile() {
        // a log with no answers in it
        assertFailsWith<StoreFormatException> { decoded(doc(cards = """"w1":[100,[]]""")) }
        // a rating outside the four
        assertFailsWith<StoreFormatException> { decoded(doc(cards = """"w1":[100,[[50,9]]]""")) }
        // an entry that is not [due, log]
        assertFailsWith<StoreFormatException> { decoded(doc(cards = """"w1":[100]""")) }
        // a log entry that is not [date, rating]
        assertFailsWith<StoreFormatException> { decoded(doc(cards = """"w1":[100,[[50]]]""")) }
        // an id no card can carry
        assertFailsWith<StoreFormatException> { decoded(doc(cards = """"a|b":[100,[[50,3]]]""")) }
    }

    @Test
    fun anOwnWordTheBuildCannotReadRefusesTheWholeFile() {
        val word = ""","ownWords":[{"id":"regen","kind":"noun","texts":{"de":"Regen"}}]"""
        assertFailsWith<StoreFormatException> { decoded(doc(extra = word)) }

        val kind = ""","ownWords":[{"id":"own:regen","kind":"weather","texts":{"de":"Regen"}}]"""
        assertFailsWith<StoreFormatException> { decoded(doc(extra = kind)) }

        val empty = ""","ownWords":[{"id":"own:regen","kind":"noun","texts":{}}]"""
        assertFailsWith<StoreFormatException> { decoded(doc(extra = empty)) }
    }
}
