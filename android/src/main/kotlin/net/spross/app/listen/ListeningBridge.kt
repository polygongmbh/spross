package net.spross.app.listen

/**
 * What a listening run puts on the lock screen, and what a button there may do to it.
 *
 * The run lives on the model and the notification lives in a [ListeningService]; the two are
 * separate objects with no handle on each other, and Android hands a service no way to reach
 * a ViewModel. This is that handle, and it is deliberately the whole of it — one nullable
 * reference each way, written and read on the main thread only, so there is no second copy of
 * the run's state for the shade to fall behind on.
 */
object ListeningBridge {

    /** The run in progress, or null between runs. */
    var controls: ListeningControls? = null

    private var listener: ((ListeningNowPlaying?) -> Unit)? = null

    /** What the shade should be showing; null takes the run off it. */
    var nowPlaying: ListeningNowPlaying? = null
        set(value) {
            field = value
            listener?.invoke(value)
        }

    /** The service registering for changes; it is handed what stands right away. */
    fun observe(onChange: ((ListeningNowPlaying?) -> Unit)?) {
        listener = onChange
        onChange?.invoke(nowPlaying)
    }
}

/**
 * The four things a run can be asked to do, wherever the ask comes from.
 *
 * The lock screen, a headphone button and the on-screen buttons all arrive here and all drive
 * the SAME reducer — there is no second state machine for the notification, which is the only
 * way the shade and the screen can never disagree about whether a run is paused.
 */
interface ListeningControls {
    fun togglePause()
    fun skip()
    fun repeat()
    fun close()
}

/** The app's own name, which needs no translating — it is the name on the launcher too. */
const val SPROSS_BRAND = "Spross"

/**
 * The run as the lock screen shows it: what the run IS, and the word in the air inside it.
 *
 * The headline is the part that HOLDS STILL — the app, the mode and the pair of languages —
 * because a card that renamed itself every few seconds is a card nobody can read from a
 * pocket, and every player the system knows treats a changed title as a changed track. The
 * word goes on the line beneath, where a track inside a run belongs.
 *
 * The labels travel WITH the state rather than being looked up in the service, because chrome
 * is keyed to the language the learner already knows and only the model holds that. A service
 * reading its own resources would say the notification in the phone's language while the app
 * beside it said everything in the profile's.
 */
data class ListeningNowPlaying(
    /** "Spross · Wörter hören" — the run's own name, and the notification channel's. */
    val title: String,
    /** "Deutsch – Kiswahili", known first: the pair, in the order the mode says them. */
    val languages: String,
    /** The target word with its article, exactly as the voice says it. */
    val target: String,
    val paused: Boolean,
    /** The bedtime as a length played and a length to go, or null where none is set. */
    val bedtime: ListeningBedtimeProgress?,
    val pauseLabel: String,
    val resumeLabel: String,
    val skipLabel: String,
    val repeatLabel: String,
    val closeLabel: String,
)

/**
 * How far into its bedtime a run is — the pair a lock screen draws a progress bar from.
 *
 * A run without a bedtime has none: a playlist that laps for as long as it is left alone has
 * nothing to be a fraction of, and a bar over it would be a lie.
 */
data class ListeningBedtimeProgress(val elapsedMs: Long, val totalMs: Long)
