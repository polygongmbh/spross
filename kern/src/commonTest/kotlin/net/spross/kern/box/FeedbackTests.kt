package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Rating

class FeedbackTests {

    private fun box(): BoxState = Box.state(listOf(Box.word(1), Box.word(2)))

    private fun ownWord(id: String, texts: Map<String, String>) = OwnWord(
        id = OwnWords.ID_PREFIX + id,
        kind = OwnWords.DEFAULT_KIND,
        emoji = null,
        texts = texts,
    )

    /** The word as the box holds it once taken in — the engine stamps its age. */
    private fun added(state: BoxState, word: OwnWord, at: Long): Pair<BoxState, OwnWord> {
        val next = BoxEngine.addOwnWord(state, word, at)
        return next to next.ownWords.first { it.id == word.id }
    }

    // Filing a report

    @Test
    fun aReportedIssueIsKeptAgainstItsCard() {
        val state = BoxEngine.reportIssue(box(), "w01", "reads wrong", "t2", Box.day1)
        val issue = state.reportedIssues.getValue("w01")
        assertEquals("reads wrong", issue.comment)
        assertEquals("t2", issue.learnerInput)
        assertEquals(Box.instant(Box.day1), issue.reportedAt)
    }

    @Test
    fun reportingLeavesSchedulingAlone() {
        val answered = BoxEngine.answer(box(), "w01", Rating.Good, Box.day1, "UTC")
        val reported = BoxEngine.reportIssue(answered, "w01", null, null, Box.day1)
        assertEquals(answered.scheduling, reported.scheduling)
        assertFalse(reported.scheduling.getValue("w01").suspended)
    }

    @Test
    fun suspendingDoesNotReportAndReportingDoesNotSuspend() {
        val answered = BoxEngine.answer(box(), "w01", Rating.Good, Box.day1, "UTC")
        val suspended = BoxEngine.setSuspended(answered, "w01", true)
        assertTrue(suspended.reportedIssues.isEmpty())

        val reported = BoxEngine.reportIssue(answered, "w01", "wrong", null, Box.day1)
        assertFalse(reported.scheduling.getValue("w01").suspended)
    }

    @Test
    fun aReportNeedsNoScheduleAndNoAnswer() {
        // why: reveal comes BEFORE the first answer, so a card reported on sight has
        // no scheduling entry at all — the report must not be dropped for it.
        val state = BoxEngine.reportIssue(box(), "w01", "wrong", null, Box.day1)
        assertNull(state.scheduling["w01"])
        assertTrue("w01" in state.reportedIssues)
    }

    @Test
    fun blankCommentAndInputReadAsAbsent() {
        val state = BoxEngine.reportIssue(box(), "w01", "  ", "", Box.day1)
        val issue = state.reportedIssues.getValue("w01")
        assertNull(issue.comment)
        assertNull(issue.learnerInput)
    }

    @Test
    fun filingAgainReplacesTheEarlierReport() {
        var state = BoxEngine.reportIssue(box(), "w01", "first", null, Box.day1)
        state = BoxEngine.reportIssue(state, "w01", "second", null, Box.day1)
        assertEquals(1, state.reportedIssues.size)
        assertEquals("second", state.reportedIssues.getValue("w01").comment)
    }

    @Test
    fun anUnjoinedCardCannotBeReported() {
        assertEquals(box(), BoxEngine.reportIssue(box(), "nope", "wrong", null, Box.day1))
    }

    @Test
    fun dismissingWithdrawsTheReport() {
        val state = BoxEngine.reportIssue(box(), "w01", "wrong", null, Box.day1)
        assertTrue(BoxEngine.dismissReportedIssue(state, "w01").reportedIssues.isEmpty())
        assertEquals(state, BoxEngine.dismissReportedIssue(state, "w02"))
    }

    @Test
    fun resetKeepsWhatTheLearnerWroteAndDropsWhatTheBoxComputed() {
        var state = BoxEngine.reportIssue(box(), "w01", "wrong", null, Box.day1)
        state = BoxEngine.answer(state, "w01", Rating.Good, Box.day1, "UTC")
        state = BoxEngine.markExported(state, Box.day1)
        val fresh = BoxEngine.reset(state)
        assertEquals(state.reportedIssues, fresh.reportedIssues)
        assertEquals(state.lastExportAt, fresh.lastExportAt)
        assertTrue(fresh.scheduling.isEmpty())
    }

    @Test
    fun removingAnOwnWordTakesItsReportWithIt() {
        val word = ownWord("regenschirm", mapOf("de" to "Regenschirm", "sw" to "mwavuli"))
        var state = BoxEngine.addOwnWord(box(), word, Box.day1)
        state = BoxEngine.reportIssue(state, word.id, "typo", null, Box.day1)
        assertTrue(BoxEngine.removeOwnWord(state, word.id).reportedIssues.isEmpty())
    }

    // Suggestions — own words written in only one language

    @Test
    fun aWordWrittenInOneLanguageIsASuggestionAndIsNeverScheduled() {
        val (state, half) = added(box(), ownWord("sonne", mapOf("de" to "Sonne")), Box.day1)
        assertTrue(half.isSuggestion(Box.stamp.source, Box.stamp.target))
        assertNull(state.cards[half.id])
        assertTrue(state.enqueued.isEmpty())
        assertEquals(listOf(half), state.ownWords)
    }

    // What goes out

    @Test
    fun onlyWhatIsNewSinceTheLastExportGoesOut() {
        val (afterOld, old) = added(box(), ownWord("alt", mapOf("de" to "alt", "sw" to "kuukuu")), Box.day1)
        val (withBoth, fresh) = added(
            afterOld, ownWord("neu", mapOf("de" to "neu")), Box.plusDays(Box.day1, 2.0),
        )
        val state = BoxEngine.markExported(withBoth, Box.plusDays(Box.day1, 1.0))

        assertEquals(listOf(old, fresh), Feedback.ownWordsSince(state, null))
        assertEquals(listOf(fresh), Feedback.ownWordsSince(state, state.lastExportAt))
    }

    @Test
    fun aWordFromBeforeTheBoxRecordedItsAgeReadsAsOld() {
        val ancient = OwnWord(
            id = "own:alt",
            kind = OwnWords.DEFAULT_KIND,
            emoji = null,
            texts = mapOf("de" to "alt", "sw" to "kuukuu"),
        )
        // why: the engine stamps every word it takes in, so the only way a word can
        // carry DISTANT_PAST is to have been decoded from a document written before
        // the box recorded ages at all.
        val stored = box().copy(ownWords = listOf(ancient))
        val state = BoxEngine.markExported(stored, Box.day1)
        assertTrue(Feedback.ownWordsSince(state, state.lastExportAt).isEmpty())
    }

    @Test
    fun theReportNamesTheProfileTheWordsAndTheIssues() {
        var state = BoxEngine.addOwnWord(box(), ownWord("sonne", mapOf("de" to "Sonne")), Box.day1)
        state = BoxEngine.reportIssue(state, "w01", "t1 should accept t9", "t9", Box.day1)

        assertEquals(
            """
            de → sw

            Suggested words (1):
            - Sonne → ?

            Reported issues (1):
            - w01: g1 → t1
              typed: t9
              comment: t1 should accept t9
            """.trimIndent(),
            Feedback.reportText(state, null),
        )
    }

    @Test
    fun anEmptySectionIsLeftOutEntirely() {
        val state = BoxEngine.reportIssue(box(), "w01", null, null, Box.day1)
        val text = Feedback.reportText(state, null)
        assertFalse("Suggested words" in text)
        assertTrue("Reported issues (1)" in text)
        assertFalse("typed:" in text)
        assertFalse("comment:" in text)
    }

    @Test
    fun nothingToSayIsVisibleBeforeTheMailIsBuilt() {
        assertFalse(Feedback.hasAnything(box(), null))
        val state = BoxEngine.reportIssue(box(), "w01", null, null, Box.day1)
        assertTrue(Feedback.hasAnything(state, null))
        assertFalse(Feedback.hasAnything(state, Box.instant(Box.plusDays(Box.day1, 1.0))))
    }

    @Test
    fun theClipboardTextIsOneWordPerLine() {
        val paired = ownWord("regenschirm", mapOf("de" to "Regenschirm", "sw" to "mwavuli"))
        var state = BoxEngine.addOwnWord(box(), paired, Box.day1)
        state = BoxEngine.addOwnWord(state, ownWord("sonne", mapOf("de" to "Sonne")), Box.day1)
        assertEquals("Regenschirm → mwavuli\nSonne → ?", Feedback.ownWordsText(state, null))
    }
}
