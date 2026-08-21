package net.spross.app.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import net.spross.kern.catalog.Playback

/**
 * Plays one bundled recording at a time, under the catalog's ANALYSIS INDEX: `gainDb`
 * decibels from the analysis target, and `leadMs` of dead air to skip at its head.
 *
 * why MediaPlayer where clips of a second or two would suit SoundPool: what ships is the
 * untouched Commons transcode — re-encoding it is an adaptation under BY-SA — so the
 * loudness the packs never agreed on and the second of dead air in front of the uk
 * letters are corrected HERE, at playback. SoundPool cannot seek at all, so the lead
 * alone already decides the engine; and its volume, like MediaPlayer's, only ever
 * attenuates, while those letters ask for up to +20 dB. That is what a [LoudnessEnhancer]
 * on the player's own audio session is for — the boost scheme, split in [playbackVolume].
 *
 * The prepared player is KEPT until the next word or a [stop], which is what makes
 * [replay] instant. It reads the mp3 straight out of the APK, which needs the entry
 * STORED rather than deflated — see the `noCompress` pin in android/build.gradle.kts.
 *
 * why a worker thread: create/setDataSource/prepare/release and the enhancer's init are
 * binder calls into audioserver — seconds each when that process wedges — and they used
 * to run on the main thread at every reveal autoplay, which is an ANR at a tap. One
 * [HandlerThread] owns the whole player lifecycle; the main thread only posts requests.
 */
class PronunciationPlayer {

    private val thread = HandlerThread("pronunciation").apply { start() }
    private val handler = Handler(thread.looper)

    /**
     * Bumped by every play and every stop: the id of the newest request. Atomic because
     * the main thread bumps it and the worker both reads it (the stale-prepare guard)
     * and bumps it on a decode error.
     */
    private val request = AtomicInteger(0)

    /**
     * Whether a play stands unstopped — what [replay] answers without waiting on the
     * worker. A decode error clears it, after which the caller loads afresh.
     */
    @Volatile private var held = false

    /**
     * What the clip in the air owes back when it ends, and which request owes it.
     *
     * A listening run arms its next beat off this, so it has to be the END of the word and
     * never a stop or an overtaking play — those are words cut off, not words finished.
     */
    @Volatile private var pending: Pair<Int, () -> Unit>? = null

    /** why: the worker owns the player, so the callback hops before the app hears about it. */
    private val main = Handler(Looper.getMainLooper())

    // Everything below is owned by [thread]: the worker builds the player there, so
    // MediaPlayer's callbacks land there too and the guards need no lock.

    /** Which request the prepare in flight belongs to; 0 once it has landed. */
    private var preparing = 0

    /** The clip in hand — prepared, or still on its way. */
    private var player: MediaPlayer? = null

    /** The boost half of the index, on [player]'s session; null where none was asked for. */
    private var enhancer: LoudnessEnhancer? = null

    /** Where the clip in hand starts speaking, ms. */
    private var head = 0L

    /**
     * Plays [afd] under its analysis index, replacing whatever was sounding. The
     * descriptor is consumed on the worker. A file that will not open simply stays
     * silent — a word is never worth an error surface.
     */
    fun play(
        afd: AssetFileDescriptor,
        gainDb: Double,
        leadMs: Long,
        fadeDb: Double = 0.0,
        onFinish: (() -> Unit)? = null,
    ) {
        val current = request.incrementAndGet()
        held = true
        pending = onFinish?.let { current to it }
        handler.post {
            clear()
            load(current, afd, gainDb, leadMs, fadeDb)
        }
    }

    /**
     * Plays the clip already held — the instant answer to a second tap on the same word.
     * False when nothing is loaded and the caller has to hand over a descriptor.
     */
    fun replay(volume: Float = 1f, onFinish: (() -> Unit)? = null): Boolean {
        if (!held) return false
        pending = onFinish?.let { request.get() to it }
        handler.post {
            // why: the fade may have moved since this clip was prepared — the boost on its
            // session is the catalog's correction and stands, the volume is the ramp.
            player?.setVolume(volume, volume)
            // why: a prepare still on its way ends in this very clip sounding, so a
            // second ask while it lands is answered by letting it land, not twice.
            player?.takeIf { preparing == 0 }?.let(::sound)
        }
        return true
    }

    /** Stops the word in the air and strands any prepare still on its way. */
    fun stop() {
        request.incrementAndGet()
        held = false
        pending = null
        handler.post { clear() }
    }

    /** From `AppModel.onCleared()`. Nothing outlives a stop, so this is one. */
    fun release() {
        stop()
        // why: quitSafely drains what stop() just posted — the release of the clip in
        // hand — before the worker ends; a plain quit would leak the player.
        thread.quitSafely()
    }

    /** Builds and prepares the player for [current] — worker side of [play]. */
    private fun load(
        current: Int,
        afd: AssetFileDescriptor,
        gainDb: Double,
        leadMs: Long,
        fadeDb: Double,
    ) {
        val player = MediaPlayer()
        this.player = player
        preparing = current
        player.setOnPreparedListener { prepared ->
            // why: a prepare cannot be canceled, and the card it was meant for can be
            // gone before it lands — stop() and the next word both bump the request id,
            // so only the newest one is ever allowed to sound. A stale callback touches
            // nothing at all: the marks it would clear belong to the request that
            // overtook it, and the newer request's own clear() already released its player.
            if (current != request.get()) return@setOnPreparedListener
            preparing = 0
            head = Playback.headMs(leadMs, prepared.duration.toLong())
            sound(prepared)
        }
        player.setOnCompletionListener {
            // why: only the newest request may report — a clip the next word overtook
            // ended because it was replaced, which is not the end of anything owed.
            if (current == request.get()) finish(current)
        }
        player.setOnErrorListener { _, _, _ ->
            fail(current)
            true // handled: a file that will not decode is a silent word, never a crash
        }
        try {
            player.setAudioAttributes(SPEECH)
            // why: MediaPlayer dups the descriptor, so closing ours the moment
            // setDataSource returns is safe even though the decode is still on its way.
            afd.use { player.setDataSource(it) }
            // The attenuating half of the index rides the player and the boosting half
            // its session; the scheme leaves only one of the two ever doing anything.
            playbackVolume(gainDb, fadeDb).let { player.setVolume(it, it) }
            boost(player, playbackBoostMillibels(gainDb))
            player.prepareAsync()
        } catch (_: IOException) {
            fail(current) // nothing readable behind the descriptor
        }
    }

    /**
     * An error stop, scoped to the request it happened to: only the newest request may
     * end itself — a failure the next word already overtook has nothing left to clear.
     */
    private fun fail(current: Int) {
        if (!request.compareAndSet(current, current + 1)) return
        held = false
        clear()
        // why: a file that will not decode still ends the word — a run armed off the
        // completion would otherwise stand still on one broken clip.
        finish(current)
    }

    /** Hands the completion on where it belongs to the request that asked for it. */
    private fun finish(current: Int) {
        val owed = pending ?: return
        if (owed.first != current) return
        pending = null
        main.post(owed.second)
    }

    /** Releases whatever is in hand — the worker-side body of [stop]. */
    private fun clear() {
        preparing = 0
        head = 0
        // why: the enhancer first — it hangs on the session this player owns. And release
        // rather than stop: it halts playback from EVERY state, the error one included,
        // and a two-second clip's codec is not worth holding past the card that asked.
        enhancer?.release()
        enhancer = null
        player?.release()
        player = null
    }

    /**
     * Starts [player] at the measured head — always by way of a seek, which is also what
     * makes a second tap start the word over instead of doing nothing to a player that is
     * already running. SEEK_CLOSEST rather than a sync-frame seek: the head is a
     * measurement in ms, and the nearest key frame can land back inside the very dead air
     * it exists to skip. minSdk 26 carries the mode.
     */
    private fun sound(player: MediaPlayer) {
        player.seekTo(head, MediaPlayer.SEEK_CLOSEST)
        player.start()
    }

    /** Hangs the boost on [player]'s own session — no volume on this platform can give it. */
    private fun boost(player: MediaPlayer, millibels: Int) {
        if (millibels <= 0) return
        enhancer = try {
            LoudnessEnhancer(player.audioSessionId).apply {
                setTargetGain(millibels)
                enabled = true
            }
        } catch (_: RuntimeException) {
            // why: the effect is not on every device, and a letter at the volume it was
            // recorded at is a far better answer than a crash on the way to saying it.
            null
        }
    }

    private companion object {
        /**
         * A target word is media — content the learner asked for, not a notification —
         * and it is speech, which is what a car or a hearing aid decides on.
         */
        val SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
