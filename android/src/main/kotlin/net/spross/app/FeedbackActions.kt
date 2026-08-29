package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.Feedback
import net.spross.kern.box.OwnWord
import net.spross.kern.box.ReportedIssue

/**
 * Telling whoever maintains the catalog what is wrong with it, and what is missing from it.
 *
 * The rules — what a report holds, what counts as new since the last one, how the text
 * reads — are kern's ([ReportedIssue], [Feedback]);
 * this layer carries the clock, the clipboard and the mail app.
 */

/** The problem filed against this card, if any. */
fun AppModel.reportedIssue(cardId: String): ReportedIssue? = box?.reportedIssues?.get(cardId)

/**
 * File a content problem against one card. [learnerInput] is whatever they had typed —
 * always carried, never asked about ([ReportedIssue]).
 */
fun AppModel.reportIssue(cardId: String, comment: String?, learnerInput: String?) {
    updateBox { BoxEngine.reportIssue(it, cardId, comment, learnerInput, now()) }
}

/** Withdraw a report; the card's schedule is untouched either way. */
fun AppModel.dismissReportedIssue(cardId: String) {
    updateBox { BoxEngine.dismissReportedIssue(it, cardId) }
}

/**
 * Words the learner wrote down with only one half, oldest first. They join nothing and are
 * never scheduled ([net.spross.kern.box.OwnWords.cards]), so no card row can list them —
 * the feedback section is the only place they are visible.
 */
val AppModel.suggestions: List<OwnWord>
    get() {
        val stamp = box?.joinStamp ?: return emptyList()
        return box?.ownWords.orEmpty().filter { it.isSuggestion(stamp.source, stamp.target) }
    }

/** The half a suggestion does carry, whichever of the two languages it is in. */
fun AppModel.suggestionText(word: OwnWord): String {
    val stamp = box?.joinStamp ?: return ""
    return word.texts[stamp.target] ?: word.texts[stamp.source] ?: ""
}

/**
 * Whether there is anything to copy or send at all — what withholds the actions.
 * [onlyNew] measures against the last time the learner took a copy.
 */
fun AppModel.hasFeedback(onlyNew: Boolean): Boolean {
    val state = box ?: return false
    return Feedback.hasAnything(state, if (onlyNew) state.lastExportAt else null)
}

/**
 * Whether a copy has ever been taken — what makes "only what is new" an offer worth
 * making rather than a second name for "everything".
 */
val AppModel.hasExportedBefore: Boolean get() = box?.lastExportAt != null

/**
 * The suggestions and the reports as text — the same one the mail carries, so the
 * clipboard can never come back with less than the Send button would have sent.
 */
fun AppModel.reportText(onlyNew: Boolean): String {
    val state = box ?: return ""
    return Feedback.reportText(state, if (onlyNew) state.lastExportAt else null)
}

/**
 * The suggestions and the reports as a mail body, or null when there is nothing to say.
 */
fun AppModel.reportMailBody(onlyNew: Boolean): String? =
    if (hasFeedback(onlyNew)) reportText(onlyNew) else null

/**
 * Record that a copy has just been taken — what a later "only what is new" measures
 * against. Taking everything still marks it: either way the learner has now seen the lot.
 */
fun AppModel.markExported() {
    updateBox { BoxEngine.markExported(it, now()) }
}
