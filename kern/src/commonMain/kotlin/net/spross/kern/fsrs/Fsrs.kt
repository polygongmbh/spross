package net.spross.kern.fsrs

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/**
 * FSRS-6 formula set. Pure math over [MemoryState]; no clocks — callers pass
 * elapsed time in fractional days (`< 1.0` takes the same-day short-term path).
 *
 * Formulas follow ts-fsrs v5.4.1 / py-fsrs v6.3.1 with the divergences resolved
 * toward ts-fsrs/fsrs-rs: same-day `sinc >= 1` mask for Hard and above, and the
 * S_MIN 0.001 initial-stability floor (ts-only 0.1 floor not adopted).
 */
class Fsrs(val parameters: FsrsParameters = FsrsParameters()) {

    private val w: List<Double> = parameters.w

    /** decay = −w20; factor makes R(t = S) = 0.9 exactly. */
    private val decay: Double = -w[20]
    private val factor: Double = 0.9.pow(1.0 / decay) - 1.0

    /** R(t, S) = (1 + factor · t / S)^decay — probability of recall, in (0, 1]. */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        val s = max(stability, S_MIN)
        val t = max(elapsedDays, 0.0)
        return (1.0 + factor * t / s).pow(decay)
    }

    /**
     * Memory state after a review. `memory == null` means first review
     * (initial S0/D0); `elapsedDays < 1.0` takes the short-term path.
     */
    fun nextMemory(memory: MemoryState?, elapsedDays: Double, rating: Rating): MemoryState {
        if (memory == null) {
            return MemoryState(
                stability = initialStability(rating),
                difficulty = initialDifficulty(rating),
            )
        }
        val s = max(memory.stability, S_MIN)
        val d = clampDifficulty(memory.difficulty)
        val t = max(elapsedDays, 0.0)
        val stability = when {
            t < 1.0 -> shortTermStability(s, rating)
            rating == Rating.Again -> {
                // why: cap post-lapse stability at the same-day-again outcome so a lapse
                // can never leave the card stronger than failing it immediately would.
                val ceiling = s / exp(w[17] * w[18])
                min(max(ceiling, S_MIN), forgetStability(d, s, retrievability(t, s)))
            }
            else -> recallStability(d, s, retrievability(t, s), rating)
        }
        return MemoryState(stability = stability, difficulty = nextDifficulty(d, rating))
    }

    /**
     * I(rd, S) = S / factor · (rd^(1/decay) − 1), in fractional days and
     * unclamped — the interval the model actually asks for. Equals S at
     * rd = 0.9. [FsrsScheduler] quantizes and clamps it per parameters.
     */
    fun intervalRawDays(
        stability: Double,
        desiredRetention: Double = parameters.desiredRetention,
    ): Double {
        require(desiredRetention > 0.0 && desiredRetention <= 1.0) {
            "desiredRetention must be in (0, 1]"
        }
        return stability / factor * (desiredRetention.pow(1.0 / decay) - 1.0)
    }

    /**
     * [intervalRawDays] rounded half-up to whole days, then clamped to
     * `[1, maximumIntervalDays]` — the reference day-bucket convention.
     */
    fun intervalDays(
        stability: Double,
        desiredRetention: Double = parameters.desiredRetention,
    ): Int {
        val rounded = floor(intervalRawDays(stability, desiredRetention) + 0.5)
        return min(max(rounded, 1.0), parameters.maximumIntervalDays.toDouble()).toInt()
    }

    // Formula internals (exposed to the module's tests for the reference unit vectors).

    /** S0(G) = w[G−1], floored at S_MIN (py-fsrs/fsrs-rs convention). */
    internal fun initialStability(rating: Rating): Double =
        max(w[rating.value - 1], S_MIN)

    /** D0(G) = w4 − e^{w5·(G−1)} + 1, unclamped (mean reversion anchors on the raw value). */
    internal fun rawInitialDifficulty(rating: Rating): Double =
        w[4] - exp(w[5] * (rating.value - 1)) + 1.0

    internal fun initialDifficulty(rating: Rating): Double =
        clampDifficulty(rawInitialDifficulty(rating))

    /** ΔD = −w6·(G−3) with linear damping, then mean reversion toward UNCLAMPED D0(Easy). */
    internal fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val deltaD = -w[6] * (rating.value - 3)
        val damped = difficulty + deltaD * (10.0 - difficulty) / 9.0
        val reverted = w[7] * rawInitialDifficulty(Rating.Easy) + (1.0 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    /** S′_r = S·(1 + e^{w8}·(11−D)·S^{−w9}·(e^{w10·(1−R)}−1)·hardPenalty·easyBonus) */
    internal fun recallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
        val growth = exp(w[8]) *
            (11.0 - difficulty) *
            stability.pow(-w[9]) *
            (exp(w[10] * (1.0 - retrievability)) - 1.0) *
            hardPenalty *
            easyBonus
        return clampStability(stability * (1.0 + growth))
    }

    /** S′_f = w11·D^{−w12}·((S+1)^{w13}−1)·e^{w14·(1−R)} */
    internal fun forgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double = clampStability(
        w[11] *
            difficulty.pow(-w[12]) *
            ((stability + 1.0).pow(w[13]) - 1.0) *
            exp(w[14] * (1.0 - retrievability)),
    )

    /**
     * Same-day: S′_s = S · sinc with sinc = S^{−w19}·e^{w17·(G−3+w18)}.
     * why: sinc is clamped >= 1 for G >= Hard (fsrs-rs/ts-fsrs semantics; py-fsrs
     * v6.3.1 masks only Good/Easy — resolved toward the canonical algorithm source).
     */
    internal fun shortTermStability(stability: Double, rating: Rating): Double {
        val sinc = stability.pow(-w[19]) * exp(w[17] * (rating.value - 3 + w[18]))
        val masked = if (rating.value >= Rating.Hard.value) max(sinc, 1.0) else sinc
        return clampStability(stability * masked)
    }

    private fun clampDifficulty(difficulty: Double): Double = min(max(difficulty, 1.0), 10.0)

    private fun clampStability(stability: Double): Double = min(max(stability, S_MIN), S_MAX)

    companion object {
        const val S_MIN: Double = 0.001
        const val S_MAX: Double = 36500.0
    }
}
