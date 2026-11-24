package net.spross.kern.listen

/**
 * What the lock screen calls a listening run: the app and the mode over the pair of languages the box joins.
 * The mode and the language names arrive worded in the language the learner already knows;
 * kern only says how they stand together.
 *
 * The lock-screen card names the run and never the turn, so it is pushed only when the run changes —
 * opened, paused, resumed, given a bedtime, closed — rather than per word:
 * the artwork crosses to the system whole on every push.
 * A bedtime alone gives the card a progress bar, since it is the only length a lapping run has.
 */
object NowPlayingLines {
    /** The app's own name, which needs no translating — it is the name on the launcher in every language. */
    const val BRAND: String = "Spross"

    /** The run's title: the app, then the [mode] it is in. */
    fun title(mode: String): String = "$BRAND · $mode"

    /** The pair the box joins, known first — the order the mode says them in; the known language alone where no target is open. */
    fun languages(source: String, target: String?): String =
        if (target == null) source else "$source – $target"
}
