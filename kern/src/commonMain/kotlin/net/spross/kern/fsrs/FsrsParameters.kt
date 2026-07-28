package net.spross.kern.fsrs

/**
 * FSRS-6 parameters. Defaults mirror the pinned references
 * (ts-fsrs v5.4.1 / py-fsrs v6.3.1 — identical values) so the golden vectors
 * run verbatim; the product overrides retention, maximum interval, and
 * relearning steps from `BoxConfig`.
 */
data class FsrsParameters(
    /** 21 weights; `w[20]` is the trainable decay (default 0.1542). */
    val w: List<Double> = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9,
    val maximumIntervalDays: Int = 36500,
    /** Learning steps in seconds (reference `[1m, 10m]`). */
    val learningStepsSeconds: List<Long> = listOf(60L, 600L),
    /** Relearning steps in seconds (reference `[10m]`; product keeps `[10m]` — model/Config.kt). */
    val relearningStepsSeconds: List<Long> = listOf(600L),
    /**
     * Granularity a graduated interval is rounded to. Whole days by default —
     * the reference day-bucket convention the golden vectors are pinned to;
     * the product schedules continuously (1 s) so a 5.4-day interval is not
     * rounded down to 5.
     */
    val intervalGranularitySeconds: Long = 86_400L,
    /**
     * Floor for a graduated interval. One day by default: bringing a card back
     * inside the same day is what a learning step is for, not what a schedule
     * that already graduated should ask for.
     */
    val minimumIntervalSeconds: Long = 86_400L,
) {
    init {
        require(w.size == WEIGHT_COUNT) { "FSRS-6 needs $WEIGHT_COUNT weights, got ${w.size}" }
        require(desiredRetention > 0.0 && desiredRetention <= 1.0) {
            "desiredRetention must be in (0, 1]"
        }
        require(maximumIntervalDays >= 1) { "maximumIntervalDays must be >= 1" }
        require(learningStepsSeconds.all { it > 0 }) { "learning steps must be positive" }
        require(relearningStepsSeconds.all { it > 0 }) { "relearning steps must be positive" }
        require(intervalGranularitySeconds >= 1) { "interval granularity must be >= 1 s" }
        require(minimumIntervalSeconds >= 1) { "minimum interval must be >= 1 s" }
    }

    companion object {
        const val WEIGHT_COUNT: Int = 21

        /** Default weight vector, identical in ts-fsrs v5.4.1, py-fsrs v6.3.1, fsrs-rs. */
        val DEFAULT_WEIGHTS: List<Double> = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722,
            0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425,
            0.0912, 0.0658, 0.1542,
        )
    }
}
