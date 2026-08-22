package net.spross.app.listen

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.app.AppModel
import net.spross.app.audio.Pronouncer
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
    private val rng = Random.Default

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
        apply(ListeningRun.reduce(ListeningRun.idle(candidates), ListeningIntent.Start, rng))
        // why: the state is published by the reduction above, so the service's very first
        // notification already carries the word rather than an empty shell of one.
        ListeningService.start(app)
    }

    /**
     * The pool was rebuilt under a running playlist (a voice arrived in Settings, the box
     * moved) — carried in rather than restarting the run, which would cut the word in the air.
     */
    fun refresh(candidates: List<ListeningCandidate>) {
        val current = state ?: return
        state = ListeningRun.withCandidates(current, candidates)
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
    }

    /**
     * Long-press: the bedtime is cleared and the run laps again from where it is — the one
     * gesture that reaches zero, which the tap never can.
     */
    fun turnOffTimer() {
        timerTotalMs = 0
        deadline = null
    }

    /** How long the bedtime has left, or null where none was set. */
    fun remainingMs(): Long? = deadline?.let { it - System.currentTimeMillis() }

    private fun dispatch(intent: ListeningIntent) {
        val current = state ?: return
        apply(ListeningRun.reduce(current, intent, rng))
    }

    private fun apply(reduction: ListeningReduction) {
        // why: every intent kills the chain in flight — a gap armed off the word that was
        // just cut must not walk the playlist on behind the learner.
        generation++
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
        publish()
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
                title = chrome.listenTitle,
                // The article is part of the word here as it is in the voice: the lock
                // screen shows what is being said, not a citation form beside it.
                target = turn.spokenArticle?.let { "$it ${turn.targetForm}" } ?: turn.targetForm,
                meaning = turn.sourceForm,
                paused = run.paused,
                pauseLabel = chrome.listenPause,
                resumeLabel = chrome.listenResume,
                skipLabel = chrome.listenSkip,
                repeatLabel = chrome.listenRepeat,
                closeLabel = chrome.close,
            )
        }
    }

    /** One saying, and the gap that follows it once the word has actually ended. */
    private fun sound(turn: ListeningTurn, at: ListeningBeat) {
        if (expired()) {
            model.closeListening()
            return
        }
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
                    ListeningBeat.Echo -> dispatch(ListeningIntent.Advance)
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
        val pronunciation = lang?.let { model.catalog?.pronunciation(it, form) }
        if (pronunciation == null) {
            finish()
            return
        }
        model.pronouncer.pronounce(
            pronunciation,
            Pronouncer.Trigger.LISTENING,
            if (meaning) null else turn.spokenArticle,
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

    /** The bedtime has arrived: a deadline in the past. A run with none set never arrives. */
    private fun expired(): Boolean = remainingMs()?.let { it <= 0 } == true
}
