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
        val suspended = BoxEngine.setSuspended(answered, "w01", true, Box.day1)
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

    // Emptying the outbox

    /** A suggestion, a studiable own word and a filed report — one of each. */
    private fun outbox(): BoxState {
        var state = BoxEngine.addOwnWord(box(), ownWord("sonne", mapOf("de" to "Sonne")), Box.day1)
        state = BoxEngine.addOwnWord(
            state, ownWord("mwavuli", mapOf("de" to "Regenschirm", "sw" to "mwavuli")), Box.day1,
        )
        return BoxEngine.reportIssue(state, "w01", "wrong", null, Box.day1)
    }

    @Test
    fun clearingTakesTheSuggestionsAndTheReportsAndNothingElse() {
        val state = outbox()
        assertEquals(2, Feedback.clearableCount(state))

        val cleared = BoxEngine.clearFeedback(state)
        assertEquals(listOf("own:mwavuli"), cleared.ownWords.map { it.id })
        assertTrue(cleared.reportedIssues.isEmpty())
        assertEquals(0, Feedback.clearableCount(cleared))
    }

    @Test
    fun clearingKeepsAStudiedWordWithItsScheduleAndItsQueueSlot() {
        var state = outbox()
        state = BoxEngine.answer(state, "own:mwavuli", Rating.Good, Box.day1, "UTC")
        val cleared = BoxEngine.clearFeedback(state)

        assertEquals(state.scheduling, cleared.scheduling)
        assertEquals(state.enqueued, cleared.enqueued)
        assertEquals(state.cards.keys, cleared.cards.keys)
    }

    @Test
    fun clearingLeavesTheExportStampAlone() {
        // why: the stamp records that a copy was TAKEN, which emptying the outbox
        // afterwards does not undo — "only what is new" still measures from there.
        val state = BoxEngine.markExported(outbox(), Box.day1)
        assertEquals(state.lastExportAt, BoxEngine.clearFeedback(state).lastExportAt)
    }

    @Test
    fun aReportedOwnWordKeepsTheWordAndLosesTheReport() {
        var state = BoxEngine.addOwnWord(
            box(), ownWord("mwavuli", mapOf("de" to "Regenschirm", "sw" to "mwavuli")), Box.day1,
        )
        state = BoxEngine.reportIssue(state, "own:mwavuli", "typo", null, Box.day1)
        val cleared = BoxEngine.clearFeedback(state)

        assertEquals(listOf("own:mwavuli"), cleared.ownWords.map { it.id })
        assertTrue(cleared.reportedIssues.isEmpty())
    }

    @Test
    fun clearingAnEmptyOutboxChangesNothing() {
        val state = box()
        assertEquals(0, Feedback.clearableCount(state))
        assertEquals(state, BoxEngine.clearFeedback(state))
    }

    @Test
    fun fillingInTheMissingHalfTakesAWordOutOfTheOutbox() {
        val (state, half) = added(box(), ownWord("sonne", mapOf("de" to "Sonne")), Box.day1)
        assertEquals(1, Feedback.clearableCount(state))

        val whole = BoxEngine.updateOwnWord(
            state, half.copy(texts = mapOf("de" to "Sonne", "sw" to "jua")),
        )
        assertEquals(0, Feedback.clearableCount(whole))
        assertEquals(whole.ownWords, BoxEngine.clearFeedback(whole).ownWords)
    }
}
