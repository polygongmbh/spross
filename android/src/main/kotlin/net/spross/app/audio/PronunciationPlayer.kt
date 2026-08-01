package net.spross.app.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Plays one bundled recording at a time.
 *
 * SoundPool rather than MediaPlayer or a streaming player: these are clips of a second
 * or two, it decodes them ahead of playback so asking for the same word again answers
 * instantly ([replay]), and it costs no dependency. It reads the mp3 straight out of
 * the APK, which needs the entry STORED rather than deflated — see the `noCompress`
 * pin in android/build.gradle.kts.
 */
class PronunciationPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(1) // one word at a time: a new fire replaces the one sounding
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()

    /** Bumped by every play and every stop: the id of the newest request. */
    private var request = 0

    /** Which request the load in flight belongs to; 0 once it has landed. */
    private var loading = 0
    private var sample = 0
    private var stream = 0

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            // why: a load cannot be cancelled, and the card it was meant for can be
            // gone before it lands — stop() and the next word both bump the request
            // id, so only the newest one is allowed to sound. Anything else is
            // unloaded instead of played into a card that never asked for it.
            val current = loading == request && sampleId == sample && status == LOAD_OK
            loading = 0
            if (current) start(sampleId) else pool.unload(sampleId)
        }
    }

    /** Plays [afd], replacing whatever was sounding. The descriptor is consumed here. */
    fun play(afd: AssetFileDescriptor) {
        stop()
        loading = request
        // why: SoundPool dups the descriptor, so closing ours the moment load()
        // returns is safe even though the decode is still on its way.
        sample = afd.use { pool.load(it, PRIORITY) }
    }

    /**
     * Plays the clip already held — the instant answer to a second tap on the same
     * word. False when nothing is loaded and the caller has to hand over a descriptor.
     */
    fun replay(): Boolean {
        if (sample == 0) return false
        if (stream != 0) pool.stop(stream)
        start(sample)
        return true
    }

    /** Stops the word in the air and strands any load still on its way. */
    fun stop() {
        request++
        if (stream != 0) {
            pool.stop(stream)
            stream = 0
        }
        if (sample != 0) {
            pool.unload(sample)
            sample = 0
        }
    }

    /** From `AppModel.onCleared()`. */
    fun release() {
        stop()
        pool.release()
    }

    private fun start(sampleId: Int) {
        stream = pool.play(sampleId, VOLUME, VOLUME, PRIORITY, NO_LOOP, NORMAL_RATE)
    }

    private companion object {
        const val LOAD_OK = 0
        const val PRIORITY = 1
        const val VOLUME = 1f
        const val NO_LOOP = 0
        const val NORMAL_RATE = 1f
    }
}
