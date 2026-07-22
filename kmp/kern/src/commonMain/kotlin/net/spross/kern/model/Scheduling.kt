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
 * ONE schedule per card (user ruling 2026-07-22): production and recognition
 * reviews alternate as PRESENTATIONS of the same memory — see [presentationRole].
 * Keyed by [cardId] in every scheduling map.
 * Invariant: `phase == New ⟺ memory == null ⟺ due == null`.
 */
data class CardScheduling(
    val cardId: String,
    val addedAt: Instant,
    val phase: CardPhase = CardPhase.New,
    /** Step position within Learning/Relearning steps; null in New/Review. */
    val stepIndex: Int? = null,
    val memory: MemoryState? = null,
    val due: Instant? = null,
    val lapses: Int = 0,
    val suspended: Boolean = false,
    /** Appended on EVERY answer, including same-day retries. */
    val log: List<ReviewLogEntry> = emptyList(),
) {
    init {
        require(cardId.isNotEmpty() && '|' !in cardId) { "invalid cardId: $cardId" }
    }

    /** Presentation input: [presentationRole] of the NEXT review reads this count. */
    val reviewCount: Int get() = log.size
}
