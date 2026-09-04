package net.spross.app.listen

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.app.AppModel
import net.spross.app.audio.Pronouncer
import net.spross.app.ui.languageName
import net.spross.kern.listen.LISTENING_WATCHDOG_MS
import net.spross.kern.listen.ListeningCandidate
import net.spross.kern.listen.ListeningEffect
import net.spross.kern.listen.ListeningIntent
import net.spross.kern.listen.ListeningReduction
import net.spross.kern.listen.ListeningRun
import net.spross.kern.listen.ListeningRunState
import net.spross.kern.listen.ListeningTurn
import net.spross.kern.listen.listeningGainDb
import net.spross.kern.listen.listeningTimerStepMs

/** Which of a turn's three sayings is in the air. The meaning arrives with its reading. */
enum class ListeningBeat { Target, Meaning, Echo }

/**
 * The platform half of a listening run: kern's reducer decides WHAT is said, this arms WHEN.
 *
 * A turn is three sayings and three gaps, and every beat after the first is armed off the
 * previous word ACTUALLY ENDING plus kern's number — never off a timer guessing how long a
 * word lasts, which is the one thing that would make the mode drift on a slow voice or a long
 * phrase. Each beat takes a token; anything that arrives in between — a skip, a pause, the
 * lock screen, a focus loss — bumps the counter and the chain in flight simply stops firing.
 * A word that never reports (an engine that dropped it, a clip that never decoded) is caught
 * by the ceiling below, so a run can stall on nothing.
 *
 * The audio is driven off the reduction's EFFECTS, never off a state diff: `Repeat` returns
 * an identical state and its whole observable is the `Play` it asks for.
 *
 * It touches no box. Nothing here books a review, writes a schedule or moves the streak —
 * listening answers nothing, so it costs the box nothing (`docs/surfaces.md`).
 */
class ListeningDriver(
    private val app: Application,
    private val model: AppModel,
) : ListeningControls {

    /** The run, or null between runs. The screen and the lock screen both read this. */
    var state by mutableStateOf<ListeningRunState?>(null)
        private set

    /** Which saying is in the air, so the screen can let the meaning arrive with its reading. */
    var beat by mutableStateOf<ListeningBeat?>(null)
        private set

    /**
     * The stretch the bedtime standing runs over, in milliseconds; 0 is off, which is where a
     * run starts. Every tap resets it — a bedtime extended at midnight is a new stretch, not
     * the old one with a longer tail, so kern's ramp starts over from full.
     */
    var timerTotalMs by mutableStateOf(0L)
        private set

    /** When the bedtime falls, or null while the run laps for as long as it is left alone. */
    var deadline by mutableStateOf<Long?>(null)
        private set

    val turn: ListeningTurn? get() = state?.turn
    val active: Boolean get() = state?.active == true
    val paused: Boolean get() = state?.paused == true

    private val handler = Handler(Looper.getMainLooper())

    /** Which beat is the newest — everything armed under an older token is dead. */
    private var generation = 0

    /** Whether the pause standing was the platform's doing, and so may be lifted again. */
    private var pausedByFocus = false

    private val focus = ListeningAudioFocus(
        app,
        onLoss = {
            // why: a call or another player asking for its turn pauses the run rather than
            // talking underneath it — and the pause is remembered as not the learner's.
            if (active && !paused) {
                pausedByFocus = true
                dispatch(ListeningIntent.TogglePause)
            }
        },
        onGain = {
            if (active && paused && pausedByFocus) {
                pausedByFocus = false
                dispatch(ListeningIntent.TogglePause)
            }
        },
    )

    /** Opens the playlist. A pool with nothing in it never opens a run at all. */
    fun start(candidates: List<ListeningCandidate>) {
        if (active || candidates.isEmpty()) return
        timerTotalMs = 0
        deadline = null
        pausedByFocus = false
        focus.take()
        ListeningBridge.controls = this
        apply(ListeningRun.reduce(ListeningRun.idle(candidates), ListeningIntent.Start))
        // why: the state is published by the reduction above, so the service's very first
        // notification already carries the word rather than an empty shell of one.
        ListeningService.start(app)
    }

    override fun togglePause() {
        // The learner's own pause outranks the platform's: lifting it is theirs to do.
        pausedByFocus = false
        dispatch(ListeningIntent.TogglePause)
    }

    override fun skip() = dispatch(ListeningIntent.Skip)

    override fun repeat() = dispatch(ListeningIntent.Repeat)

    /**
     * The ✕ as the lock screen and a headphone stop button reach it. It goes through the
     * MODEL rather than straight to [stop], because closing a run also leaves its screen —
     * a phone unlocked after this must not come back to a run that is no longer playing.
     */
    override fun close() = model.closeListening()

    /** Ends the run: the audio stops, the focus goes back, and whatever was playing resumes. */
    fun stop() {
        if (state != null) dispatch(ListeningIntent.Close)
        generation++
        state = null
        beat = null
        deadline = null
        timerTotalMs = 0
        pausedByFocus = false
        focus.release()
        ListeningBridge.controls = null
        ListeningBridge.nowPlaying = null
        ListeningService.stop(app)
    }

    /**
     * Adds kern's five minutes to WHAT IS LEFT of the bedtime — never to what was picked, so a
     * run four minutes into a five-minute bedtime gets six more and not ten
     * (`listeningTimerStepMs`). Every tap only ever ADDS: the minutes never come down by
     * tapping, and the chip's long press is the one way back to off.
     */
    fun cycleTimer() {
        timerTotalMs = listeningTimerStepMs(remainingMs() ?: 0L, 1)
        deadline = System.currentTimeMillis() + timerTotalMs
        // why: the lock screen's progress bar is the same clock the chip is, so it is
        // redrawn on the tap rather than waiting out the word in the air.
        publish()
    }

    /**
     * Long-press: the bedtime is cleared and the run laps again from where it is — the one
     * gesture that reaches zero, which the tap never can.
     */
    fun turnOffTimer() {
        timerTotalMs = 0
        deadline = null
        publish()
    }

    /** How long the bedtime has left, or null where none was set. */
    fun remainingMs(): Long? = deadline?.let { it - System.currentTimeMillis() }

    private fun dispatch(intent: ListeningIntent) {
        val current = state ?: return
        apply(ListeningRun.reduce(current, intent))
    }

    private fun apply(reduction: ListeningReduction) {
        // why: every intent kills the chain in flight — a gap armed off the word that was
        // just cut must not walk the playlist on behind the learner.
        generation++
        val was = state?.let { it.paused to it.active }
        state = reduction.state
        for (effect in reduction.effects) {
            when (effect) {
                is ListeningEffect.Play -> sound(effect.turn, ListeningBeat.Target)
                ListeningEffect.Stop -> {
                    model.pronouncer.stop()
                    beat = null
                }
            }
        }
        // why: the card carries nothing that moves with a turn, so a turn is not a reason to
        // push one — only a run that started, stopped or changed its mind about playing is.
        // Asked HERE rather than at each caller so no route in can forget: a headphone
        // button, a focus loss and the on-screen button all arrive through this one door.
        if (was != (paused to active)) publish()
    }

    /**
     * What the lock screen shows. The chrome travels with it because chrome is keyed to the
     * language the learner already knows, and only the model holds that — a service reading
     * its own resources would label the notification in the phone's language instead.
     */
    private fun publish() {
        val run = state?.takeIf { it.active }
        val turn = run?.turn
        val chrome = model.chrome
        ListeningBridge.nowPlaying = if (turn == null) {
            null
        } else {
            ListeningNowPlaying(
                title = "$SPROSS_BRAND · ${chrome.listenTitle}",
                languages = languages(),
                paused = run.paused,
                bedtime = remainingMs()?.let {
                    ListeningBedtimeProgress(timerTotalMs - it.coerceAtLeast(0L), timerTotalMs)
                },
                pauseLabel = chrome.listenPause,
                resumeLabel = chrome.listenResume,
                skipLabel = chrome.listenSkip,
                repeatLabel = chrome.listenRepeat,
                closeLabel = chrome.commonClose,
            )
        }
    }

    /** The pair the box joins, known first — the order the mode says them in. */
    private fun languages(): String {
        val stamp = model.box?.joinStamp ?: return ""
        return "${model.languageName(stamp.source)} – ${model.languageName(stamp.target)}"
    }

    /** One saying, and the gap that follows it once the word has actually ended. */
    private fun sound(turn: ListeningTurn, at: ListeningBeat) {
        beat = at
        val token = ++generation
        val gap = when (at) {
            ListeningBeat.Target -> turn.recallGapMs
            ListeningBeat.Meaning -> turn.echoGapMs
            ListeningBeat.Echo -> turn.turnGapMs
        }
        say(turn, at, token) {
            handler.postDelayed({
                if (token != generation) return@postDelayed
                when (at) {
                    ListeningBeat.Target -> sound(turn, ListeningBeat.Meaning)
                    ListeningBeat.Meaning -> sound(turn, ListeningBeat.Echo)
                    // why: the bedtime ends the run at the SEAM between turns, never at the
                    // deadline itself — a word cut off mid-air is exactly the change loud
                    // enough to wake someone that the ramp spends the whole bedtime avoiding.
                    // The turn is already down at the floor by now, so the few seconds it
                    // runs over are the quietest of the run.
                    ListeningBeat.Echo ->
                        if (expired()) model.closeListening() else dispatch(ListeningIntent.Advance)
                }
            }, gap)
        }
    }

    /**
     * Says one side of the turn and reports back when the word has ended.
     *
     * The article rides the TARGET sayings alone: the meaning is there to identify the word,
     * and its grammar is not what is being taught (`docs/read-aloud.md`).
     */
    private fun say(turn: ListeningTurn, at: ListeningBeat, token: Int, onDone: () -> Unit) {
        val stamp = model.box?.joinStamp
        val meaning = at == ListeningBeat.Meaning
        val lang = if (meaning) stamp?.source else stamp?.target
        val form = if (meaning) turn.sourceForm else turn.targetForm
        var reported = false
        val finish = {
            if (!reported && token == generation) {
                reported = true
                onDone()
            }
        }
        // why: the article rides into the LOOKUP as well as into the voice — the target
        // beat may have a recording that speaks it, the meaning beat never does.
        val article = if (meaning) null else turn.spokenArticle
        val pronunciation = lang?.let { model.catalog?.pronunciation(it, form, article) }
        if (pronunciation == null) {
            finish()
            return
        }
        model.pronouncer.pronounce(
            pronunciation,
            Pronouncer.Trigger.LISTENING,
            article,
            fadeDb(),
            finish,
        )
        // why: insurance, not timing — a word whose end is never reported would leave the
        // chain standing still, and a run that has gone quiet is worse than one that hurries.
        // Kern owns the ceiling so the two phones cannot wait different lengths.
        handler.postDelayed({ finish() }, LISTENING_WATCHDOG_MS)
    }

    /**
     * Kern's bedtime ramp at this moment: quieter by degrees over the whole run rather than a
     * dimming that starts. The length rides along with what is left of it, so the ramp is a
     * fraction of the bedtime and every length ends in the same place.
     */
    private fun fadeDb(): Double =
        remainingMs()?.let { listeningGainDb(it, timerTotalMs) } ?: 0.0

    /**
     * The bedtime has arrived: a deadline in the past. A run with none set never arrives.
     * Asked at the seam between turns and nowhere else, so what it ends is a turn that
     * finished rather than a word halfway out.
     */
    private fun expired(): Boolean = remainingMs()?.let { it <= 0 } == true
}
