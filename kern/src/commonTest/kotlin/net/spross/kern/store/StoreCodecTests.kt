package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.DayTally

/** One language's stored file: what it carries, what it refuses, and what it leaves out. */
class StoreCodecTests {

    private val state = StoreFixture.state()
    private val box = StoredBox.of(state)

    /** Hand-built file, so a refusal test says which one thing is wrong. */
    private fun doc(
        cards: String = """"w1":[100,[[50,3]]]""",
        schemaVersion: Int = StoreCodec.SCHEMA_VERSION,
        extra: String = "",
    ): String = """{"cards":{$cards},"schemaVersion":$schemaVersion$extra}"""

    @Test
    fun aBoxRoundTripsBackToTheStateItHeld() {
        val back = StoreCodec.decode(StoreCodec.encode(box))
        assertEquals(state, back.join(StoreFixture.cards, StoreFixture.stamp))
    }

    @Test
    fun theFileCarriesItsOwnSchemaVersion() {
        assertTrue(""""schemaVersion":2""" in StoreCodec.encode(box))
    }

    @Test
    fun aCardIsItsDueAndItsLogAndNothingElse() {
        val sched = state.scheduling.getValue("fixture-verb")
        val entry = sched.log.single()
        val json = StoreCodec.encode(box)
        assertTrue(
            """"fixture-verb":[${sched.due!!.epochSeconds},[[${entry.date.epochSeconds},${entry.rating.value}]]]"""
                in json,
            json,
        )
    }

    @Test
    fun encodingDoesNotDependOnInsertionOrder() {
        val reversed = box.copy(scheduling = box.scheduling.entries.reversed().associate { it.toPair() })
        assertEquals(StoreCodec.encode(box), StoreCodec.encode(reversed))
    }

    @Test
    fun aSuspensionWithNoAnswersIsAnIdAndNothingMore() {
        val suspended = BoxEngine.setSuspended(state, "fixture-phrase", true, Box.day1)
        val json = StoreCodec.encode(StoredBox.of(suspended))
        assertTrue(""""suspended":["fixture-phrase"]""" in json, json)
        assertFalse(""""fixture-phrase":[""" in json, json) // no card entry of its own

        val sched = StoreCodec.decode(json).scheduling.getValue("fixture-phrase")
        assertTrue(sched.suspended)
        assertTrue(sched.log.isEmpty())
    }

    @Test
    fun anAnsweredCardThatIsSuspendedStandsInBothPlaces() {
        val suspended = BoxEngine.setSuspended(state, "fixture-verb", true, Box.day1)
        val json = StoreCodec.encode(StoredBox.of(suspended))
        assertTrue(""""suspended":["fixture-verb"]""" in json, json)

        val sched = StoreCodec.decode(json).scheduling.getValue("fixture-verb")
        assertTrue(sched.suspended)
        assertEquals(1, sched.log.size)
    }

    @Test
    fun todaysCrossingsSurviveAndAnAbsentOneIsNone() {
        val crossed = StoredBox.of(state.copy(consolidatedToday = DayTally("2026-07-01", 2)))
        assertEquals(
            DayTally("2026-07-01", 2),
            StoreCodec.decode(StoreCodec.encode(crossed)).consolidatedToday,
        )
        assertEquals(null, StoreCodec.decode(StoreCodec.encode(box)).consolidatedToday)
    }

    @Test
    fun aDocumentFromAnotherSchemaIsRefused() {
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(schemaVersion = 3)) }
        assertFailsWith<StoreFormatException> { StoreCodec.decode("not json") }
    }

    @Test
    fun aCardTheBuildCannotReadRefusesTheWholeFile() {
        // a log with no answers in it
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(cards = """"w1":[100,[]]""")) }
        // a rating outside the four
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(cards = """"w1":[100,[[50,9]]]""")) }
        // an entry that is not [due, log]
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(cards = """"w1":[100]""")) }
        // a log entry that is not [date, rating]
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(cards = """"w1":[100,[[50]]]""")) }
        // an id no card can carry
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(cards = """"a|b":[100,[[50,3]]]""")) }
    }

    @Test
    fun anOwnWordTheBuildCannotReadRefusesTheWholeFile() {
        val word = ""","ownWords":[{"id":"regen","kind":"noun","texts":{"de":"Regen"}}]"""
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(extra = word)) }

        val kind = ""","ownWords":[{"id":"own:regen","kind":"weather","texts":{"de":"Regen"}}]"""
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(extra = kind)) }

        val empty = ""","ownWords":[{"id":"own:regen","kind":"noun","texts":{}}]"""
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(extra = empty)) }
    }
}
