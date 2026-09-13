package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import net.spross.kern.model.CardPhase

/**
 * The one-way door from the v1 box a device still holds into the one that replaces it —
 * same file name, so a conversion is a rewrite and never a move.
 *
 * Documents are written out by hand here: the build that wrote them is gone, and a real one
 * is pinned in `StoreGoldenTest` instead.
 */
class LegacyStoreTests {

    /** A v1 document, the shape the previous schema wrote. */
    private fun doc(
        scheduling: String = entry(),
        schemaVersion: Int = 1,
        source: String = "de",
        ownWords: String? = null,
    ): String =
        """{"config":{"desiredRetention":0.8,""" +
            """"maximumIntervalDays":365,""" +
            """"sessionCap":30,"stepsSeconds":[60,600]},"enqueued":["w2"],""" +
            (ownWords?.let { """"ownWords":[$it],""" } ?: "") +
            """"scheduling":{$scheduling},"schemaVersion":$schemaVersion,""" +
            """"source":"$source","target":"uk"}"""

    /** One v1 schedule, carrying the fields this build no longer stores. */
    private fun entry(
        key: String = "w1",
        cardId: String = "w1",
        phase: String = "review",
        lapses: Int = 0,
        suspended: Boolean = false,
        log: String = """[{"date":"2026-07-01T12:00:00Z","elapsedDays":0.0,"rating":3}]""",
        due: String = ""","due":"2026-07-02T12:00:00Z"""",
        memory: String = ""","memory":{"difficulty":5.0,"stability":3.0}""",
    ): String =
        """"$key":{"addedAt":"2026-07-01T12:00:00Z","cardId":"$cardId"$due,"lapses":$lapses,""" +
            """"log":$log$memory,"phase":"$phase","suspended":$suspended}"""

    @Test
    fun aV1BoxArrivesAsTheScheduleItsLogImplies() {
        val box = LegacyStore.convert(doc())
        val sched = box.scheduling.getValue("w1")

        assertEquals(CardPhase.Review, sched.phase)
        assertEquals(1, sched.log.size)
        // the stored due is kept — it is the one thing a replay cannot work out
        assertEquals(Instant.parse("2026-07-02T12:00:00Z"), sched.due)
        assertEquals(listOf("w2"), box.enqueued)
    }

    /** Reading routes by the version the file declares, and says when it converted one. */
    @Test
    fun loadingConvertsAV1FileAndSaysSo() {
        val legacy = StoreCodec.load(doc())
        assertTrue(legacy.converted)
        assertEquals(1, legacy.box.scheduling.size)

        val current = StoreCodec.load(StoreCodec.encode(legacy.box))
        assertFalse(current.converted)
        assertEquals(legacy.box.scheduling, current.box.scheduling)

        assertFailsWith<StoreFormatException> { StoreCodec.load(doc(schemaVersion = 3)) }
    }

    @Test
    fun aWordSuspendedBeforeItWasEverAskedArrivesAsAHusk() {
        val husk = entry(key = "w3", cardId = "w3", phase = "new", suspended = true,
                         log = "[]", due = "", memory = "")
        val sched = LegacyStore.convert(doc(scheduling = "${entry()},$husk")).scheduling.getValue("w3")

        assertTrue(sched.suspended)
        assertTrue(sched.log.isEmpty())
    }

    /** A card that was never answered and is not suspended says nothing worth carrying. */
    @Test
    fun anEmptyScheduleIsLeftBehind() {
        val empty = entry(key = "w3", cardId = "w3", phase = "new", log = "[]", due = "", memory = "")
        assertFalse("w3" in LegacyStore.convert(doc(scheduling = "${entry()},$empty")).scheduling)
    }

    /**
     * The leech rule removed on 2026-09-01 auto-suspended anything with two lapses; its mark
     * is lifted on the way over, read off the count v1 stored rather than a replayed one.
     * A learner's own hand-suspend of a twice-lapsed word goes with them — a best-effort
     * sweep, and re-suspending afterwards is a fresh, current choice.
     */
    @Test
    fun aLeechEraSuspensionIsLiftedOnTheWayOver() {
        val leech = entry(key = "w3", cardId = "w3", lapses = 2, suspended = true)
        assertFalse(LegacyStore.convert(doc(scheduling = "${entry()},$leech")).scheduling.getValue("w3").suspended)

        val ownChoice = entry(key = "w4", cardId = "w4", lapses = 1, suspended = true)
        assertTrue(LegacyStore.convert(doc(scheduling = "${entry()},$ownChoice")).scheduling.getValue("w4").suspended)
    }

    /**
     * A box written by an older build carries keys renamed or retired since — `maxLearning`,
     * `settledStability`, the split learning/relearning ladders. Unknown keys are dropped
     * rather than failing a document the learner cannot rewrite.
     */
    @Test
    fun keysThisBuildNoLongerKnowsAreDropped() {
        val legacy =
            """{"config":{"desiredRetention":0.8,"dueSoftCap":30,""" +
                """"learningStepsSeconds":[60,600],"maxLearning":9,"maximumIntervalDays":365,""" +
                """"settledStability":2.0,"sessionCap":30},"dailyStats":{},""" +
                """"enqueued":[],"newIntroduced":{},"scheduling":{${entry()}},""" +
                """"schemaVersion":1,"source":"de","target":"uk"}"""
        assertEquals(1, LegacyStore.convert(legacy).scheduling.size)
    }

    @Test
    fun anOwnWordWrittenBeforeItsAgeWasRecordedArrivesAsOld() {
        val word = """{"id":"own:regen","kind":"noun","texts":{"de":"Regen","uk":"дощ"}}"""
        assertEquals(Instant.DISTANT_PAST, LegacyStore.convert(doc(ownWords = word)).ownWords.single().addedAt)
    }

    @Test
    fun aDocumentThisReaderCannotTrustIsRefused() {
        assertFailsWith<StoreFormatException> { LegacyStore.convert(doc(source = "uk")) } // source == target
        // New phase with a memory behind it: the invariant a v1 box promised
        assertFailsWith<StoreFormatException> { LegacyStore.convert(doc(scheduling = entry(phase = "new"))) }
    }
}
