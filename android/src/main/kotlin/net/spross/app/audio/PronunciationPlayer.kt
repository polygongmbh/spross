package net.spross.app.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import java.io.IOException
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
 */
class PronunciationPlayer {

    /** Bumped by every play and every stop: the id of the newest request. */
    private var request = 0

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
     * descriptor is consumed here. A file that will not open simply stays silent — a
     * word is never worth an error surface.
     */
    fun play(afd: AssetFileDescriptor, gainDb: Double, leadMs: Long) {
        stop()
        val current = request
        val player = MediaPlayer()
        this.player = player
        preparing = current
        player.setOnPreparedListener { prepared ->
            // why: a prepare cannot be cancelled, and the card it was meant for can be
            // gone before it lands — stop() and the next word both bump the request id,
            // so only the newest one is ever allowed to sound. A stale callback touches
            // nothing at all: the marks it would clear belong to the request that
            // overtook it. Callbacks arrive on the thread that built the player — the
            // main one — so the guard needs no lock.
            if (current != request) return@setOnPreparedListener
            preparing = 0
            head = Playback.headMs(leadMs, prepared.duration.toLong())
            sound(prepared)
        }
        player.setOnErrorListener { _, _, _ ->
            if (current == request) stop()
            true // handled: a file that will not decode is a silent word, never a crash
        }
        try {
            player.setAudioAttributes(SPEECH)
            // why: MediaPlayer dups the descriptor, so closing ours the moment
            // setDataSource returns is safe even though the decode is still on its way.
            afd.use { player.setDataSource(it) }
            // The attenuating half of the index rides the player and the boosting half
            // its session; the scheme leaves only one of the two ever doing anything.
            playbackVolume(gainDb).let { player.setVolume(it, it) }
            boost(player, playbackBoostMillibels(gainDb))
            player.prepareAsync()
        } catch (_: IOException) {
            stop() // nothing readable behind the descriptor
        }
    }

    /**
     * Plays the clip already held — the instant answer to a second tap on the same word.
     * False when nothing is loaded and the caller has to hand over a descriptor.
     */
    fun replay(): Boolean {
        val player = player ?: return false
        // why: a prepare still on its way ends in this very clip sounding, so a second
        // ask while it lands is answered by letting it land, not by decoding it twice.
        if (preparing != 0) return true
        sound(player)
        return true
    }

    /** Stops the word in the air and strands any prepare still on its way. */
    fun stop() {
        request++
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

    /** From `AppModel.onCleared()`. Nothing outlives a stop, so this is one. */
    fun release() = stop()

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
