package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.model.ReviewLogEntry
import net.spross.kern.model.Role
import net.spross.kern.model.UnitKey
import net.spross.kern.model.UnitScheduling

/** Shared builders for box-engine scenario tests — fixed UTC clock, hand-built joins. */
internal object Box {
    const val TZ = "UTC"

    fun millis(y: Int, m: Int, d: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(y, m, d, hour, minute).toInstant(TimeZone.UTC).toEpochMilliseconds()

    /** noon, 2026-07-01 — the default "today" for scenarios. */
    val day1: Long = millis(2026, 7, 1)

    fun instant(millis: Long): Instant = Instant.fromEpochMilliseconds(millis)

    fun plusSeconds(millis: Long, s: Long): Long = millis + s * 1000
    fun plusDays(millis: Long, d: Double): Long = millis + (d * 86_400_000).toLong()

    fun word(
        n: Int,
        area: String = "area1",
        kind: CardKind = CardKind.Noun,
        synonyms: List<String> = emptyList(),
    ): Card = Card(
        id = "w" + n.toString().padStart(2, '0'),
        kind = kind, area = area, emoji = null, seedIndex = n,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "de", text = "g$n"),
        target = Realization(lang = "sw", text = "t$n", synonyms = synonyms),
        promptFeminineMarker = false,
    )

    fun phrase(
        id: String,
        components: List<String>,
        area: String = "area1",
        seedIndex: Int = 90,
    ): Card = Card(
        id = id, kind = CardKind.Phrase, area = area, emoji = null, seedIndex = seedIndex,
        components = components, feminineOf = null,
        source = Realization(lang = "de", text = id),
        target = Realization(lang = "sw", text = id),
        promptFeminineMarker = false,
    )

    fun config(maxLearning: Int = 8, dueSoftCap: Int = 60, sessionCap: Int = 30): BoxConfig =
        BoxConfig(maxLearning = maxLearning, dueSoftCap = dueSoftCap, sessionCap = sessionCap)

    val stamp = JoinStamp("de", "sw", "fixture")

    fun state(cards: List<Card>, config: BoxConfig = config()): BoxState =
        BoxEngine.bootstrap(cards, config, stamp)

    fun produce(id: String): String = UnitKey.produce(id).encoded
    fun recognize(id: String, form: String): String = UnitKey.recognize(id, form).encoded

    /** Hand-crafted schedule entry for scenario setup (produce by default). */
    fun sched(
        cardId: String,
        role: Role = Role.Produce,
        form: String? = null,
        phase: CardPhase = CardPhase.Review,
        stability: Double = 10.0,
        dueMillis: Long,
        lastReviewMillis: Long,
        lapses: Int = 0,
        suspended: Boolean = false,
    ): UnitScheduling = UnitScheduling(
        cardId = cardId, role = role, form = form,
        addedAt = instant(lastReviewMillis),
        phase = phase,
        stepIndex = if (phase == CardPhase.Learning || phase == CardPhase.Relearning) 0 else null,
        memory = MemoryState(stability = stability, difficulty = 5.0),
        due = instant(dueMillis),
        lapses = lapses,
        suspended = suspended,
        log = listOf(ReviewLogEntry(instant(lastReviewMillis), Rating.Good, 1.0)),
    )

    fun inject(state: BoxState, entry: UnitScheduling): BoxState =
        state.copy(scheduling = state.scheduling + (entry.key to entry))

    /** Answer asserting the outcome applied. */
    fun answered(state: BoxState, unitKey: String, rating: Rating, nowMillis: Long): BoxState {
        val outcome = BoxEngine.answer(state, unitKey, rating, nowMillis, TZ)
        check(outcome.status == AnswerStatus.Applied) {
            "expected Applied for $unitKey, got ${outcome.status}"
        }
        return outcome.state
    }

    /** Growth candidates under composer-computed budgets (the session composer's diet). */
    fun candidates(
        state: BoxState,
        nowMillis: Long = day1,
        capacity: Int = state.config.sessionCap,
    ): NewCandidates = Growth.newCandidates(
        state,
        conceptBudget = Growth.learningPoolBudget(state),
        gateOpen = Growth.healthGateOpen(state, nowMillis),
        capacityUnits = capacity,
    )
}
