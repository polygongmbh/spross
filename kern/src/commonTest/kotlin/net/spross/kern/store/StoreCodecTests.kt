package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.model.CardPhase

class StoreCodecTests {

    private val state = StoreFixture.state()

    // Round trip

    @Test
    fun roundTripPreservesAggregate() {
        val decoded = StoreCodec.decode(StoreCodec.encode(state))
        assertEquals("uk", decoded.target)
        assertEquals("de", decoded.source)
        assertEquals(state.config, decoded.config)
        assertEquals(state.scheduling, decoded.scheduling)
        assertEquals(state.enqueued, decoded.enqueued)
        assertEquals(state.newIntroduced, decoded.newIntroduced)
        assertEquals(state.dailyStats, decoded.dailyStats)
    }

    @Test
    fun joinRebuildsBoxState() {
        val decoded = StoreCodec.decode(StoreCodec.encode(state))
        assertEquals(state, decoded.join(StoreFixture.cards, StoreFixture.stamp))
    }

    @Test
    fun encodeIsByteStableAcrossRoundTrip() {
        val first = StoreCodec.encode(state)
        val second = StoreCodec.encode(
            StoreCodec.decode(first).join(StoreFixture.cards, StoreFixture.stamp),
        )
        assertEquals(first, second)
    }

    @Test
    fun encodeIsInsertionOrderIndependent() {
        val reversed = state.copy(
            scheduling = state.scheduling.entries.reversed().associate { it.key to it.value },
            dailyStats = state.dailyStats.entries.reversed().associate { it.key to it.value },
        )
        assertEquals(StoreCodec.encode(state), StoreCodec.encode(reversed))
    }

    @Test
    fun datesEncodeAsIsoUtcStrings() {
        val encoded = StoreCodec.encode(state)
        assertTrue("\"addedAt\":\"2026-07-01T12:00:00Z\"" in encoded, encoded)
        assertTrue("\"date\":\"2026-07-01T12:10:00Z\"" in encoded, encoded)
    }

    @Test
    fun nullFieldsAreOmitted() {
        // Review-phase cards carry stepIndex = null; New would carry null memory/due.
        assertFalse("null" in StoreCodec.encode(state))
    }

    // Decode validation (hand-built minimal documents)

    private fun doc(
        scheduling: String = "",
        schemaVersion: Int = 1,
        source: String = "de",
        /** null omits the key entirely — the shape a box written before own words has. */
        ownWords: String? = null,
    ): String =
        """{"config":{"desiredRetention":0.8,"learningStepsSeconds":[60,600],""" +
            """"maximumIntervalDays":365,""" +
            """"relearningStepsSeconds":[600],"sessionCap":30},"dailyStats":{},"enqueued":[],""" +
            """"newIntroduced":{},""" +
            (ownWords?.let { """"ownWords":[$it],""" } ?: "") +
            """"scheduling":{$scheduling},"schemaVersion":$schemaVersion,""" +
            """"source":"$source","target":"uk"}"""

    private fun entry(
        key: String = "w1",
        cardId: String = "w1",
        phase: String = "review",
        due: String = ""","due":"2026-07-02T12:00:00Z"""",
        memory: String = ""","memory":{"difficulty":5.0,"stability":3.0}""",
    ): String =
        """"$key":{"addedAt":"2026-07-01T12:00:00Z","cardId":"$cardId"$due,"lapses":0,""" +
            """"log":[{"date":"2026-07-01T12:00:00Z","elapsedDays":0.0,"rating":3}]""" +
            """$memory,"phase":"$phase","suspended":false}"""

    @Test
    fun decodeAcceptsMinimalDocument() {
        val decoded = StoreCodec.decode(doc(entry()))
        val sched = decoded.scheduling.getValue("w1")
        assertEquals("w1", sched.cardId)
        assertEquals(CardPhase.Review, sched.phase)
        assertEquals(3.0, sched.memory?.stability)
        assertEquals(1, sched.reviewCount)
    }

    /**
     * A box written by an older build carries `maxLearning` and `phraseUnlockStability`,
     * both renamed since, plus `maxUnsettled` and `dueSoftCap`, retired with the load
     * throttle and the backlog gate, and `settledStability`, merged into the one landed
     * bar. Keys this build no longer knows are dropped instead of failing the document,
     * and the renamed settings fall back to the build's calibration.
     */
    @Test
    fun decodeDropsKeysRenamedSinceTheDocumentWasWritten() {
        val legacy =
            """{"config":{"desiredRetention":0.8,"dueSoftCap":30,""" +
                """"learningStepsSeconds":[60,600],"maxLearning":9,"maximumIntervalDays":365,""" +
                """"phraseUnlockStability":2.0,"maxUnsettled":9,"relearningStepsSeconds":[600],""" +
                """"sessionCap":30,"settledStability":2.0},""" +
                """"dailyStats":{},"enqueued":[],"newIntroduced":{},""" +
                """"scheduling":{${entry()}},"schemaVersion":1,"source":"de","target":"uk"}"""

        val decoded = StoreCodec.decode(legacy)

        assertEquals(30, decoded.config.sessionCap)
        assertEquals(6.0, decoded.config.consolidatedStability)
        assertEquals("w1", decoded.scheduling.getValue("w1").cardId)
    }

    @Test
    fun decodeRejectsMalformedJson() {
        assertFailsWith<StoreFormatException> { StoreCodec.decode("not json") }
    }

    @Test
    fun decodeRejectsWrongSchemaVersion() {
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(schemaVersion = 2)) }
    }

    @Test
    fun decodeRejectsSameSourceAndTarget() {
        assertFailsWith<StoreFormatException> { StoreCodec.decode(doc(source = "uk")) }
    }

    @Test
    fun decodeRejectsMismatchedSchedulingKey() {
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(key = "w2", cardId = "w1")))
        }
    }

    @Test
    fun decodeRejectsPipeInCardId() {
        // v1-era unit keys ("id|produce") are not valid card ids.
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(key = "w1|produce", cardId = "w1|produce")))
        }
    }

    @Test
    fun decodeRejectsUnknownPhase() {
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(phase = "later")))
        }
    }

    @Test
    fun decodeRejectsInvariantViolation() {
        // Review phase but no due date: phase == New ⟺ memory == null ⟺ due == null.
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(due = "")))
        }
    }

    @Test
    fun decodeRejectsInvalidInstant() {
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(due = ""","due":"yesterday"""")))
        }
    }

    // Own words — the one thing in the document that is content, not a derivation

    private val umbrella = OwnWord(
        id = "own:regenschirm", kind = OwnWords.DEFAULT_KIND, emoji = "☂️",
        texts = mapOf("de" to "Regenschirm", "uk" to "парасолька"),
    )

    @Test
    fun ownWordsSurviveTheRoundTripAndRejoinAsCards() {
        val authored = BoxEngine.addOwnWord(state, umbrella, 1_700_000_000_000)
        val rejoined = StoreCodec.decode(StoreCodec.encode(authored))
            .join(StoreFixture.cards, StoreFixture.stamp)
        assertEquals(authored, rejoined)
        assertEquals("парасолька", rejoined.cards.getValue(umbrella.id).target.text)
    }

    @Test
    fun aDocumentWrittenBeforeOwnWordsExistedDecodesAsNone() {
        assertTrue(StoreCodec.decode(doc(entry())).ownWords.isEmpty())
    }

    // Reports — content the learner wrote, like their own words

    @Test
    fun reportsAndTheExportMarkSurviveTheRoundTrip() {
        val reported = BoxEngine.markExported(
            BoxEngine.reportIssue(state, StoreFixture.cards.first().id, "wrong", "typed", 1_700_000_000_000),
            1_700_000_100_000,
        )
        val rejoined = StoreCodec.decode(StoreCodec.encode(reported))
            .join(StoreFixture.cards, StoreFixture.stamp)
        assertEquals(reported, rejoined)
    }

    @Test
    fun aReportedIssueRoundTripsWithoutACommentOrAnInput() {
        val reported = BoxEngine.reportIssue(
            state, StoreFixture.cards.first().id, null, null, 1_700_000_000_000,
        )
        val rejoined = StoreCodec.decode(StoreCodec.encode(reported))
            .join(StoreFixture.cards, StoreFixture.stamp)
        assertEquals(reported.reportedIssues, rejoined.reportedIssues)
    }

    @Test
    fun aDocumentWrittenBeforeReportingExistedDecodesAsNone() {
        val decoded = StoreCodec.decode(doc(entry()))
        assertTrue(decoded.reportedIssues.isEmpty())
        assertEquals(null, decoded.lastExportAt)
    }

    @Test
    fun anOwnWordWrittenBeforeItsAgeWasRecordedDecodesAsOld() {
        val word = """{"id":"own:regen","kind":"noun","texts":{"de":"Regen","uk":"дощ"}}"""
        val decoded = StoreCodec.decode(doc(entry(), ownWords = word)).ownWords.single()
        assertEquals(Instant.DISTANT_PAST, decoded.addedAt)
    }

    @Test
    fun decodeAcceptsAnOwnWordWithNoPicture() {
        val word = """{"id":"own:regen","kind":"noun","texts":{"de":"Regen","uk":"дощ"}}"""
        val decoded = StoreCodec.decode(doc(entry(), ownWords = word)).ownWords.single()
        assertEquals(null, decoded.emoji)
        assertEquals(mapOf("de" to "Regen", "uk" to "дощ"), decoded.texts)
    }

    @Test
    fun decodeRejectsAnOwnWordIdThatCouldCollideWithTheCatalog() {
        val word = """{"id":"regen","kind":"noun","texts":{"de":"Regen"}}"""
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(), ownWords = word))
        }
    }

    @Test
    fun decodeRejectsAnOwnWordWithNoTextAtAll() {
        val word = """{"id":"own:regen","kind":"noun","texts":{}}"""
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(), ownWords = word))
        }
    }

    @Test
    fun decodeRejectsAnUnknownOwnWordKind() {
        val word = """{"id":"own:regen","kind":"weather","texts":{"de":"Regen"}}"""
        assertFailsWith<StoreFormatException> {
            StoreCodec.decode(doc(entry(), ownWords = word))
        }
    }
}
