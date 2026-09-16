package net.spross.kern.box

import kotlin.time.Instant

/**
 * A content problem the learner filed against one card: a translation that is wrong,
 * a synonym the catalog should accept, a prompt that reads badly.
 *
 * It says nothing about scheduling. Reporting is deliberately independent of
 * [BoxEngine.setSuspended] — a word can be wrong and still worth practicing, and one
 * can be irrelevant without being wrong — so neither verb ever implies the other.
 */
data class ReportedIssue(
    val cardId: String,
    /** What the learner wrote about it; null when they filed without a word. */
    val comment: String?,
    /**
     * What they had typed as their answer when they filed. Always carried, never asked
     * about: the answer the catalog rejected IS the report in the common case (a valid
     * synonym marked wrong), and a learner who has to opt into attaching it will not.
     */
    val learnerInput: String?,
    val reportedAt: Instant,
)

/**
 * How much of what the learner wrote one report carries.
 *
 * The two differ exactly where a word has been written in BOTH of the profile's languages:
 * such a word is study material with progress on it, and a learner who only wants to tell
 * the catalog what it still owes them has no reason to hand their whole vocabulary over
 * with it.
 */
enum class FeedbackScope {
    /** Every own word, the finished pairs included, and every filed report. */
    Everything,

    /** Only what still waits for an answer: the one-sided suggestions, the notes, the reports. */
    Outbox,
}

/**
 * What the learner has to say back to whoever maintains the catalog: the words they
 * had to write themselves, the notes that name no word, and the problems they filed.
 *
 * The wording lives here rather than on each platform because a report is an
 * INTERCHANGE format, not screen chrome — two apps formatting it their own way would
 * mean the maintainer reading two dialects of the same thing. It is English for the
 * same reason: its reader is the maintainer, never the learner's own device.
 */
object Feedback {

    /** Subject line for the mail a report opens; it goes to [net.spross.kern.Legal.CONTACT_ADDRESS]. */
    const val MAIL_SUBJECT: String = "Spross catalog feedback"

    /**
     * The whole report as text: words the learner wrote, problems they filed against the
     * catalog, and general feedback — each section omitted when empty.
     *
     * ONE text for both ways out, the clipboard and the mail body. Every word carries its
     * language codes so the report is self-contained, independent of the open profile.
     *
     * [since] filters to what was written or filed after it — `null` takes the lot,
     * [scope] to how much of what survives that filter is the catalog's business,
     * and [appInfo] places a version/OS line at the top when provided.
     */
    fun reportText(
        state: BoxState,
        since: Instant?,
        scope: FeedbackScope,
        appInfo: String? = null,
    ): String {
        val words = exportedWords(state, since, scope)
        val remarks = exportedRemarks(state, since)
        val issues = issuesSince(state, since)
        val sections = mutableListOf<String>()
        appInfo?.let { sections += it }
        if (words.isNotEmpty()) {
            sections += "Word suggestions — check translations, consider fluency:\n" +
                words.joinToString("\n") { "- " + wordLine(it) }
        }
        if (issues.isNotEmpty()) {
            sections += "Remarks on catalog words:\n" +
                issues.joinToString("\n") { "- " + issueLine(state, it) }
        }
        if (remarks.isNotEmpty()) {
            sections += "General feedback:\n" +
                remarks.joinToString("\n") { "- " + it.comment.orEmpty() }
        }
        return sections.joinToString("\n\n")
    }

    /** Whether a report built with the same filters would carry anything at all. */
    fun hasAnything(state: BoxState, since: Instant?, scope: FeedbackScope): Boolean =
        exportedWords(state, since, scope).isNotEmpty() ||
            exportedRemarks(state, since).isNotEmpty() ||
            issuesSince(state, since).isNotEmpty()

    /** The own WORDS such a report carries, in the order they were written; never the notes. */
    fun exportedWords(state: BoxState, since: Instant?, scope: FeedbackScope): List<OwnWord> {
        val words =
            if (scope == FeedbackScope.Outbox) suggestions(state) else wordsAndSuggestions(state)
        return words.filter { since == null || it.addedAt > since }
    }

    /**
     * The notes such a report carries, in the order they were written.
     *
     * No [FeedbackScope] narrows them away: a note is never study material, so the scope
     * that leaves the learner's own vocabulary behind still owes its reader every one.
     */
    fun exportedRemarks(state: BoxState, since: Instant?): List<OwnWord> =
        remarks(state).filter { since == null || it.addedAt > since }

    /**
     * The words still waiting for a second language, oldest first — the half the learner
     * had, and the half the catalog owes ([OwnWord.isSuggestion]).
     */
    fun suggestions(state: BoxState): List<OwnWord> = state.ownWords.filter { it.isSuggestion }

    /**
     * The bare notes, oldest first: what the learner had to say that names no word at all
     * ([OwnWord.isRemark]).
     *
     * They are neither suggestions nor study material — the third thing the box holds, and
     * the one whose subject may be nothing in the catalog. Read here rather than per
     * platform so one surface cannot quietly count them among the words it suggests.
     */
    fun remarks(state: BoxState): List<OwnWord> = state.ownWords.filter { it.isRemark }

    /**
     * The words written in two languages or more, oldest first.
     *
     * They are the learner's own study material rather than an errand for the catalog, which
     * is why a surface that lists both lists them apart, and why emptying the outbox leaves
     * them where they are ([BoxEngine.clearFeedback]). A pair the open profile cannot study
     * ([OwnWord.joins]) is in here too: it is a finished word waiting for its pair to come
     * back round, not a half-written one.
     */
    fun wordPairs(state: BoxState): List<OwnWord> = state.ownWords.filter { it.isPair }

    /**
     * How much a [BoxEngine.clearFeedback] would take: the suggestions, the notes and the
     * filed reports. It is the whole of what waits to be sent on — a word written in both
     * languages is study material and is never counted here.
     */
    fun clearableCount(state: BoxState): Int =
        suggestions(state).size + remarks(state).size + state.reportedIssues.size

    /**
     * Whether this card can carry a report at all: a word the learner wrote themselves
     * cannot.
     *
     * A report is what they say to whoever maintains the CATALOG, and their own word has
     * nobody to tell — the form beside it already changes anything they would have
     * reported. [BoxEngine.reportIssue] refuses one, and a surface deciding whether to
     * offer the action asks here rather than spelling the id rule a second time.
     */
    fun isReportable(cardId: String): Boolean = !OwnWords.owns(cardId)

    /**
     * The problems standing against CATALOG cards, NEWEST first — the list a surface hands
     * back for review, and the complement of [wordPairs] and [suggestions] in that panel.
     *
     * Newest first because a review list is a queue of what still wants dealing with, and
     * the freshest problem is the one the learner can still say something about. The export
     * keeps the opposite order for the opposite reason ([issuesSince]). Both orders are
     * decided here: a surface that sorted for itself is how the two apps came to read the
     * same list in opposite directions.
     *
     * Own words are left out ([isReportable]): a reported one already stands in the list
     * above wearing its flag, and naming it twice in one section reads as two problems.
     */
    fun catalogIssues(state: BoxState): List<ReportedIssue> =
        state.reportedIssues.values
            .filter { isReportable(it.cardId) }
            .sortedByDescending { it.reportedAt }

    /**
     * Issues filed after [since], OLDEST first: the export is a log, and a log is read
     * forward. The screen's review list is [catalogIssues] and runs the other way.
     */
    fun issuesSince(state: BoxState, since: Instant?): List<ReportedIssue> =
        state.reportedIssues.values
            .filter { since == null || it.reportedAt > since }
            .sortedBy { it.reportedAt }

    /** The pairs and the suggestions, in the order they were written; the notes stay out. */
    private fun wordsAndSuggestions(state: BoxState): List<OwnWord> =
        state.ownWords.filterNot { it.isRemark }

    /** One word with language codes on every half — profile-independent. */
    private fun wordLine(word: OwnWord): String {
        val text = word.languages.joinToString(" → ") { "$it: ${word.texts[it]}" }
        return listOfNotNull(text, word.comment).joinToString("\n  ")
    }

    /** One issue, compact: `cardId lang/lang (typed: input): comment`. */
    private fun issueLine(state: BoxState, issue: ReportedIssue): String {
        val card = state.cards[issue.cardId]
        return buildString {
            append(issue.cardId)
            if (card != null) append(" ").append(card.source.lang).append("/").append(card.target.lang)
            issue.learnerInput?.let { append(" (typed: ").append(it).append(")") }
            issue.comment?.let { append(": ").append(it) }
        }
    }
}
