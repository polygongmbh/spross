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

/**
 * One answer, as the box keeps it. The elapsed time FSRS wants is the gap to the previous
 * entry, so it is derived where it is needed rather than stored beside the two facts it
 * follows from.
 */
data class ReviewLogEntry(
    val date: Instant,
    val rating: Rating,
)

/**
 * ONE schedule per card (user ruling 2026-07-22): production and recognition
 * reviews alternate as PRESENTATIONS of the same memory — see [presentationRole].
 * Keyed by [cardId] in every scheduling map.
 * Invariant: `phase == New ⟺ memory == null ⟺ due == null`.
 */
data class CardScheduling(
    val cardId: String,
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
