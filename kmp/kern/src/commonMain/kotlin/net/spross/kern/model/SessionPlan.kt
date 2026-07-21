package net.spross.kern.model

/** Identifies the join a plan was composed against; the app recomposes when stale. */
data class JoinStamp(
    val source: Language,
    val target: Language,
    val catalogFingerprint: String,
)

/** A composed session; all entry lists carry unit keys. */
data class SessionPlan(
    val reviews: List<String>,
    val unlockedPhrases: List<String>,
    val newUnits: List<String>,
    val joinStamp: JoinStamp,
) {
    val isEmpty: Boolean
        get() = reviews.isEmpty() && unlockedPhrases.isEmpty() && newUnits.isEmpty()
}
