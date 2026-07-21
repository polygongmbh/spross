package net.spross.kern.model

import kotlin.time.Instant

enum class Rating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4),
}

enum class CardPhase { New, Learning, Review, Relearning }

/** FSRS memory state. */
data class MemoryState(
    val stability: Double,
    val difficulty: Double,
)

data class ReviewLogEntry(
    val date: Instant,
    val rating: Rating,
    /** Fractional days since the previous log entry; 0 on introduction. */
    val elapsedDays: Double,
)

/**
 * One scheduling unit (one exercise of one card).
 * Invariant: `phase == New ⟺ memory == null ⟺ due == null`.
 * cardId/role/form are explicit; the map key is derived ([key]) and validated on decode.
 */
data class UnitScheduling(
    val cardId: String,
    val role: Role,
    /** Normalized form key; present iff recognize. */
    val form: String? = null,
    val addedAt: Instant,
    val phase: CardPhase = CardPhase.New,
    val memory: MemoryState? = null,
    val due: Instant? = null,
    val lapses: Int = 0,
    val suspended: Boolean = false,
    /** Appended on EVERY answer, including same-day retries. */
    val log: List<ReviewLogEntry> = emptyList(),
) {
    val key: String get() = UnitKey(cardId, role, form).encoded
}
