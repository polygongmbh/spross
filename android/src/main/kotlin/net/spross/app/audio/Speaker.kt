package net.spross.app.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import net.spross.kern.catalog.VoiceSelection
import net.spross.kern.model.Language

/**
 * Live speech synthesis for a target form — the fallback branch of pronunciation:
 * bundled recordings are canonical, the synthesizer answers only where none matches.
 *
 * The engine is PINNED to Google's, the one that carries Swahili offline; a
 * device-default engine would make the fallback depend on the device. Where it is
 * missing the binding fails and every call stays a silent no-op: a target word in the
 * wrong voice teaches worse than silence.
 */
class Speaker(context: Context) {

    /**
     * Binding is asynchronous — nothing may be said before it lands, or ever if it fails.
     *
     * Compose state rather than a plain flag: what a device can SAY decides whether the
     * letter drill exists at all, and a surface that asked before the binding landed would
     * hide it for the rest of the run. Written from `onInit`, which arrives on the main
     * thread; snapshot state is safe either way.
     */
    var ready by mutableStateOf(false)
        private set

    private val tts = TextToSpeech(
        context.applicationContext,
        { status -> ready = status == TextToSpeech.SUCCESS },
        GOOGLE_ENGINE,
    )

    /**
     * Which utterance is the newest. The id handed to the engine carries it, so the end of
     * the word now sounding is told apart from the end of one a flush already discarded —
     * the two arrive on the same callback and a constant id could not separate them.
     */
    private val generation = AtomicInteger(0)

    /** What the utterance in the air owes back when it ends, and which utterance owes it. */
    @Volatile private var pending: Pair<Int, () -> Unit>? = null

    private val main = Handler(Looper.getMainLooper())

    init {
        // why: the engine reports on a binder thread, so the callback hops to the main
        // thread before anything the app owns hears about it — a listening run arms its
        // next beat here, and its state is read from the composition.
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("the pre-21 signature the base class still declares abstract")
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
    }

    /** Whether this device can say anything at all in [lang]. */
    fun canSpeak(lang: Language): Boolean =
        ready && tts.isLanguageAvailable(localeFor(lang)) >= TextToSpeech.LANG_AVAILABLE

    /**
     * Speaks [text] in [lang], replacing whatever was sounding, and answers whether
     * anything will actually sound — false where the engine never bound or has no voice
     * for the language, so a caller waiting on the end of the word is not left waiting.
     *
     * [volume] is linear 0–1, which is how the engine takes an attenuation; the listening
     * fade is the only thing that ever asks for one. [onFinish] fires ONCE, on the main
     * thread, when the utterance ends — never when a later call flushed it away, which is
     * a word that was cut off rather than a word that finished.
     */
    fun speak(
        text: String,
        lang: Language,
        volume: Float = 1f,
        onFinish: (() -> Unit)? = null,
    ): Boolean {
        if (!ready) return false
        if (tts.setLanguage(localeFor(lang)) < TextToSpeech.LANG_AVAILABLE) return false
        val current = generation.incrementAndGet()
        pending = onFinish?.let { current to it }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
        }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId(current)) != TextToSpeech.SUCCESS) {
            pending = null
            return false
        }
        return true
    }

    /** Cuts the word in the air. A stopped word owes nothing, so its completion is dropped. */
    fun stop() {
        generation.incrementAndGet()
        pending = null
        if (ready) tts.stop()
    }

    /** Releases the engine binding; from `AppModel.onCleared()`. */
    fun shutdown() {
        tts.shutdown()
    }

    /** Hands the completion on where it belongs to the utterance that is still the newest. */
    private fun finish(id: String?) {
        val owed = pending ?: return
        if (id != utteranceId(owed.first)) return
        pending = null
        main.post(owed.second)
    }

    // why: kern picks the variety a language is taught in (Spanish peninsular, so a
    // Latin-American voice cannot teach seseo); this engine cannot search its voices,
    // and asking for the right tag is all the say it has in which one answers.
    private fun localeFor(lang: Language): Locale =
        Locale.forLanguageTag(VoiceSelection.preferredTag(lang))

    private fun utteranceId(generation: Int): String = "$UTTERANCE_ID-$generation"

    private companion object {
        /** The TTS engine with offline Swahili (data/reference/audio/README.md). */
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val UTTERANCE_ID = "spross-pronunciation"
    }
}
