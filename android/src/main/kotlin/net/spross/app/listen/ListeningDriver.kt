package net.spross.app.listen

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.app.AppModel
import net.spross.app.closeListening
import net.spross.app.ui.languageName
import net.spross.kern.catalog.PronounceTrigger
import net.spross.kern.catalog.pronunciation
import net.spross.kern.listen.LISTENING_WATCHDOG_MS
import net.spross.kern.listen.ListeningCandidate
import net.spross.kern.listen.ListeningEffect
import net.spross.kern.listen.ListeningIntent
import net.spross.kern.listen.ListeningReduction
import net.spross.kern.listen.ListeningRun
import net.spross.kern.listen.ListeningRunState
import net.spross.kern.listen.ListeningSaying
import net.spross.kern.listen.ListeningSeam
import net.spross.kern.listen.ListeningTurn
import net.spross.kern.listen.NowPlayingLines
import net.spross.kern.listen.listeningSeam
import net.spross.kern.listen.sayings

/**
 * The platform half of a listening run: kern's reducer decides WHAT is said, this arms WHEN.
 *
 * A turn is kern's three sayings and their gaps (`ListeningTurn.sayings`), and every beat
 * after the first is armed off the previous word ACTUALLY ENDING plus kern's number — never off a timer guessing how long a
 * word lasts, which is the one thing that would make the mode drift on a slow voice or a long
 * phrase. Each beat takes a token; anything that arrives in between — a skip, a pause, the
 * lock screen, a focus loss — bumps the counter and the chain in flight simply stops firing.
 * A word that never reports (an engine that dropped it, a clip that never decoded) is caught
 * by the ceiling below, so a run can stall on nothing.
 *
 * The audio is driven off the reduction's EFFECTS, never off a state diff: a first `Back`
 * leaves the turn as it was and its whole observable is the `Play` it asks for.
 *
 * It touches no box. Nothing here books a review, writes a schedule or moves the streak —
 * listening answers nothing, so it costs the box nothing (`docs/listening.md`).
 */
class ListeningDriver(
    private val app: Application,
    private val model: AppModel,
) : ListeningControls {

    /** The run, or null between runs. The screen and the lock screen both read this. */
    var state by mutableStateOf<ListeningRunState?>(null)
        private set

    /**
     * Whether this turn's meaning has been said yet — the card follows the SOUND rather than a
     * timer of its own, so what is read and what is heard arrive together.
     */
    var revealed by mutableStateOf(false)
        private set

    /** The run's bedtime; off until the chip is tapped. */
    val bedtime = ListeningBedtime()

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
        bedtime.clear()
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

    override fun back() = dispatch(ListeningIntent.Back)

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
        revealed = false
        bedtime.clear()
        pausedByFocus = false
        focus.release()
        ListeningBridge.controls = null
        ListeningBridge.nowPlaying = null
        ListeningService.stop(app)
    }

    /** A tap on the chip: kern's five minutes more ([ListeningBedtime.extend]). */
    fun cycleTimer() {
        bedtime.extend()
        // why: the lock screen's progress bar is the same clock the chip is, so it is
        // redrawn on the tap rather than waiting out the word in the air.
        publish()
    }

    /**
     * Long-press: the bedtime is cleared and the run laps again from where it is — the one
     * gesture that reaches zero, which the tap never can.
     */
    fun turnOffTimer() {
        bedtime.clear()
        publish()
    }

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
                is ListeningEffect.Play -> play(effect.turn)
                // why: a pause keeps the card as it stood — resuming replays the turn from its
                // first saying, which is what hides the meaning again.
                ListeningEffect.Stop -> model.pronouncer.stop()
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
                title = NowPlayingLines.title(chrome.listenTitle),
                languages = languages(),
                paused = run.paused,
                bedtime = bedtime.progress(),
                pauseLabel = chrome.listenPause,
                resumeLabel = chrome.listenResume,
                skipLabel = chrome.listenSkip,
                backLabel = chrome.listenBack,
                closeLabel = chrome.commonClose,
            )
        }
    }

    /** The pair the box joins, known first — the order the mode says them in. */
    private fun languages(): String {
        val stamp = model.box?.joinStamp ?: return ""
        return NowPlayingLines.languages(model.languageName(stamp.source), model.languageName(stamp.target))
    }

    private fun play(turn: ListeningTurn) {
        revealed = false
        walk(turn.sayings, 0)
    }

    /**
     * Says one saying, waits kern's gap once its word has actually ended, and walks on; past
     * the last one the turn is over and kern's seam decides between the next turn and the
     * bedtime ending the run (`listeningSeam`).
     */
    private fun walk(sayings: List<ListeningSaying>, index: Int) {
        val token = ++generation
        val saying = sayings.getOrNull(index)
        if (saying == null) {
            when (listeningSeam(bedtime.remainingMs())) {
                ListeningSeam.End -> model.closeListening()
                ListeningSeam.Advance -> dispatch(ListeningIntent.Advance)
            }
            return
        }
        if (saying.revealed) revealed = true
        say(saying, token) {
            handler.postDelayed({
                if (token == generation) walk(sayings, index + 1)
            }, saying.gapMs)
        }
    }

    /** Says one saying in its language and reports back, once, when the word has ended. */
    private fun say(saying: ListeningSaying, token: Int, onDone: () -> Unit) {
        val stamp = model.box?.joinStamp
        val lang = if (saying.inTarget) stamp?.target else stamp?.source
        var reported = false
        val finish = {
            if (!reported && token == generation) {
                reported = true
                onDone()
            }
        }
        // why: the article rides into the LOOKUP as well as into the voice — a target saying
        // may have a recording that speaks it.
        val pronunciation = lang?.let { model.catalog?.pronunciation(it, saying.form, saying.article) }
        if (pronunciation == null) {
            finish()
            return
        }
        model.pronouncer.pronounce(
            pronunciation,
            PronounceTrigger.Listening,
            saying.article,
            bedtime.gainDb(),
            onFinish = finish,
        )
        // why: insurance, not timing — a word whose end is never reported would leave the
        // chain standing still, and a run that has gone quiet is worse than one that hurries.
        // Kern owns the ceiling so the two phones cannot wait different lengths.
        handler.postDelayed({ finish() }, LISTENING_WATCHDOG_MS)
    }
}
