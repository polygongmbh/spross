package net.spross.kern.model

/** Identifies the join a plan was composed against; the app recomposes when stale. */
data class JoinStamp(
    val source: Language,
    val target: Language,
    val catalogFingerprint: String,
)

/**
 * A composed session; all entry lists carry card ids. Composition is
 * role-agnostic — the presentation of each entry is resolved at render time
 * from the card's log count ([presentationRole]).
 */
data class SessionPlan(
    val reviews: List<String>,
    val unlockedPhrases: List<String>,
    val newCards: List<String>,
    val joinStamp: JoinStamp,
) {
    val isEmpty: Boolean
        get() = reviews.isEmpty() && unlockedPhrases.isEmpty() && newCards.isEmpty()
}
