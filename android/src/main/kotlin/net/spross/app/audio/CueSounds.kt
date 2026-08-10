package net.spross.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.IOException
import net.spross.kern.session.ToneKind

/**
 * The review loop's feedback chimes: one soft sound per verdict, and the cheer a new
 * record earns. Every run in the app — cards, the letter drill, the trainer — sounds
 * through this one pool.
 *
 * The clips are the very files iOS plays (`App/Resources/Sounds/`, authored by
 * `scripts/sounds.py` and synced into the APK by android/build.gradle.kts), so the
 * grammar the script describes — ascending third for correct, descending for wrong, one
 * neutral note for reveal — is heard identically on both platforms and re-tuning it
 * stays a single edit. Nothing here is a system sound: Android's `ToneGenerator` beeps
 * belong to the dialler, and the levels the script measured only mean something if the
 * measured bytes are what plays.
 *
 * They play under MEDIA, beside the spoken words rather than in the notification domain,
 * for the reason `Sounds.swift` gives at length: a chime and a word heard against each
 * other must follow ONE volume slider, or the levels are set against nothing.
 *
 * NOT gated on [Pronouncer.muted] — that switch silences autoplayed WORDS, and iOS gates
 * the chimes on neither it nor the screen reader. A verdict is punctuation, not speech.
 */
class CueSounds(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(STREAMS)
        .setAudioAttributes(CUE)
        .build()

    /**
     * Clip name → the pool's id for it, loaded as the model opens so the first answer of
     * a session pays no decode. A name that would not open is simply absent, and the cue
     * behind it stays silent — a chime is never worth an error surface.
     */
    private val loaded: Map<String, Int> = buildMap {
        val assets = context.applicationContext.assets
        for (name in listOf(CORRECT, WRONG, REVEAL, CHEER)) {
            try {
                // why: the pool dups the descriptor during load, exactly as the
                // framework's own resource overload assumes — closing ours is safe.
                assets.openFd("sounds/$name.wav").use { put(name, pool.load(it, 1)) }
            } catch (_: IOException) {
                continue
            }
        }
    }

    /** The verdict kern reached, made audible. */
    fun play(kind: ToneKind) {
        fire(
            when (kind) {
                ToneKind.Correct -> CORRECT
                ToneKind.Wrong -> WRONG
                ToneKind.Reveal -> REVEAL
            },
        )
    }

    /** The finish, once: the correct interval carried on up to the octave. */
    fun cheer() {
        fire(CHEER)
    }

    /** From `AppModel.onCleared()` — the decoded clips go with the model that opened them. */
    fun release() {
        pool.release()
    }

    // why: a play whose id never finished loading is a no-op in the pool rather than a
    // throw, so an answer inside the first hundred ms of launch is silent, never a crash.
    private fun fire(name: String) {
        val id = loaded[name] ?: return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private companion object {
        const val CORRECT = "correct"
        const val WRONG = "wrong"
        const val REVEAL = "reveal"
        const val CHEER = "cheer"

        /** A second answer inside the first chime's tail overlaps it rather than cutting it. */
        const val STREAMS = 2

        /** The levels live in the files; the pool only ever attenuates, so it does not. */
        const val VOLUME = 1f

        /**
         * Media, like the words: see the class note. SONIFICATION rather than speech —
         * a car or a hearing aid should treat a chime as the interface noise it is.
         */
        val CUE: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
