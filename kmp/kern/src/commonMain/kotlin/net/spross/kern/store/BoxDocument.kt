package net.spross.kern.store

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.CardPhase
import net.spross.kern.model.DayStats
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating
import net.spross.kern.model.ReviewLogEntry
import net.spross.kern.model.Role
import net.spross.kern.model.UnitKey
import net.spross.kern.model.UnitScheduling

/**
 * Persisted aggregate for one TARGET language (`box-<target>.json`), schema version 1.
 * Every serializable type in this file stays internal — the public door is [StoreCodec];
 * a public @Serializable class would flood the ObjC header with serialization internals.
 */
@Serializable
internal data class BoxDocument(
    val schemaVersion: Int,
    val target: String,
    val source: String,
    val config: ConfigDto,
    val scheduling: Map<String, UnitDto>,
    val enqueued: List<String>,
    val newIntroduced: Map<String, Int>,
    val dailyStats: Map<String, DayStatsDto>,
)

@Serializable
internal data class ConfigDto(
    val maxLearning: Int,
    val sessionCap: Int,
    val dueSoftCap: Int,
    val desiredRetention: Double,
    val maximumIntervalDays: Int,
    val phraseUnlockStability: Double,
    val learningStepsSeconds: List<Long>,
    val relearningStepsSeconds: List<Long>,
)

@Serializable
internal data class UnitDto(
    val cardId: String,
    val role: String,
    val form: String? = null,
    @Serializable(with = IsoInstantSerializer::class) val addedAt: Instant,
    val phase: String,
    val stepIndex: Int? = null,
    val memory: MemoryDto? = null,
    @Serializable(with = IsoInstantSerializer::class) val due: Instant? = null,
    val lapses: Int,
    val suspended: Boolean,
    val log: List<LogEntryDto>,
)

@Serializable
internal data class MemoryDto(val stability: Double, val difficulty: Double)

@Serializable
internal data class LogEntryDto(
    @Serializable(with = IsoInstantSerializer::class) val date: Instant,
    val rating: Int,
    val elapsedDays: Double,
)

@Serializable
internal data class DayStatsDto(val reviews: Int, val introduced: Int, val activeCount: Int)

// Encoding (state → document)

internal fun boxDocument(state: BoxState): BoxDocument = BoxDocument(
    schemaVersion = StoreCodec.SCHEMA_VERSION,
    target = state.joinStamp.target,
    source = state.joinStamp.source,
    config = configDto(state.config),
    scheduling = state.scheduling.mapValues { unitDto(it.value) },
    enqueued = state.enqueued,
    newIntroduced = state.newIntroduced,
    dailyStats = state.dailyStats.mapValues { dayStatsDto(it.value) },
)

private fun configDto(config: BoxConfig): ConfigDto = ConfigDto(
    maxLearning = config.maxLearning,
    sessionCap = config.sessionCap,
    dueSoftCap = config.dueSoftCap,
    desiredRetention = config.desiredRetention,
    maximumIntervalDays = config.maximumIntervalDays,
    phraseUnlockStability = config.phraseUnlockStability,
    learningStepsSeconds = config.learningStepsSeconds,
    relearningStepsSeconds = config.relearningStepsSeconds,
)

private fun unitDto(sched: UnitScheduling): UnitDto = UnitDto(
    cardId = sched.cardId,
    role = sched.role.keySegment,
    form = sched.form,
    addedAt = sched.addedAt,
    phase = phaseName(sched.phase),
    stepIndex = sched.stepIndex,
    memory = sched.memory?.let { MemoryDto(it.stability, it.difficulty) },
    due = sched.due,
    lapses = sched.lapses,
    suspended = sched.suspended,
    log = sched.log.map { LogEntryDto(it.date, it.rating.value, it.elapsedDays) },
)

internal fun dayStatsDto(stats: DayStats): DayStatsDto =
    DayStatsDto(reviews = stats.reviews, introduced = stats.introduced, activeCount = stats.activeCount)

private fun phaseName(phase: CardPhase): String = when (phase) {
    CardPhase.New -> "new"
    CardPhase.Learning -> "learning"
    CardPhase.Review -> "review"
    CardPhase.Relearning -> "relearning"
}

// Decoding (document → validated aggregate)

private fun fail(message: String): Nothing = throw StoreFormatException(message)

internal fun BoxDocument.toDecoded(): DecodedBox {
    if (schemaVersion != StoreCodec.SCHEMA_VERSION) {
        fail("unsupported schemaVersion $schemaVersion (expected ${StoreCodec.SCHEMA_VERSION})")
    }
    if (target.isBlank() || source.isBlank() || target == source) {
        fail("invalid profile: source=\"$source\" target=\"$target\"")
    }
    return DecodedBox(
        target = target,
        source = source,
        config = config.toDomain(),
        scheduling = scheduling.entries.associate { (key, dto) -> key to dto.toDomain(key) },
        enqueued = enqueued,
        newIntroduced = newIntroduced,
        dailyStats = dailyStats.mapValues { it.value.toDomain() },
    )
}

private fun ConfigDto.toDomain(): BoxConfig = BoxConfig(
    maxLearning = maxLearning,
    sessionCap = sessionCap,
    dueSoftCap = dueSoftCap,
    desiredRetention = desiredRetention,
    maximumIntervalDays = maximumIntervalDays,
    phraseUnlockStability = phraseUnlockStability,
    learningStepsSeconds = learningStepsSeconds,
    relearningStepsSeconds = relearningStepsSeconds,
)

private fun UnitDto.toDomain(key: String): UnitScheduling {
    val parsedRole = when (role) {
        Role.Produce.keySegment -> Role.Produce
        Role.Recognize.keySegment -> Role.Recognize
        else -> fail("scheduling entry $key: unknown role \"$role\"")
    }
    val parsedPhase = when (phase) {
        "new" -> CardPhase.New
        "learning" -> CardPhase.Learning
        "review" -> CardPhase.Review
        "relearning" -> CardPhase.Relearning
        else -> fail("scheduling entry $key: unknown phase \"$phase\"")
    }
    val unitKey = try {
        UnitKey(cardId, parsedRole, form)
    } catch (e: IllegalArgumentException) {
        fail("scheduling entry $key: ${e.message}")
    }
    if (unitKey.encoded != key) {
        fail("scheduling key \"$key\" does not match its entry (\"${unitKey.encoded}\")")
    }
    val isNew = parsedPhase == CardPhase.New
    if (isNew != (memory == null) || isNew != (due == null)) {
        fail("scheduling entry $key violates the phase/memory/due invariant")
    }
    return UnitScheduling(
        cardId = cardId,
        role = parsedRole,
        form = form,
        addedAt = addedAt,
        phase = parsedPhase,
        stepIndex = stepIndex,
        memory = memory?.let { MemoryState(stability = it.stability, difficulty = it.difficulty) },
        due = due,
        lapses = lapses,
        suspended = suspended,
        log = log.map { entry ->
            ReviewLogEntry(
                date = entry.date,
                rating = Rating.entries.firstOrNull { it.value == entry.rating }
                    ?: fail("scheduling entry $key: unknown rating ${entry.rating}"),
                elapsedDays = entry.elapsedDays,
            )
        },
    )
}

private fun DayStatsDto.toDomain(): DayStats =
    DayStats(reviews = reviews, introduced = introduced, activeCount = activeCount)
