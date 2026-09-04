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

    /** Only what still waits for an answer: the one-sided suggestions and the reports. */
    Outbox,
}

/**
 * What the learner has to say back to whoever maintains the catalog: the words they
 * had to write themselves, and the problems they filed.
 *
 * The wording lives here rather than on each platform because a report is an
 * INTERCHANGE format, not screen chrome — two apps formatting it their own way would
 * mean the maintainer reading two dialects of the same thing. It is English for the
 * same reason: its reader is the maintainer, never the learner's own device.
 */
object Feedback {

    /** Subject line for the mail a report opens; it goes to [net.spross.kern.Legal.CONTACT_ADDRESS]. */
    const val MAIL_SUBJECT: String = "Spross catalog feedback"

    /** Stands in for the missing half of a word written in only one language. */
    private const val UNTRANSLATED = "?"

    /**
     * The whole report as text: the profile, the learner's own words, then the problems
     * they filed — each section omitted when it is empty, and the whole thing empty only
     * when [hasAnything] is false.
     *
     * ONE text for both ways out, the clipboard and the mail body. A clipboard form that
     * carried only the words would come back empty for a learner who has filed reports and
     * written nothing, which is a copy button that silently does nothing.
     *
     * A word written in only one language prints its missing half as [UNTRANSLATED] rather
     * than being left out: it is the entry most worth reading. [since] filters to what was
     * written or filed after it — `null` takes the lot — and [scope] to how much of what
     * survives that filter is the catalog's business.
     */
    fun reportText(state: BoxState, since: Instant?, scope: FeedbackScope): String {
        val words = exportedWords(state, since, scope)
        val issues = issuesSince(state, since)
        val sections = mutableListOf<String>()
        sections += "${state.joinStamp.source} → ${state.joinStamp.target}"
        if (words.isNotEmpty()) {
            sections += "Suggested words (${words.size}):\n" +
                words.joinToString("\n") { "- " + wordLine(state, it) }
        }
        if (issues.isNotEmpty()) {
            sections += "Reported issues (${issues.size}):\n" +
                issues.joinToString("\n") { issueLines(state, it) }
        }
        return sections.joinToString("\n\n")
    }

    /** Whether a report built with the same filters would carry anything at all. */
    fun hasAnything(state: BoxState, since: Instant?, scope: FeedbackScope): Boolean =
        exportedWords(state, since, scope).isNotEmpty() || issuesSince(state, since).isNotEmpty()

    /** The own words such a report carries, in the order they were written. */
    fun exportedWords(state: BoxState, since: Instant?, scope: FeedbackScope): List<OwnWord> {
        val words = if (scope == FeedbackScope.Outbox) suggestions(state) else state.ownWords
        return words.filter { since == null || it.addedAt > since }
    }

    /**
     * The words still waiting for one of the profile's two languages, oldest first.
     *
     * Read here rather than per platform because being a suggestion is a JOIN question —
     * the same word is a suggestion under one pair and a card under another — and a
     * surface that answered it itself would answer it for the pair it happens to draw.
     */
    fun suggestions(state: BoxState): List<OwnWord> =
        state.ownWords.filter { it.isSuggestion(state.joinStamp.source, state.joinStamp.target) }

    /**
     * The words written in both of the profile's languages, oldest first: the complement of
     * [suggestions], and the ones the join has made cards of.
     *
     * They are the learner's own study material rather than an errand for the catalog, which
     * is why a surface that lists both lists them apart, and why emptying the outbox leaves
     * them where they are ([BoxEngine.clearFeedback]).
     */
    fun wordPairs(state: BoxState): List<OwnWord> =
        state.ownWords.filterNot { it.isSuggestion(state.joinStamp.source, state.joinStamp.target) }

    /**
     * How much a [BoxEngine.clearFeedback] would take: the suggestions plus the filed
     * reports. It is the whole of what waits to be sent on — a word written in both
     * languages is study material and is never counted here.
     */
    fun clearableCount(state: BoxState): Int =
        suggestions(state).size + state.reportedIssues.size

    /** Issues filed after [since], oldest first. */
    fun issuesSince(state: BoxState, since: Instant?): List<ReportedIssue> =
        state.reportedIssues.values
            .filter { since == null || it.reportedAt > since }
            .sortedBy { it.reportedAt }

    private fun wordLine(state: BoxState, word: OwnWord): String {
        val known = word.texts[state.joinStamp.source] ?: UNTRANSLATED
        val learning = word.texts[state.joinStamp.target] ?: UNTRANSLATED
        return "$known → $learning"
    }

    private fun issueLines(state: BoxState, issue: ReportedIssue): String {
        val card = state.cards[issue.cardId]
        val pair = if (card == null) issue.cardId
        else "${issue.cardId}: ${card.source.text} → ${card.target.text}"
        return buildString {
            append("- ").append(pair)
            issue.learnerInput?.let { append("\n  typed: ").append(it) }
            issue.comment?.let { append("\n  comment: ").append(it) }
        }
    }
}
