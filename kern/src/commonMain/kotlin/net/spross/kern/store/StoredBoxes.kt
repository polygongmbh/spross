package net.spross.kern.store

import kotlin.time.Instant
import net.spross.kern.box.BoxState
import net.spross.kern.box.DayTally
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.box.ReportedIssue
import net.spross.kern.box.answerDays
import net.spross.kern.box.mergeAnswerDays
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Language

/**
 * One target language's box as the store keeps it: what the learner did and wrote, and
 * nothing the build can work out again. Schedules arrive already replayed from their own
 * logs, so everything but `due` is this build's reading of them.
 */
data class StoredBox(
    val scheduling: Map<String, CardScheduling> = emptyMap(),
    val enqueued: List<String> = emptyList(),
    val ownWords: List<OwnWord> = emptyList(),
    val reportedIssues: Map<String, ReportedIssue> = emptyMap(),
    val lastExportAt: Instant? = null,
    /** Local to this device — an export leaves it behind ([BoxState.consolidatedToday]). */
    val consolidatedToday: DayTally? = null,
) {
    /**
     * Attach a fresh catalog join to obtain a live [BoxState]. Calibration belongs to the
     * BUILD — steps, retention and caps are decisions this version makes — so the product
     * configuration is applied here rather than carried in the file.
     *
     * The learner's own words are joined in here rather than by the caller: they are the
     * box's own content, and a caller that had to remember to merge them would one day forget.
     */
    fun join(cards: List<Card>, joinStamp: JoinStamp): BoxState = BoxState(
        config = BoxConfig.product(),
        cards = (cards + OwnWords.cards(ownWords, joinStamp.source, joinStamp.target))
            .associateBy { it.id },
        joinStamp = joinStamp,
        scheduling = scheduling,
        enqueued = enqueued,
        ownWords = ownWords,
        reportedIssues = reportedIssues,
        lastExportAt = lastExportAt,
        consolidatedToday = consolidatedToday,
    )

    companion object {
        fun of(state: BoxState): StoredBox = StoredBox(
            scheduling = state.scheduling,
            enqueued = state.enqueued,
            ownWords = state.ownWords,
            reportedIssues = state.reportedIssues,
            lastExportAt = state.lastExportAt,
            consolidatedToday = state.consolidatedToday,
        )
    }
}

/**
 * Every target language the learner has — which is what ONE store file holds, and why the
 * cross-language streak needs no second read of anything.
 */
data class StoredBoxes(val boxes: Map<Language, StoredBox> = emptyMap()) {

    /** The store with [state]'s own language written back into it. */
    fun with(state: BoxState): StoredBoxes =
        StoredBoxes(boxes + (state.joinStamp.target to StoredBox.of(state)))

    /**
     * A restore: every language [imported] carries replaces the one held here, and a
     * language it says nothing about is left exactly as it was.
     */
    fun restoring(imported: StoredBoxes): StoredBoxes = StoredBoxes(boxes + imported.boxes)

    /** Answers per day across every language but [target] — the cross-language streak's input. */
    fun answerDaysExcept(target: Language, tzId: String): Map<String, Int> = mergeAnswerDays(
        boxes.filterKeys { it != target }.values.map { answerDays(it.scheduling, tzId) },
    )

    companion object {
        val EMPTY: StoredBoxes = StoredBoxes()
    }
}
