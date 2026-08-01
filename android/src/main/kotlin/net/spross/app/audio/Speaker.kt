package net.spross.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
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

    /** Whether this device can say anything at all in [lang]. */
    fun canSpeak(lang: Language): Boolean =
        ready && tts.isLanguageAvailable(localeFor(lang)) >= TextToSpeech.LANG_AVAILABLE

    /**
     * Speaks [text] in [lang], replacing whatever was sounding. Callers hand over
     * `Pronunciation.utterance` — the form as it stands on the card, minus only the
     * citation dash a synthesizer would read out.
     */
    fun speak(text: String, lang: Language) {
        if (!ready) return
        if (tts.setLanguage(localeFor(lang)) < TextToSpeech.LANG_AVAILABLE) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        if (ready) tts.stop()
    }

    /** Releases the engine binding; from `AppModel.onCleared()`. */
    fun shutdown() {
        tts.shutdown()
    }

    // why: Spanish is taught in the peninsular variety (distinción) — a Latin-American
    // voice would teach seseo, so the bare code is widened to es-ES.
    private fun localeFor(lang: Language): Locale =
        Locale.forLanguageTag(if (lang == "es") "es-ES" else lang)

    private companion object {
        /** The TTS engine with offline Swahili (data/reference/audio/README.md). */
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val UTTERANCE_ID = "spross-pronunciation"
    }
}
