package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.box.CatalogMatch
import net.spross.kern.box.CatalogMatches
import net.spross.kern.box.Feedback
import net.spross.kern.box.FeedbackScope
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

/**
 * Rewrite an entry the box holds no card for — a suggestion or a note — as the free text it
 * is: the half it carries stays in the language it was WRITTEN in, whatever pair happens to
 * be open, and the note is the rest of what the learner had to say. Both cleared would be an
 * entry that says nothing, so it is refused; taking it out is [removeOwnWord].
 */
fun AppModel.saveOwnEntry(word: OwnWord, text: String, comment: String) {
    val written = text.trim()
    val said = comment.trim()
    if (written.isEmpty() && said.isEmpty()) return
    val texts = word.texts.toMutableMap()
    word.languages.firstOrNull()?.let { language ->
        if (written.isEmpty()) texts.remove(language) else texts[language] = written
    }
    val rewritten = OwnWords.write(
        id = word.id,
        kind = word.kind,
        emoji = word.emoji,
        texts = texts,
        comment = said,
    )
    updateBox { BoxEngine.updateOwnWord(it, rewritten) }
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
 * The bare notes, oldest first: what the learner had to say that names no word at all
 * ([OwnWord.isRemark]). Neither suggestions nor study material — what a note is about need
 * not be in the catalog.
 */
val AppModel.remarks: List<OwnWord>
    get() = box?.let(Feedback::remarks).orEmpty()

/**
 * Words written in both of the profile's languages, oldest first — study material with a
 * card behind it, and the complement of [suggestions].
 */
val AppModel.ownWordPairs: List<OwnWord>
    get() = box?.let(Feedback::wordPairs).orEmpty()

/**
 * The CATALOG cards a problem stands against — which ones and in which order is
 * [Feedback.catalogIssues]; only resolving each to its card is this layer's.
 */
val AppModel.reportedCatalogCards: List<Card>
    get() {
        val state = box ?: return emptyList()
        return Feedback.catalogIssues(state).mapNotNull { state.cards[it.cardId] }
    }

/** The half a suggestion does carry, whichever language it is in. */
fun AppModel.suggestionText(word: OwnWord): String =
    word.languages.firstOrNull()?.let { word.texts[it] }.orEmpty()

/**
 * A pair this profile cannot study, read in the order kern names its languages — both
 * halves the learner wrote, neither of them the known or the learning side here.
 */
fun AppModel.otherPairText(word: OwnWord): String =
    word.languages.mapNotNull { word.texts[it] }.joinToString(" → ")

/**
 * The flags of the languages it is written in that this pair cannot read
 * ([OwnWord.languagesOutside]) — the whole of why it has no card, said without words.
 */
fun AppModel.otherPairFlags(word: OwnWord): String {
    val stamp = box?.joinStamp ?: return ""
    return word.languagesOutside(stamp.source, stamp.target)
        .mapNotNull { catalog?.languages?.get(it)?.flag }
        .joinToString("")
}

/** The same languages named, for a screen reader, which is handed no picture at all. */
fun AppModel.otherPairLanguageNames(word: OwnWord): String {
    val stamp = box?.joinStamp ?: return ""
    return word.languagesOutside(stamp.source, stamp.target)
        .joinToString(", ") { LanguageChoices.name(it, catalog?.languages?.get(it)) }
}

/**
 * Whether there is anything to copy or send at all — what withholds the actions.
 * [onlyNew] measures against the last time the learner took a copy, [scope] against how
 * much of what they wrote the catalog is owed.
 */
fun AppModel.hasFeedback(
    onlyNew: Boolean,
    scope: FeedbackScope = FeedbackScope.Everything,
): Boolean {
    val state = box ?: return false
    return Feedback.hasAnything(state, if (onlyNew) state.lastExportAt else null, scope)
}

/**
 * Whether a copy has ever been taken — what makes "only what is new" an offer worth
 * making rather than a second name for "everything".
 */
val AppModel.hasExportedBefore: Boolean get() = box?.lastExportAt != null

/**
 * What goes out as text — the same one the mail carries, so the clipboard can never come
 * back with less than the Send button would have sent.
 */
fun AppModel.reportText(onlyNew: Boolean, scope: FeedbackScope): String {
    val state = box ?: return ""
    return Feedback.reportText(state, if (onlyNew) state.lastExportAt else null, scope)
}

/** The same as a mail body, or null when there is nothing to say. */
fun AppModel.reportMailBody(onlyNew: Boolean, scope: FeedbackScope): String? =
    if (hasFeedback(onlyNew, scope)) reportText(onlyNew, scope) else null

/**
 * Record that a copy has just been taken — what a later "only what is new" measures
 * against. Whether this scope moves the stamp at all is kern's ([BoxEngine.markExported]).
 *
 * Stamped rather than updated: [AppModel.updateBox] re-walks the whole box for the
 * statistics, the streak strip and the shelf counts, and the stamp feeds none of them —
 * it is read by this file alone.
 */
fun AppModel.markExported(scope: FeedbackScope) {
    stampBox { BoxEngine.markExported(it, now(), scope) }
}

/**
 * How many entries a clear would take: the suggestions, the notes and the filed reports.
 * Kern's count, not the screen's — a word written in both languages is study material and
 * is never in it ([Feedback.clearableCount]).
 */
val AppModel.clearableCount: Int get() = box?.let(Feedback::clearableCount) ?: 0

/** Empty the outbox: every suggestion, every note and every report go, the pairs stay. */
fun AppModel.clearFeedback() {
    updateBox(BoxEngine::clearFeedback)
}

// The catalog catching up with a word the learner had to write themselves
// (`CatalogMatches`, `BoxEngine.mergeOwnWord`, kern §6).

/**
 * Every own word the catalog now has a word for, the whole matches leading. A walk of every
 * card, so it is taken once when the sheet opens and not per recomposition.
 */
fun AppModel.catalogMatches(): List<CatalogMatch> =
    box?.let { CatalogMatches.of(it) }.orEmpty()

/**
 * Move the chosen words onto their catalog cards, in one write.
 *
 * One at a time off the state the last one returned, exactly as a harvest is taken in: each
 * merge rewrites the box, and a batch aimed at the state this started from would merge every
 * word onto a box that no longer holds the one before it.
 */
fun AppModel.merge(matches: List<CatalogMatch>) {
    if (matches.isEmpty()) return
    updateBox { state ->
        matches.fold(state) { carried, match ->
            BoxEngine.mergeOwnWord(carried, match.word.id, match.cardId)
        }
    }
}

/**
 * The catalog's own writing of a matched card, target side first as every exposure surface
 * reads (`kern/docs/reports.md`).
 */
fun AppModel.catalogText(match: CatalogMatch): String {
    val card = box?.cards?.get(match.cardId) ?: return ""
    return "${card.target.text} → ${card.source.text}"
}

/** The learner's own word as they wrote it, read the same way round. */
fun AppModel.writtenText(match: CatalogMatch): String {
    val stamp = box?.joinStamp ?: return ""
    val word = match.word
    return listOfNotNull(word.texts[stamp.target], word.texts[stamp.source]).joinToString(" → ")
}
