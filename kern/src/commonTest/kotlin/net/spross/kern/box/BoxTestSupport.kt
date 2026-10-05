package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.model.ReviewLogEntry
import net.spross.kern.session.SessionComposer

/** Shared builders for box-engine scenario tests — fixed UTC clock, hand-built joins. */
internal object Box {
    const val TZ = "UTC"

    fun millis(y: Int, m: Int, d: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(y, m, d, hour, minute).toInstant(TimeZone.UTC).toEpochMilliseconds()

    /** noon, 2026-07-01 — the default "today" for scenarios. */
    val day1: Long = millis(2026, 7, 1)

    fun instant(millis: Long): Instant = Instant.fromEpochMilliseconds(millis)

    /** The product ladder these scenarios run on — index it, never restate its numbers. */
    val steps: List<Long> = BoxConfig().stepsSeconds

    fun plusSeconds(millis: Long, s: Long): Long = millis + s * 1000
    fun plusDays(millis: Long, d: Double): Long = millis + (d * 86_400_000).toLong()

    /** The due queue's card ids at [now], in its order. */
    fun dueIds(state: BoxState, now: Long): List<String> = Inventory.due(state, now).map { it.cardId }

    fun word(
        n: Int,
        area: String = "area1",
        kind: CardKind = CardKind.Noun,
        teaches: List<String> = emptyList(),
        accepts: List<String> = emptyList(),
    ): Card = Card(
        id = "w" + n.toString().padStart(2, '0'),
        kind = kind, area = area, emoji = null, seedIndex = n,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "de", text = "g$n"),
        target = Realization(lang = "sw", text = "t$n", teaches = teaches, accepts = accepts),
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

    fun config(sessionCap: Int = BoxConfig().sessionCap): BoxConfig = BoxConfig(sessionCap = sessionCap)

    val stamp = JoinStamp("de", "sw", "fixture")

    fun state(cards: List<Card>, config: BoxConfig = config()): BoxState =
        BoxEngine.bootstrap(cards, config, stamp)

    /** Hand-crafted schedule entry for scenario setup (Review phase by default). */
    fun sched(
        cardId: String,
        phase: CardPhase = CardPhase.Review,
        stability: Double = 10.0,
        dueMillis: Long,
        lastReviewMillis: Long,
        suspended: Boolean = false,
        /** Review-log length — presentation-role input ([net.spross.kern.model.presentationRole]). */
        logCount: Int = 1,
    ): CardScheduling = CardScheduling(
        cardId = cardId,
        phase = phase,
        stepIndex = if (phase == CardPhase.Learning || phase == CardPhase.Relearning) 0 else null,
        memory = MemoryState(stability = stability, difficulty = 5.0),
        due = instant(dueMillis),
        suspended = suspended,
        log = List(logCount) { ReviewLogEntry(instant(lastReviewMillis), Rating.Good) },
    )

    fun inject(state: BoxState, entry: CardScheduling): BoxState =
        state.copy(scheduling = state.scheduling + (entry.cardId to entry))

    /** The id [word] gives card [n]. */
    fun id(n: Int): String = "w" + n.toString().padStart(2, '0')

    /**
     * A box at [day1] over [catalog] words, scheduled from `w01` on: [due] cards overdue by
     * an hour more each, then [soon] shaky ones back within the next hours, then [later]
     * ones back from day five on. Every word past those is unseen.
     */
    fun scenario(
        catalog: Int,
        due: Int = 0,
        soon: Int = 0,
        later: Int = 0,
        config: BoxConfig = config(),
    ): BoxState {
        var state = state((1..catalog).map { word(it) }, config)
        var n = 0
        repeat(due) {
            n += 1
            state = inject(state, sched(id(n), dueMillis = day1 - n * 3_600_000L, lastReviewMillis = plusDays(day1, -10.0)))
        }
        repeat(soon) { i ->
            n += 1
            state = inject(
                state,
                sched(id(n), stability = 0.5, dueMillis = plusSeconds(day1, 3_600L * (i + 1)), lastReviewMillis = plusDays(day1, -1.0)),
            )
        }
        repeat(later) { i ->
            n += 1
            state = inject(state, sched(id(n), dueMillis = plusDays(day1, 5.0 + i), lastReviewMillis = plusDays(day1, -1.0)))
        }
        return state
    }

    /** Forty overdue cards and [spare] unseen words: more due work than one round holds. */
    fun backlogged(spare: Int = 10): BoxState = scenario(catalog = 40 + spare, due = 40)

    /** Answer, returning the resulting state. */
    fun answered(state: BoxState, cardId: String, rating: Rating, nowMillis: Long): BoxState =
        BoxEngine.answer(state, cardId, rating, nowMillis)

    /**
     * A box carrying [answers] answers logged on [nowMillis] — what closes a day
     * ([net.spross.kern.box.answersOn]). They sit on a schedule this join does not
     * carry, so the day is worked without composition seeing another card.
     */
    fun workedToday(state: BoxState, answers: Int, nowMillis: Long = day1): BoxState =
        inject(
            state,
            sched("answers-today", dueMillis = nowMillis, lastReviewMillis = nowMillis, logCount = answers),
        )

    /** Growth candidates under composer-computed budgets (the session composer's diet). */
    fun candidates(
        state: BoxState,
        capacity: Int = state.config.sessionCap,
    ): NewCandidates = Growth.newCandidates(
        state,
        budget = SessionComposer.NEW_CARDS_PER_ROUND,
        capacity = capacity,
    )
}
