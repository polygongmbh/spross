package net.spross.kern.listen

/** Which of a turn's three sayings is in the air. */
enum class ListeningBeat { Target, Meaning, Echo }

/**
 * One saying of a turn, and how long the run waits once its word has ACTUALLY ENDED.
 *
 * [inTarget] picks the language: the target the learner is learning, or their own.
 * [article] rides the target sayings alone —
 * the meaning is there to identify the word, and its grammar is not what is taught (`docs/read-aloud.md`).
 */
data class ListeningSaying(
    val beat: ListeningBeat,
    val form: String,
    val inTarget: Boolean,
    val article: String?,
    val gapMs: Long,
) {
    /** Whether the card shows its meaning while this saying is in the air: the meaning and the echo. */
    val revealed: Boolean get() = beat != ListeningBeat.Target
}

/**
 * The turn as said, in order: the target word, its meaning after the recall gap,
 * and the target again after the echo gap — the second saying is where the word and its meaning meet.
 * The last gap is the breath before the next turn.
 */
val ListeningTurn.sayings: List<ListeningSaying>
    get() = listOf(
        ListeningSaying(ListeningBeat.Target, targetForm, inTarget = true, spokenArticle, recallGapMs),
        ListeningSaying(ListeningBeat.Meaning, sourceForm, inTarget = false, article = null, echoGapMs),
        ListeningSaying(ListeningBeat.Echo, targetForm, inTarget = true, spokenArticle, turnGapMs),
    )

/** What the run does at the seam after a turn's last saying. */
enum class ListeningSeam {
    /** Draw the next turn ([ListeningIntent.Advance]). */
    Advance,

    /** The bedtime has arrived: the run closes and its screen leaves. */
    End,
}

/**
 * Whether a bedtime has arrived, [msRemaining] before it: at or past it.
 * A run with none set (null) never arrives anywhere.
 */
fun listeningBedtimeArrived(msRemaining: Long?): Boolean = msRemaining != null && msRemaining <= 0L

/**
 * The seam between two turns, [bedtimeMsRemaining] before the bedtime (null while none is set).
 *
 * The bedtime is read HERE and nowhere else:
 * a word cut off mid-air is exactly the change loud enough to wake someone that the fade spends the whole bedtime avoiding,
 * and the turn that runs over is already at the floor, the quietest of the run.
 */
fun listeningSeam(bedtimeMsRemaining: Long?): ListeningSeam =
    if (listeningBedtimeArrived(bedtimeMsRemaining)) ListeningSeam.End else ListeningSeam.Advance
