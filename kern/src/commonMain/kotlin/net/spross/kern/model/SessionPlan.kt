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
    /** Cards whose due time has passed. */
    val reviews: List<String>,
    /** Not yet due, pulled forward — either asked for, or to fill a short round out. */
    val ahead: List<String>,
    val unlockedPhrases: List<String>,
    val newCards: List<String>,
    val joinStamp: JoinStamp,
) {
    /** The run, in order: due work first, warm-ups next, unseen words last. */
    val queue: List<String>
        get() = reviews + ahead + unlockedPhrases + newCards

    val isEmpty: Boolean
        get() = queue.isEmpty()

    val cardCount: Int
        get() = reviews.size + ahead.size + unlockedPhrases.size + newCards.size

    /** Entries the learner has never answered — unlocked phrases plus seed-order words. */
    val freshCount: Int
        get() = unlockedPhrases.size + newCards.size
}
