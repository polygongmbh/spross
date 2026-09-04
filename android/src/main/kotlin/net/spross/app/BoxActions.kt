package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.Feedback
import net.spross.kern.box.OwnWord
import net.spross.kern.box.OwnWords
import net.spross.kern.box.ReportedIssue
import net.spross.kern.model.Card

/**
 * What the learner does to ONE word of their box, and what they send back about the catalog.
 *
 * The rules — what a report holds, what an edit keeps, what counts as new since the last
 * copy, how the text reads — are kern's ([BoxEngine], [ReportedIssue], [Feedback]);
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

/** Drop one word's progress: back to new, card and report kept ([BoxEngine.forget]). */
fun AppModel.forgetCard(cardId: String) {
    updateBox { BoxEngine.forget(it, cardId) }
}

/** Take a word the learner wrote back out — the app's only deletion. */
fun AppModel.removeOwnWord(wordId: String) {
    updateBox { BoxEngine.removeOwnWord(it, wordId) }
}

/**
 * Store a word the learner just wrote, or rewrite the one they had opened.
 *
 * [rewriting] is what tells the two apart, and it is the whole of the difference:
 * [BoxEngine.updateOwnWord] keeps the id, and with it the schedule and the queue slot,
 * where taking it in afresh would mint a new word and leave the progress behind.
 */
fun AppModel.saveOwnWord(word: OwnWord, rewriting: Boolean) {
    updateBox {
        if (rewriting) BoxEngine.updateOwnWord(it, word) else BoxEngine.addOwnWord(it, word, now())
    }
}

/** Every word the learner wrote, oldest first — studiable ones and suggestions alike. */
val AppModel.ownWords: List<OwnWord> get() = box?.ownWords.orEmpty()

/** The ids already in use, so a newly written word cannot collide with one ([OwnWords.mint]). */
val AppModel.ownWordIds: Set<String> get() = ownWords.mapTo(mutableSetOf()) { it.id }

/**
 * Words the learner wrote down with only one half, oldest first. They join nothing and are
 * never scheduled ([OwnWords.cards]), so they carry no card row of their own.
 */
val AppModel.suggestions: List<OwnWord>
    get() = box?.let(Feedback::suggestions).orEmpty()

/**
 * Words written in both of the profile's languages, oldest first — study material with a
 * card behind it, and the complement of [suggestions].
 */
val AppModel.ownWordPairs: List<OwnWord>
    get() = box?.let(Feedback::wordPairs).orEmpty()

/**
 * The CATALOG cards a problem stands against, oldest report first.
 *
 * Own words are left out on purpose: a reported own word is already listed above wearing
 * its flag, and listing it twice in one section reads as two different problems.
 */
val AppModel.reportedCatalogCards: List<Card>
    get() {
        val state = box ?: return emptyList()
        return state.reportedIssues.values
            .sortedBy { it.reportedAt }
            .filterNot { OwnWords.owns(it.cardId) }
            .mapNotNull { state.cards[it.cardId] }
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

/**
 * How many entries a clear would take: the suggestions plus the filed reports. Kern's
 * count, not the screen's — a word written in both languages is study material and is
 * never in it ([Feedback.clearableCount]).
 */
val AppModel.clearableCount: Int get() = box?.let(Feedback::clearableCount) ?: 0

/** Empty the outbox: every suggestion and every report go, the word pairs stay. */
fun AppModel.clearFeedback() {
    updateBox(BoxEngine::clearFeedback)
}
