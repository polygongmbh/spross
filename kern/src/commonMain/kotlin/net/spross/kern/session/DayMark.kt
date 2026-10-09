package net.spross.kern.session

/**
 * The mark a Home day card wears: the card's own [emoji], or the run's flame where it is null,
 * with the run's count beside it as one badge when [counted].
 * The count rides only with a run to name — unguarded it read "🔥 0 days" to anyone who had not started one.
 * Neither an emoji nor a count draws nothing.
 */
data class DayMark(val emoji: String?, val counted: Boolean) {
    companion object {
        /** The round's card: the flame with the run, a sparkle without one. */
        fun offer(streak: Int): DayMark = if (streak > 0) DayMark(null, true) else DayMark("✨", false)

        /** The card of a day with nothing due: a celebration once the day was worked, a sprout before, wearing the run. */
        fun done(worked: Boolean, streak: Int): DayMark = DayMark(if (worked) "🎉" else "🌱", streak > 0)

        /** The compact header over a drill lead: the flame with the run, nothing without one. */
        fun lead(streak: Int): DayMark = DayMark(null, streak > 0)
    }
}

/** Which round the button under a drill lead starts. */
enum class RoundStart {
    /** The day's round, while one is left. */
    Due,

    /** An extra round, once none is but the box can still compose one. */
    Extra,
}
